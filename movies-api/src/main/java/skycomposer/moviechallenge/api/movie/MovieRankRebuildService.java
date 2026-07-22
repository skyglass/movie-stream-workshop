package skycomposer.moviechallenge.api.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

// Shared core of "submit a manual drag-and-drop order, rebuild a Movie Challenge vote history + rank that
// reproduces it" -- used by both Movie Personality ranking (MovieGuideService.submitRanking, always submits its
// whole movie set) and a real user re-ranking the currently loaded prefix of Favorite Movies.
//
// Deliberately does NOT touch movie_recommendations: that's fine to fully replace for a synthetic Personality
// user (they have no other state), but would silently wipe a real user's disliked movies if reused here --
// callers that need the recommendations replacement (Personality only) do it themselves, on top of this.
@RequiredArgsConstructor
@Service
public class MovieRankRebuildService {

    private static final int LOCAL_COMPARISON_WINDOW = 4;

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final MovieChallengeRepository movieChallengeRepository;

    private record RankSlot(int rankPosition, BigDecimal score, int directComparisons, BigDecimal mu,
                            BigDecimal sigma, BigDecimal scoreError80) {
    }

    @Transactional
    public void rebuildRanks(String username, List<String> orderedImdbIds) {
        replaceAllVotes(username, generateComparisonGraph(orderedImdbIds));
        movieChallengeRepository.rebuildUserMovieRanks(username);
        movieChallengeRepository.rebuildUserMovieChallengeCounts(username);
    }

    // Re-ranks exactly the first N favorite movies supplied by the dialog. Rows after that prefix (and ranked
    // movies which are not favorites) are never rebuilt: each submitted movie is moved into one of the prefix's
    // existing rank/score slots, while every row outside the prefix remains byte-for-byte unchanged. Replacing
    // only votes internal to the prefix also leaves all Movie Challenge history involving an outside movie alone.
    @Transactional
    public void rebuildFavoritePrefixRanks(String username, List<String> orderedImdbIds) {
        if (orderedImdbIds.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot submit an empty ranking");
        }
        if (new HashSet<>(orderedImdbIds).size() != orderedImdbIds.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "The ranking contains duplicate movies");
        }

        List<String> currentPrefix = currentFavoritePrefix(username, orderedImdbIds.size());
        if (currentPrefix.size() != orderedImdbIds.size()
                || !new HashSet<>(currentPrefix).equals(new HashSet<>(orderedImdbIds))) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "The ranking must contain exactly the currently loaded favorite movies");
        }

        List<RankSlot> slots = rankSlots(username, currentPrefix);
        replaceVotesWithinPrefix(username, orderedImdbIds, generateComparisonGraph(orderedImdbIds));
        assignPrefixToExistingSlots(username, orderedImdbIds, slots);
        movieChallengeRepository.rebuildUserMovieChallengeCounts(username);
    }

    // Given the fully-known submitted order (rank 1 = best), builds a bounded winner/loser vote set instead of a
    // full O(N^2) round-robin: each movie is compared against its next LOCAL_COMPARISON_WINDOW neighbors (local
    // ordering) plus a handful of exponentially-further movies at halving distances down to that same window
    // (global "bridge" anchoring, similar to a skip-list) -- O(N log N) pairs, enough for the real Bradley-Terry
    // fit to reproduce this exact order on its own. Always forward (winner index < loser index), so no unordered
    // pair is ever generated twice.
    private List<String[]> generateComparisonGraph(List<String> orderedImdbIds) {
        int n = orderedImdbIds.size();
        List<String[]> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int window = 1; window <= LOCAL_COMPARISON_WINDOW && i + window < n; window++) {
                pairs.add(new String[]{orderedImdbIds.get(i), orderedImdbIds.get(i + window)});
            }
            for (int distance = n / 2; distance > LOCAL_COMPARISON_WINDOW; distance /= 2) {
                int j = i + distance;
                if (j < n) {
                    pairs.add(new String[]{orderedImdbIds.get(i), orderedImdbIds.get(j)});
                }
            }
        }
        return pairs;
    }

    private void replaceAllVotes(String username, List<String[]> pairs) {
        jdbc.sql("delete from user_movie_challenge_vote where user_id=:username")
                .param("username", username).update();
        insertVotes(username, pairs);
    }

    private void replaceVotesWithinPrefix(String username, List<String> orderedImdbIds, List<String[]> pairs) {
        jdbc.sql("""
                delete from user_movie_challenge_vote
                where user_id=:username and winner_id in (:ids) and loser_id in (:ids)
                """).param("username", username).param("ids", orderedImdbIds).update();
        insertVotes(username, pairs);
    }

    private void insertVotes(String username, List<String[]> pairs) {
        if (pairs.isEmpty()) return;
        List<Object[]> batchArgs = pairs.stream().map(pair -> new Object[]{username, pair[0], pair[1]}).toList();
        jdbcTemplate.batchUpdate("insert into user_movie_challenge_vote(user_id, winner_id, loser_id) values (?, ?, ?)", batchArgs);
    }

    private List<String> currentFavoritePrefix(String username, int size) {
        return jdbc.sql("""
                select rank.movie_id
                from user_movie_rank rank
                join movie_recommendations recommendation
                  on recommendation.user_id=rank.user_id
                 and recommendation.movie_id=rank.movie_id
                 and recommendation.positive=true
                join user_movie_challenge challenge
                  on challenge.user_id=rank.user_id and challenge.movie_id=rank.movie_id
                where rank.user_id=:username
                order by rank.rank_position
                limit :size
                """).param("username", username).param("size", size).query(String.class).list();
    }

    private List<RankSlot> rankSlots(String username, List<String> movieIds) {
        return jdbc.sql("""
                select rank_position, score, direct_comparisons, mu, sigma, score_error_80
                from user_movie_rank
                where user_id=:username and movie_id in (:ids)
                order by rank_position
                for update
                """).param("username", username).param("ids", movieIds).query((rs, rowNum) -> new RankSlot(
                rs.getInt("rank_position"), rs.getBigDecimal("score"), rs.getInt("direct_comparisons"),
                rs.getBigDecimal("mu"), rs.getBigDecimal("sigma"), rs.getBigDecimal("score_error_80"))).list();
    }

    private void assignPrefixToExistingSlots(String username, List<String> orderedImdbIds, List<RankSlot> slots) {
        Integer maxRank = jdbc.sql("select coalesce(max(rank_position), 0) from user_movie_rank where user_id=:username")
                .param("username", username).query(Integer.class).single();
        jdbc.sql("""
                update user_movie_rank set rank_position=rank_position+:offset
                where user_id=:username and movie_id in (:ids)
                """).param("offset", maxRank).param("username", username).param("ids", orderedImdbIds).update();

        String sql = """
                update user_movie_rank
                set rank_position=?, score=?, direct_comparisons=?, mu=?, sigma=?, score_error_80=?
                where user_id=? and movie_id=?
                """;
        List<Object[]> batch = new ArrayList<>(orderedImdbIds.size());
        for (int i = 0; i < orderedImdbIds.size(); i++) {
            RankSlot slot = slots.get(i);
            batch.add(new Object[]{slot.rankPosition(), slot.score(), slot.directComparisons(), slot.mu(),
                    slot.sigma(), slot.scoreError80(), username, orderedImdbIds.get(i)});
        }
        jdbcTemplate.batchUpdate(sql, batch);
    }
}
