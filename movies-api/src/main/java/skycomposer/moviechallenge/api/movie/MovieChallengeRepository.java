package skycomposer.moviechallenge.api.movie;

import skycomposer.moviechallenge.api.movie.dto.MovieChallengeDto;
import skycomposer.moviechallenge.api.movie.dto.MovieChallengeDto.MovieChallengeMovieDto;
import skycomposer.moviechallenge.api.movie.dto.MovieRatingDto;

import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class MovieChallengeRepository {

    private static final int MINIMUM_SKIP_COMPARISONS = 8;
    private static final int MINIMUM_SKIP_RANK_DISTANCE = 10;
    private static final double MINIMUM_SKIP_CONFIDENCE = 0.80;
    private static final int MIN_TARGET_DIRECT_COMPARISONS = 4;
    private static final int MAX_TARGET_DIRECT_COMPARISONS = 10;
    private static final int MIN_CLOSE_RANK_DISTANCE = 3;
    private static final int MAX_CLOSE_RANK_DISTANCE = 12;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Optional<MovieChallengeDto> findNextChallenge(String username) {
        List<ChallengeCandidate> candidates = challengeCandidates(username);
        if (candidates.size() < 2) {
            return Optional.empty();
        }

        Map<String, Integer> candidateIndexes = candidateIndexes(candidates);
        BitSet[] completedPairs = completedPairs(username, candidates.size(), candidateIndexes);
        ChallengeThresholds thresholds = challengeThresholds(candidates.size());

        int bestFirstIndex = -1;
        int bestSecondIndex = -1;
        int bestRankCategory = Integer.MAX_VALUE;
        int bestMinComparisons = Integer.MAX_VALUE;
        int bestMaxComparisons = Integer.MAX_VALUE;
        int bestRankDistance = Integer.MAX_VALUE;
        String bestFirstMovieId = null;
        String bestSecondMovieId = null;

        for (int firstIndex = 0; firstIndex < candidates.size() - 1; firstIndex++) {
            ChallengeCandidate first = candidates.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < candidates.size(); secondIndex++) {
                if (completedPairs[firstIndex] != null && completedPairs[firstIndex].get(secondIndex)) {
                    continue;
                }

                ChallengeCandidate second = candidates.get(secondIndex);
                if (confidentlySeparated(first, second)) {
                    continue;
                }

                boolean needsMoreEvidence = needsMoreEvidence(first, second, thresholds);
                if (!needsMoreEvidence) {
                    continue;
                }

                int rankCategory = rankCategory(first, second, thresholds);
                int minComparisons = Math.min(first.directComparisons(), second.directComparisons());
                int maxComparisons = Math.max(first.directComparisons(), second.directComparisons());
                int rankDistance = rankDistance(first, second, thresholds);
                if (betterPair(rankCategory, minComparisons, maxComparisons, rankDistance,
                        first.movieId(), second.movieId(),
                        bestRankCategory,
                        bestMinComparisons, bestMaxComparisons, bestRankDistance,
                        bestFirstMovieId, bestSecondMovieId)) {
                    bestFirstIndex = firstIndex;
                    bestSecondIndex = secondIndex;
                    bestRankCategory = rankCategory;
                    bestMinComparisons = minComparisons;
                    bestMaxComparisons = maxComparisons;
                    bestRankDistance = rankDistance;
                    bestFirstMovieId = first.movieId();
                    bestSecondMovieId = second.movieId();
                }
            }
        }

        if (bestFirstIndex < 0) {
            return Optional.empty();
        }

        return Optional.of(toMovieChallenge(candidates.get(bestFirstIndex), candidates.get(bestSecondIndex)));
    }

    public boolean hasAvailableChallenge(String username) {
        return findNextChallenge(username).isPresent();
    }

    public boolean canRecordWinnerLoser(String username, String winnerId, String loserId) {
        String sql = """
                select case when :winnerId <> :loserId
                    and exists (
                        select 1
                        from movie_recommendations
                        where user_id = :username
                            and movie_id = :winnerId
                            and positive = true
                    )
                    and exists (
                        select 1
                        from movie_recommendations
                        where user_id = :username
                            and movie_id = :loserId
                            and positive = true
                    )
                    and not exists (
                        select 1
                        from user_movie_challenge_vote vote
                        where user_id = :username
                            and (
                                (vote.winner_id = :winnerId and vote.loser_id = :loserId)
                                or (vote.winner_id = :loserId and vote.loser_id = :winnerId)
                            )
                    )
                then true else false end
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, params(username, winnerId, loserId), Boolean.class));
    }

    public void insertDirectWinnerLoser(String username, String winnerId, String loserId) {
        String sql = """
                insert into user_movie_challenge_vote (user_id, winner_id, loser_id)
                select :username, :winnerId, :loserId
                where :winnerId <> :loserId
                    and not exists (
                        select 1
                        from user_movie_challenge_vote existing
                        where existing.user_id = :username
                            and (
                                (existing.winner_id = :winnerId and existing.loser_id = :loserId)
                                or (existing.winner_id = :loserId and existing.loser_id = :winnerId)
                            )
                    )
                """;
        jdbcTemplate.update(sql, params(username, winnerId, loserId));
    }

    public void rebuildUserMovieRanks(String username) {
        jdbcTemplate.update("delete from user_movie_rank where user_id = :username", Map.of("username", username));

        String sql = """
                insert into user_movie_rank (user_id, movie_id, rank_position, score, direct_comparisons, confidence)
                with movie_stats as (
                    select user_id,
                        movie_id,
                        sum(wins) as wins,
                        sum(losses) as losses
                    from (
                        select user_id, winner_id as movie_id, 1 as wins, 0 as losses
                        from user_movie_challenge_vote
                        where user_id = :username
                        union all
                        select user_id, loser_id as movie_id, 0 as wins, 1 as losses
                        from user_movie_challenge_vote
                        where user_id = :username
                    ) vote_result
                    group by user_id, movie_id
                ),
                ranked_movies as (
                    select user_id,
                        movie_id,
                        row_number() over (
                            partition by user_id
                            order by (
                                cast(1 as numeric(12, 6))
                                    + cast(9 as numeric(12, 6))
                                        * (cast(wins + 2 as numeric(12, 6))
                                            / cast(wins + losses + 4 as numeric(12, 6)))
                                        * least(cast(1 as numeric(12, 6)),
                                            cast(wins + losses as numeric(12, 6)) / cast(8 as numeric(12, 6)))
                            ) desc,
                            wins desc,
                            (wins + losses) desc,
                            movie_id asc
                        ) as rank_position,
                        cast(
                            cast(1 as numeric(12, 6))
                                + cast(9 as numeric(12, 6))
                                    * (cast(wins + 2 as numeric(12, 6))
                                        / cast(wins + losses + 4 as numeric(12, 6)))
                                    * least(cast(1 as numeric(12, 6)),
                                        cast(wins + losses as numeric(12, 6)) / cast(8 as numeric(12, 6)))
                            as numeric(12, 6)
                        ) as score,
                        wins + losses as direct_comparisons,
                        least(cast(1 as numeric(12, 6)),
                            cast(wins + losses as numeric(12, 6)) / cast(8 as numeric(12, 6))) as confidence
                    from movie_stats
                )
                select user_id, movie_id, rank_position, score, direct_comparisons, confidence
                from ranked_movies
                """;
        jdbcTemplate.update(sql, Map.of("username", username));
    }

    public int voteCount(String username, String movieId) {
        String sql = """
                select count(1)
                from user_movie_challenge_vote
                where user_id = :username
                    and winner_id = :movieId
                """;
        return jdbcTemplate.queryForObject(sql, Map.of("username", username, "movieId", movieId), Integer.class);
    }

    public int challengeCount(String username, String movieId) {
        String sql = """
                select coalesce(max(direct_comparisons), 0)
                from user_movie_rank
                where user_id = :username
                    and movie_id = :movieId
                """;
        return jdbcTemplate.queryForObject(sql, Map.of("username", username, "movieId", movieId), Integer.class);
    }

    public boolean transitiveWinnerLoserExists(String username, String winnerId, String loserId) {
        String sql = """
                select case when exists (
                    select 1
                    from user_movie_rank winner_rank
                    join user_movie_rank loser_rank
                        on loser_rank.user_id = winner_rank.user_id
                    where winner_rank.user_id = :username
                        and winner_rank.movie_id = :winnerId
                        and loser_rank.movie_id = :loserId
                        and winner_rank.rank_position < loser_rank.rank_position
                ) then true else false end
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, params(username, winnerId, loserId), Boolean.class));
    }

    public boolean directWinnerLoserExists(String username, String winnerId, String loserId) {
        String sql = """
                select case when exists (
                    select 1
                    from user_movie_challenge_vote
                    where user_id = :username
                        and winner_id = :winnerId
                        and loser_id = :loserId
                ) then true else false end
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, params(username, winnerId, loserId), Boolean.class));
    }

    public Optional<MovieRatingDto> movieRating(String username, String movieId) {
        return Optional.ofNullable(movieRatings(username, List.of(movieId)).get(movieId));
    }

    public Map<String, MovieRatingDto> movieRatings(String username, Collection<String> movieIds) {
        if (movieIds.isEmpty()) {
            return Map.of();
        }

        String sql = """
                select movie_id, rank_position, rating
                from user_movie_rating
                where user_id = :username
                    and movie_id in (:movieIds)
                """;
        Map<String, Object> params = Map.of("username", username, "movieIds", movieIds);
        Map<String, MovieRatingDto> ratings = new HashMap<>();
        jdbcTemplate.query(sql, params, (rs, rowNum) -> Map.entry(
                        rs.getString("movie_id"),
                        new MovieRatingDto(rs.getInt("rank_position"), rs.getBigDecimal("rating"))))
                .forEach(rating -> ratings.put(rating.getKey(), rating.getValue()));
        return ratings;
    }

    private Map<String, Object> params(String username, String winnerId, String loserId) {
        return Map.of("username", username, "winnerId", winnerId, "loserId", loserId);
    }

    private List<ChallengeCandidate> challengeCandidates(String username) {
        String sql = """
                select recommendation.movie_id,
                    movie.title,
                    movie.poster,
                    rank.rank_position,
                    rank.direct_comparisons,
                    rank.confidence
                from movie_recommendations recommendation
                join movies movie
                    on movie.imdb_id = recommendation.movie_id
                left join user_movie_rank rank
                    on rank.user_id = recommendation.user_id
                    and rank.movie_id = recommendation.movie_id
                where recommendation.user_id = :username
                    and recommendation.positive = true
                order by recommendation.movie_id asc
                """;
        return jdbcTemplate.query(sql, Map.of("username", username), (rs, rowNum) -> {
            Integer rankPosition = (Integer) rs.getObject("rank_position");
            Integer directComparisons = (Integer) rs.getObject("direct_comparisons");
            Number confidence = (Number) rs.getObject("confidence");
            return new ChallengeCandidate(
                    rs.getString("movie_id"),
                    rs.getString("title"),
                    rs.getString("poster"),
                    rankPosition,
                    directComparisons == null ? 0 : directComparisons,
                    confidence == null ? 0.0 : confidence.doubleValue());
        });
    }

    private Map<String, Integer> candidateIndexes(List<ChallengeCandidate> candidates) {
        Map<String, Integer> candidateIndexes = new HashMap<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            candidateIndexes.put(candidates.get(index).movieId(), index);
        }
        return candidateIndexes;
    }

    private BitSet[] completedPairs(String username, int candidateCount, Map<String, Integer> candidateIndexes) {
        String sql = """
                select winner_id, loser_id
                from user_movie_challenge_vote
                where user_id = :username
                """;
        BitSet[] completedPairs = new BitSet[candidateCount];
        jdbcTemplate.query(sql, Map.of("username", username), rs -> {
            Integer winnerIndex = candidateIndexes.get(rs.getString("winner_id"));
            Integer loserIndex = candidateIndexes.get(rs.getString("loser_id"));
            if (winnerIndex != null && loserIndex != null) {
                int firstIndex = Math.min(winnerIndex, loserIndex);
                int secondIndex = Math.max(winnerIndex, loserIndex);
                if (completedPairs[firstIndex] == null) {
                    completedPairs[firstIndex] = new BitSet(candidateCount);
                }
                completedPairs[firstIndex].set(secondIndex);
            }
        });
        return completedPairs;
    }

    private boolean confidentlySeparated(ChallengeCandidate first, ChallengeCandidate second) {
        return first.rankPosition() != null
                && second.rankPosition() != null
                && first.directComparisons() >= MINIMUM_SKIP_COMPARISONS
                && second.directComparisons() >= MINIMUM_SKIP_COMPARISONS
                && first.confidence() >= MINIMUM_SKIP_CONFIDENCE
                && second.confidence() >= MINIMUM_SKIP_CONFIDENCE
                && Math.abs(first.rankPosition() - second.rankPosition()) >= MINIMUM_SKIP_RANK_DISTANCE;
    }

    /**
     * Returns a category score for the pair.
     *
     * Lower scores result higher priority because they compare candidates from different categories:
     * - a new movie with a ranked movie
     * - a movie that needs more evidence with one that already has enough evidence
     *
     * Higher scores are assigned when both candidates belong to the same category
     * (both new, both ranked, both needing more evidence, or both having enough evidence).
     */
    private int rankCategory(ChallengeCandidate first, ChallengeCandidate second,
                             ChallengeThresholds thresholds) {
        int score = 0;

        boolean firstIsRanked = first.rankPosition() != null;
        boolean secondIsRanked = second.rankPosition() != null;

        // Give lower priority to comparisons where both movies are either ranked or new.
        if (firstIsRanked && secondIsRanked) {
            score++;
        }
        if (!firstIsRanked && !secondIsRanked) {
            score++;
        }

        boolean firstNeedsMoreEvidence = needsMoreEvidence(first, thresholds);
        boolean secondNeedsMoreEvidence = needsMoreEvidence(second, thresholds);

        // Give lower priority to comparisons where both movies have the same
        // evidence status.
        if (firstNeedsMoreEvidence && secondNeedsMoreEvidence) {
            score++;
        }
        if (!firstNeedsMoreEvidence && !secondNeedsMoreEvidence) {
            score++;
        }

        return score;
    }

    private int rankDistance(ChallengeCandidate first, ChallengeCandidate second,
                             ChallengeThresholds thresholds) {
        if (first.rankPosition() == null || second.rankPosition() == null) {
            return 0;
        }

        boolean firstNeedsMoreEvidence = needsMoreEvidence(first, thresholds);
        boolean secondNeedsMoreEvidence = needsMoreEvidence(second, thresholds);

        // If exactly one movie still needs more evidence, this comparison is valuable
        // because it helps stabilize the uncertain movie's ranking.
        // Mixed-confidence pairs are good candidates, but promoting all of them would dominate the ranking.
        // Instead, deterministically promote approximately every fourth pair.
        if (firstNeedsMoreEvidence != secondNeedsMoreEvidence) {
            int hash = Math.abs(Objects.hash(first.movieId(), second.movieId()));
            if (hash % 4 == 0) {
                return 0;
            }
        }

        return Math.abs(first.rankPosition() - second.rankPosition());
    }

    private boolean needsMoreEvidence(ChallengeCandidate first,
                                   ChallengeCandidate second,
                                   ChallengeThresholds thresholds) {
        if (needsMoreEvidence(first, thresholds) || needsMoreEvidence(second, thresholds)) {
            return true;
        }
        return false;
    }

    private boolean needsMoreEvidence(ChallengeCandidate candidate, ChallengeThresholds thresholds) {
        return candidate.directComparisons() < thresholds.targetDirectComparisons();
    }

    private ChallengeThresholds challengeThresholds(int movieCount) {
        return new ChallengeThresholds(
                clamp(MIN_TARGET_DIRECT_COMPARISONS,
                        MAX_TARGET_DIRECT_COMPARISONS,
                        ceilLog2(movieCount) + 1),
                clamp(MIN_CLOSE_RANK_DISTANCE,
                        MAX_CLOSE_RANK_DISTANCE,
                        (int) Math.ceil(Math.sqrt(movieCount) / 2.0)));
    }

    private int ceilLog2(int value) {
        if (value <= 1) {
            return 0;
        }
        return Integer.SIZE - Integer.numberOfLeadingZeros(value - 1);
    }

    private int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean betterPair(int rankCategory,
                               int minComparisons,
                               int maxComparisons,
                               int rankDistance,
                               String firstMovieId,
                               String secondMovieId,
                               int bestRankCategory,
                               int bestMinComparisons,
                               int bestMaxComparisons,
                               int bestRankDistance,
                               String bestFirstMovieId,
                               String bestSecondMovieId) {
        if (rankCategory != bestRankCategory) {
            return rankCategory < bestRankCategory;
        }
        if (minComparisons != bestMinComparisons) {
            return minComparisons < bestMinComparisons;
        }
        if (maxComparisons != bestMaxComparisons) {
            return maxComparisons < bestMaxComparisons;
        }
        if (rankDistance != bestRankDistance) {
            return rankDistance < bestRankDistance;
        }
        if (bestFirstMovieId == null || !firstMovieId.equals(bestFirstMovieId)) {
            return bestFirstMovieId == null || firstMovieId.compareTo(bestFirstMovieId) < 0;
        }
        return bestSecondMovieId == null || secondMovieId.compareTo(bestSecondMovieId) < 0;
    }

    private MovieChallengeDto toMovieChallenge(ChallengeCandidate first, ChallengeCandidate second) {
        return new MovieChallengeDto(
                new MovieChallengeMovieDto(first.movieId(), first.title(), first.poster()),
                new MovieChallengeMovieDto(second.movieId(), second.title(), second.poster()));
    }

    private record ChallengeCandidate(String movieId,
                                      String title,
                                      String poster,
                                      Integer rankPosition,
                                      int directComparisons,
                                      double confidence) {
    }

    private record ChallengeThresholds(int targetDirectComparisons, int closeRankDistance) {
    }
}
