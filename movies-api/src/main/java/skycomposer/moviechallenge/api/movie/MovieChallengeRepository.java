package skycomposer.moviechallenge.api.movie;

import skycomposer.moviechallenge.api.movie.dto.MovieChallengeDto;
import skycomposer.moviechallenge.api.movie.dto.MovieChallengeDto.MovieChallengeMovieDto;
import skycomposer.moviechallenge.api.movie.dto.MovieRatingDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class MovieChallengeRepository {

    private static final int EXPLORATION_DIRECT_COMPARISONS = 3;
    private static final int CLOSE_RANK_WINDOW_MINIMUM = 3;
    private static final int CLOSE_RANK_WINDOW_RECOMMENDATION_DIVISOR = 33;
    private static final double CLOSE_SCORE_DISTANCE = 1.0;
    private static final double DEFAULT_BRADLEY_TERRY_SIGMA = 2.0;
    private static final double MINIMAL_REFINEMENT_PAIR_INFORMATION = 0.30;
    private static final int BRADLEY_TERRY_ITERATIONS = 50;
    private static final double BRADLEY_TERRY_PRIOR_PRECISION = 1.0;
    private static final double BRADLEY_TERRY_MAX_UPDATE = 0.75;
    private static final double SCORE_ERROR_80_PER_SIGMA = 2.25 * 1.28155;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Optional<MovieChallengeDto> findNextChallenge(String username) {
        Optional<MovieChallengeDto> explorationChallenge = findExplorationChallenge(username);
        return explorationChallenge.or(() -> findRefinementChallenge(username));
    }

    private Optional<MovieChallengeDto> findExplorationChallenge(String username) {
        String sql = """
                select selected_pair.user_id,
                    selected_pair.movie1_id,
                    movie1.title as movie1_title,
                    movie1.poster as movie1_poster,
                    selected_pair.movie2_id,
                    movie2.title as movie2_title,
                    movie2.poster as movie2_poster
                from (
                    select first_movie.user_id,
                        case
                            when coalesce(first_rank.direct_comparisons, 0)
                                < coalesce(second_rank.direct_comparisons, 0) then first_movie.movie_id
                            when coalesce(second_rank.direct_comparisons, 0)
                                < coalesce(first_rank.direct_comparisons, 0) then second_movie.movie_id
                            when coalesce(first_rank.rank_position, 0)
                                <= coalesce(second_rank.rank_position, 0) then first_movie.movie_id
                            else second_movie.movie_id
                        end as movie1_id,
                        case
                            when coalesce(first_rank.direct_comparisons, 0)
                                < coalesce(second_rank.direct_comparisons, 0) then second_movie.movie_id
                            when coalesce(second_rank.direct_comparisons, 0)
                                < coalesce(first_rank.direct_comparisons, 0) then first_movie.movie_id
                            when coalesce(first_rank.rank_position, 0)
                                <= coalesce(second_rank.rank_position, 0) then second_movie.movie_id
                            else first_movie.movie_id
                        end as movie2_id,
                        least(coalesce(first_rank.direct_comparisons, 0),
                            coalesce(second_rank.direct_comparisons, 0)) as lower_direct_comparisons,
                        greatest(coalesce(first_rank.direct_comparisons, 0),
                            coalesce(second_rank.direct_comparisons, 0)) as higher_direct_comparisons,
                        abs(coalesce(first_rank.rank_position, 0) - coalesce(second_rank.rank_position, 0)) as rank_distance
                    from movie_recommendations first_movie
                    join movie_recommendations second_movie
                        on second_movie.user_id = first_movie.user_id
                        and second_movie.movie_id > first_movie.movie_id
                        and second_movie.positive = true
                    left join user_movie_rank first_rank
                        on first_rank.user_id = first_movie.user_id
                        and first_rank.movie_id = first_movie.movie_id
                    left join user_movie_rank second_rank
                        on second_rank.user_id = second_movie.user_id
                        and second_rank.movie_id = second_movie.movie_id
                    left join user_movie_challenge_vote direct_vote
                        on direct_vote.user_id = first_movie.user_id
                        and direct_vote.winner_id = first_movie.movie_id
                        and direct_vote.loser_id = second_movie.movie_id
                    left join user_movie_challenge_vote reverse_vote
                        on reverse_vote.user_id = first_movie.user_id
                        and reverse_vote.winner_id = second_movie.movie_id
                        and reverse_vote.loser_id = first_movie.movie_id
                    where first_movie.user_id = :username
                        and first_movie.positive = true
                        and direct_vote.user_id is null
                        and reverse_vote.user_id is null
                ) selected_pair
                join movies movie1
                    on movie1.imdb_id = selected_pair.movie1_id
                join movies movie2
                    on movie2.imdb_id = selected_pair.movie2_id
                where selected_pair.lower_direct_comparisons < :explorationDirectComparisons
                    order by lower_direct_comparisons,
                        rank_distance,
                        higher_direct_comparisons,
                        movie1_id,
                        movie2_id
                    limit 1
                """;
        return queryChallenge(sql, Map.of(
                "username", username,
                "explorationDirectComparisons", EXPLORATION_DIRECT_COMPARISONS));
    }

    private Optional<MovieChallengeDto> findRefinementChallenge(String username) {
        String sql = """
                select selected_pair.user_id,
                    selected_pair.movie1_id,
                    movie1.title as movie1_title,
                    movie1.poster as movie1_poster,
                    selected_pair.movie2_id,
                    movie2.title as movie2_title,
                    movie2.poster as movie2_poster
                from (
                    select first_movie.user_id,
                        case
                            when coalesce(first_rank.direct_comparisons, 0)
                                < coalesce(second_rank.direct_comparisons, 0) then first_movie.movie_id
                            when coalesce(second_rank.direct_comparisons, 0)
                                < coalesce(first_rank.direct_comparisons, 0) then second_movie.movie_id
                            when coalesce(first_rank.rank_position, 0)
                                <= coalesce(second_rank.rank_position, 0) then first_movie.movie_id
                            else second_movie.movie_id
                        end as movie1_id,
                        case
                            when coalesce(first_rank.direct_comparisons, 0)
                                < coalesce(second_rank.direct_comparisons, 0) then second_movie.movie_id
                            when coalesce(second_rank.direct_comparisons, 0)
                                < coalesce(first_rank.direct_comparisons, 0) then first_movie.movie_id
                            when coalesce(first_rank.rank_position, 0)
                                <= coalesce(second_rank.rank_position, 0) then second_movie.movie_id
                            else first_movie.movie_id
                        end as movie2_id,
                        least(coalesce(first_rank.direct_comparisons, 0),
                            coalesce(second_rank.direct_comparisons, 0)) as lower_direct_comparisons,
                        greatest(coalesce(first_rank.direct_comparisons, 0),
                            coalesce(second_rank.direct_comparisons, 0)) as higher_direct_comparisons,
                        abs(coalesce(first_rank.rank_position, 0) - coalesce(second_rank.rank_position, 0)) as rank_distance,
                        abs(coalesce(first_rank.score, cast(5.5 as numeric(12, 6)))
                            - coalesce(second_rank.score, cast(5.5 as numeric(12, 6)))) as score_distance,
                        (coalesce(first_rank.sigma, :defaultBradleyTerrySigma)
                            + coalesce(second_rank.sigma, :defaultBradleyTerrySigma))
                            / (cast(4 as numeric(12, 6))
                                + abs(coalesce(first_rank.mu, cast(0 as numeric(12, 6)))
                                    - coalesce(second_rank.mu, cast(0 as numeric(12, 6))))) as pair_information
                    from movie_recommendations first_movie
                    join movie_recommendations second_movie
                        on second_movie.user_id = first_movie.user_id
                        and second_movie.movie_id > first_movie.movie_id
                        and second_movie.positive = true
                    left join user_movie_rank first_rank
                        on first_rank.user_id = first_movie.user_id
                        and first_rank.movie_id = first_movie.movie_id
                    left join user_movie_rank second_rank
                        on second_rank.user_id = second_movie.user_id
                        and second_rank.movie_id = second_movie.movie_id
                    left join user_movie_challenge_vote direct_vote
                        on direct_vote.user_id = first_movie.user_id
                        and direct_vote.winner_id = first_movie.movie_id
                        and direct_vote.loser_id = second_movie.movie_id
                    left join user_movie_challenge_vote reverse_vote
                        on reverse_vote.user_id = first_movie.user_id
                        and reverse_vote.winner_id = second_movie.movie_id
                        and reverse_vote.loser_id = first_movie.movie_id
                    where first_movie.user_id = :username
                        and first_movie.positive = true
                        and direct_vote.user_id is null
                        and reverse_vote.user_id is null
                ) selected_pair
                join movies movie1
                    on movie1.imdb_id = selected_pair.movie1_id
                join movies movie2
                    on movie2.imdb_id = selected_pair.movie2_id
                where selected_pair.lower_direct_comparisons >= :explorationDirectComparisons
                    and selected_pair.rank_distance <= :closeRankWindow
                    and selected_pair.score_distance <= :closeScoreDistance
                    and selected_pair.pair_information >= :minimalRefinementPairInformation
                order by selected_pair.pair_information desc,
                    selected_pair.rank_distance,
                    selected_pair.higher_direct_comparisons,
                    selected_pair.lower_direct_comparisons,
                    selected_pair.movie1_id,
                    selected_pair.movie2_id
                limit 1
                """;
        Map<String, Object> params = Map.of(
                "username", username,
                "explorationDirectComparisons", EXPLORATION_DIRECT_COMPARISONS,
                "closeRankWindow", closeRankWindow(username),
                "closeScoreDistance", CLOSE_SCORE_DISTANCE,
                "defaultBradleyTerrySigma", DEFAULT_BRADLEY_TERRY_SIGMA,
                "minimalRefinementPairInformation", MINIMAL_REFINEMENT_PAIR_INFORMATION);
        return queryChallenge(sql, params);
    }

    private int closeRankWindow(String username) {
        Integer recommendationCount = jdbcTemplate.queryForObject("""
                select count(1)
                from movie_recommendations
                where user_id = :username
                    and positive = true
                """, Map.of("username", username), Integer.class);
        int roundedWindow = Math.round((float) Optional.ofNullable(recommendationCount).orElse(0)
                / CLOSE_RANK_WINDOW_RECOMMENDATION_DIVISOR);
        return Math.max(CLOSE_RANK_WINDOW_MINIMUM, roundedWindow);
    }

    private Optional<MovieChallengeDto> queryChallenge(String sql, Map<String, Object> params) {
        return jdbcTemplate.query(sql, params, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new MovieChallengeDto(
                    new MovieChallengeMovieDto(
                            rs.getString("movie1_id"),
                            rs.getString("movie1_title"),
                            rs.getString("movie1_poster")),
                    new MovieChallengeMovieDto(
                            rs.getString("movie2_id"),
                            rs.getString("movie2_title"),
                            rs.getString("movie2_poster"))));
        });
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
                        where vote.user_id = :username
                            and vote.winner_id = :winnerId
                            and vote.loser_id = :loserId
                    )
                    and not exists (
                        select 1
                        from user_movie_challenge_vote vote
                        where vote.user_id = :username
                            and vote.winner_id = :loserId
                            and vote.loser_id = :winnerId
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
                            and existing.winner_id = :winnerId
                            and existing.loser_id = :loserId
                    )
                    and not exists (
                        select 1
                        from user_movie_challenge_vote existing
                        where existing.user_id = :username
                            and existing.winner_id = :loserId
                            and existing.loser_id = :winnerId
                    )
                """;
        int inserted = jdbcTemplate.update(sql, params(username, winnerId, loserId));
        if (inserted > 0) {
            incrementChallengeCount(username, winnerId);
            incrementChallengeCount(username, loserId);
        }
    }

    private void incrementChallengeCount(String username, String movieId) {
        int updated = jdbcTemplate.update("""
                update user_movie_challenge
                set challenge_count = challenge_count + 1
                where user_id = :username
                    and movie_id = :movieId
                """, params(username, movieId));
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into user_movie_challenge (user_id, movie_id, challenge_count)
                    select :username, :movieId, 1
                    where not exists (
                        select 1
                        from user_movie_challenge existing
                        where existing.user_id = :username
                            and existing.movie_id = :movieId
                    )
                    """, params(username, movieId));
        }
    }

    public void rebuildUserMovieRanks(String username) {
        jdbcTemplate.update("delete from user_movie_rank where user_id = :username", Map.of("username", username));

        List<ChallengeVote> votes = directChallengeVotes(username);
        if (votes.isEmpty()) {
            return;
        }

        Map<String, BradleyTerryMovie> movies = bradleyTerryMovies(votes);
        fitBradleyTerryModel(votes, movies);
        List<RankedMovie> rankedMovies = rankedMovies(votes, movies);

        String sql = """
                insert into user_movie_rank (
                    user_id,
                    movie_id,
                    rank_position,
                    score,
                    direct_comparisons,
                    mu,
                    sigma,
                    score_error_80
                )
                values (
                    :username,
                    :movieId,
                    :rankPosition,
                    :score,
                    :directComparisons,
                    :mu,
                    :sigma,
                    :scoreError80
                )
                """;
        @SuppressWarnings("unchecked")
        Map<String, ?>[] batch = rankedMovies.stream()
                .map(movie -> Map.of(
                        "username", username,
                        "movieId", movie.movieId(),
                        "rankPosition", movie.rankPosition(),
                        "score", movie.score(),
                        "directComparisons", movie.directComparisons(),
                        "mu", movie.mu(),
                        "sigma", movie.sigma(),
                        "scoreError80", movie.scoreError80()))
                .toArray(Map[]::new);
        jdbcTemplate.batchUpdate(sql, batch);
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

    private List<ChallengeVote> directChallengeVotes(String username) {
        return jdbcTemplate.query("""
                select winner_id, loser_id
                from user_movie_challenge_vote
                where user_id = :username
                """, Map.of("username", username), (rs, rowNum) -> new ChallengeVote(
                rs.getString("winner_id"),
                rs.getString("loser_id")));
    }

    private Map<String, BradleyTerryMovie> bradleyTerryMovies(List<ChallengeVote> votes) {
        Map<String, BradleyTerryMovie> movies = new HashMap<>();
        for (ChallengeVote vote : votes) {
            BradleyTerryMovie winner = movies.computeIfAbsent(vote.winnerId(), BradleyTerryMovie::new);
            BradleyTerryMovie loser = movies.computeIfAbsent(vote.loserId(), BradleyTerryMovie::new);
            winner.wins++;
            winner.directComparisons++;
            loser.directComparisons++;
        }
        return movies;
    }

    private void fitBradleyTerryModel(List<ChallengeVote> votes, Map<String, BradleyTerryMovie> movies) {
        for (int iteration = 0; iteration < BRADLEY_TERRY_ITERATIONS; iteration++) {
            movies.values().forEach(movie -> {
                movie.gradient = -BRADLEY_TERRY_PRIOR_PRECISION * movie.mu;
                movie.curvature = BRADLEY_TERRY_PRIOR_PRECISION;
            });

            for (ChallengeVote vote : votes) {
                BradleyTerryMovie winner = movies.get(vote.winnerId());
                BradleyTerryMovie loser = movies.get(vote.loserId());
                double probability = winProbability(winner.mu, loser.mu);
                double residual = 1 - probability;
                double information = probability * (1 - probability);

                winner.gradient += residual;
                loser.gradient -= residual;
                winner.curvature += information;
                loser.curvature += information;
            }

            movies.values().forEach(movie -> {
                double update = movie.gradient / movie.curvature;
                update = Math.max(-BRADLEY_TERRY_MAX_UPDATE, Math.min(BRADLEY_TERRY_MAX_UPDATE, update));
                movie.mu += update;
            });
            centerBradleyTerryMu(movies.values());
        }
    }

    private List<RankedMovie> rankedMovies(List<ChallengeVote> votes, Map<String, BradleyTerryMovie> movies) {
        recomputeBradleyTerryPrecision(votes, movies);
        List<BradleyTerryMovie> sortedMovies = new ArrayList<>(movies.values());
        sortedMovies.forEach(movie -> movie.score = expectedScore(movie, sortedMovies));
        sortedMovies.sort(Comparator
                .comparingDouble((BradleyTerryMovie movie) -> movie.score).reversed()
                .thenComparing((BradleyTerryMovie movie) -> movie.wins, Comparator.reverseOrder())
                .thenComparing((BradleyTerryMovie movie) -> movie.directComparisons, Comparator.reverseOrder())
                .thenComparing(movie -> movie.movieId));

        List<RankedMovie> rankedMovies = new ArrayList<>();
        for (int index = 0; index < sortedMovies.size(); index++) {
            BradleyTerryMovie movie = sortedMovies.get(index);
            double sigma = 1 / Math.sqrt(movie.precision);
            double scoreError80 = Math.min(9, SCORE_ERROR_80_PER_SIGMA * sigma);
            rankedMovies.add(new RankedMovie(
                    movie.movieId,
                    index + 1,
                    decimal(movie.score),
                    movie.directComparisons,
                    decimal(movie.mu),
                    decimal(sigma),
                    decimal(scoreError80)));
        }
        return rankedMovies;
    }

    private void recomputeBradleyTerryPrecision(List<ChallengeVote> votes, Map<String, BradleyTerryMovie> movies) {
        movies.values().forEach(movie -> movie.precision = BRADLEY_TERRY_PRIOR_PRECISION);
        for (ChallengeVote vote : votes) {
            BradleyTerryMovie winner = movies.get(vote.winnerId());
            BradleyTerryMovie loser = movies.get(vote.loserId());
            double probability = winProbability(winner.mu, loser.mu);
            double information = probability * (1 - probability);
            winner.precision += information;
            loser.precision += information;
        }
    }

    private double expectedScore(BradleyTerryMovie movie, List<BradleyTerryMovie> movies) {
        if (movies.size() == 1) {
            return 10;
        }
        double expectedWinShare = 0;
        for (BradleyTerryMovie otherMovie : movies) {
            if (!otherMovie.movieId.equals(movie.movieId)) {
                expectedWinShare += winProbability(movie.mu, otherMovie.mu);
            }
        }
        return 1 + 9 * expectedWinShare / (movies.size() - 1);
    }

    private double winProbability(double winnerMu, double loserMu) {
        double difference = Math.max(-30, Math.min(30, winnerMu - loserMu));
        return 1 / (1 + Math.exp(-difference));
    }

    private void centerBradleyTerryMu(Collection<BradleyTerryMovie> movies) {
        double average = movies.stream()
                .mapToDouble(movie -> movie.mu)
                .average()
                .orElse(0);
        movies.forEach(movie -> movie.mu -= average);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private Map<String, Object> params(String username, String winnerId, String loserId) {
        return Map.of("username", username, "winnerId", winnerId, "loserId", loserId);
    }

    private Map<String, Object> params(String username, String movieId) {
        return Map.of("username", username, "movieId", movieId);
    }

    private record ChallengeVote(String winnerId, String loserId) {
    }

    private record RankedMovie(String movieId,
                               int rankPosition,
                               BigDecimal score,
                               int directComparisons,
                               BigDecimal mu,
                               BigDecimal sigma,
                               BigDecimal scoreError80) {
    }

    private static class BradleyTerryMovie {
        private final String movieId;
        private int wins;
        private int directComparisons;
        private double mu;
        private double gradient;
        private double curvature;
        private double precision;
        private double score;

        private BradleyTerryMovie(String movieId) {
            this.movieId = movieId;
        }
    }
}
