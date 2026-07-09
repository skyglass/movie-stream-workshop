package skycomposer.moviechallenge.api.movie;

import skycomposer.moviechallenge.api.movie.dto.MovieChallengeDto;
import skycomposer.moviechallenge.api.movie.dto.MovieChallengeDto.MovieChallengeMovieDto;
import skycomposer.moviechallenge.api.movie.dto.MovieRatingDto;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class MovieChallengeRepository {

    private static final int MINIMAL_DIRECT_COMPARISONS_MINIMUM = 8;
    private static final int MINIMAL_DIRECT_COMPARISONS_REFERENCE_RECOMMENDATION_COUNT = 355;
    private static final int MINIMAL_DIRECT_COMPARISONS_REFERENCE_VALUE = 15;
    private static final int COMPARISON_BALANCE_THRESHOLD = 5;
    private static final int MINIMAL_DISTANCE_FROM_MAXIMUM_DIRECT_COMPARISONS = 10;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Optional<MovieChallengeDto> findNextChallenge(String username) {
        String sql = """
                with user_candidate_stats as (
                    select coalesce(max(coalesce(rank_projection.direct_comparisons, 0)), 0) as max_direct_comparisons,
                        coalesce(min(coalesce(rank_projection.direct_comparisons, 0)), 0) as min_direct_comparisons,
                        coalesce(max(rank_projection.rank_position), 0) as max_rank_position,
                        count(recommendation.movie_id) as recommendation_count
                    from movie_recommendations recommendation
                    left join user_movie_rank rank_projection
                        on rank_projection.user_id = recommendation.user_id
                        and rank_projection.movie_id = recommendation.movie_id
                    where recommendation.user_id = :username
                        and recommendation.positive = true
                ),
                selection_settings as (
                    select max_direct_comparisons,
                        min_direct_comparisons,
                        max_rank_position,
                        greatest(
                            :minimalDirectComparisonsMinimum,
                            cast(round(
                                cast(recommendation_count * :minimalDirectComparisonsReferenceValue as numeric)
                                    / cast(:minimalDirectComparisonsReferenceRecommendationCount as numeric)
                            ) as integer)
                        ) as minimal_direct_comparisons,
                        greatest(
                            cast(round(cast(max_direct_comparisons as numeric) / 10) as integer),
                            1
                        ) as comparison_step,
                        greatest(
                            cast(round(cast(max_rank_position as numeric) / 2) as integer),
                            1
                        ) as fallback_rank_position
                    from user_candidate_stats
                ),
                candidate_movies as (
                    select recommendation.user_id,
                        recommendation.movie_id,
                        coalesce(rank_projection.direct_comparisons, 0) as direct_comparisons,
                        coalesce(rank_projection.rank_position, settings.fallback_rank_position) as selection_rank_position,
                        settings.max_direct_comparisons,
                        settings.min_direct_comparisons,
                        settings.minimal_direct_comparisons,
                        settings.comparison_step
                    from movie_recommendations recommendation
                    cross join selection_settings settings
                    left join user_movie_rank rank_projection
                        on rank_projection.user_id = recommendation.user_id
                        and rank_projection.movie_id = recommendation.movie_id
                    where recommendation.user_id = :username
                        and recommendation.positive = true
                ),
                eligible_first_movies as (
                    select first_movie.user_id,
                        first_movie.movie_id,
                        first_movie.direct_comparisons,
                        first_movie.selection_rank_position,
                        first_movie.max_direct_comparisons,
                        first_movie.min_direct_comparisons,
                        first_movie.minimal_direct_comparisons,
                        first_movie.comparison_step
                    from candidate_movies first_movie
                    where (
                            first_movie.direct_comparisons <= first_movie.minimal_direct_comparisons
                            or first_movie.max_direct_comparisons - first_movie.direct_comparisons > :comparisonBalanceThreshold
                        )
                        and exists (
                            select 1
                            from candidate_movies second_movie
                            where second_movie.user_id = first_movie.user_id
                                and second_movie.movie_id <> first_movie.movie_id
                                and (
                                    first_movie.direct_comparisons < first_movie.minimal_direct_comparisons
                                    or (
                                        second_movie.direct_comparisons < first_movie.max_direct_comparisons
                                        and (
                                            first_movie.max_direct_comparisons - first_movie.direct_comparisons
                                                > :minimalDistanceFromMaximumDirectComparisons
                                            or second_movie.direct_comparisons
                                                <= first_movie.direct_comparisons + (:comparisonBalanceThreshold - 1)
                                        )
                                    )
                                )
                                and not exists (
                                    select 1
                                    from user_movie_challenge_vote vote
                                    where vote.user_id = first_movie.user_id
                                        and vote.winner_id = first_movie.movie_id
                                        and vote.loser_id = second_movie.movie_id
                                )
                                and not exists (
                                    select 1
                                    from user_movie_challenge_vote vote
                                    where vote.user_id = first_movie.user_id
                                        and vote.winner_id = second_movie.movie_id
                                        and vote.loser_id = first_movie.movie_id
                                )
                        )
                ),
                selected_first_movie as (
                    select user_id,
                        movie_id,
                        direct_comparisons,
                        selection_rank_position,
                        max_direct_comparisons,
                        min_direct_comparisons,
                        minimal_direct_comparisons,
                        comparison_step
                    from eligible_first_movies
                    order by direct_comparisons,
                        movie_id
                    limit 1
                ),
                selected_pair as (
                    select first_movie.user_id,
                        first_movie.movie_id as movie1_id,
                        second_movie.movie_id as movie2_id,
                        case
                            when first_movie.max_direct_comparisons - first_movie.min_direct_comparisons < 3 
                                and second_movie.direct_comparisons < first_movie.max_direct_comparisons
                                then 0
                            when second_movie.direct_comparisons >= first_movie.direct_comparisons + first_movie.comparison_step
                                and second_movie.direct_comparisons
                                    < first_movie.max_direct_comparisons - :comparisonBalanceThreshold then 0
                            else abs(first_movie.direct_comparisons + first_movie.comparison_step
                                    - second_movie.direct_comparisons)
                        end as movie2_direct_comparison_order,
                        abs(first_movie.selection_rank_position - second_movie.selection_rank_position) as rank_distance
                    from selected_first_movie first_movie
                    join candidate_movies second_movie
                        on second_movie.user_id = first_movie.user_id
                        and second_movie.movie_id <> first_movie.movie_id
                    where (
                            first_movie.direct_comparisons < first_movie.minimal_direct_comparisons
                            or (
                                second_movie.direct_comparisons < first_movie.max_direct_comparisons
                                and (
                                    first_movie.max_direct_comparisons - first_movie.direct_comparisons
                                        > :minimalDistanceFromMaximumDirectComparisons
                                    or second_movie.direct_comparisons
                                        <= first_movie.direct_comparisons + (:comparisonBalanceThreshold - 1)
                                )
                            )
                        )
                        and not exists (
                            select 1
                            from user_movie_challenge_vote vote
                            where vote.user_id = first_movie.user_id
                                and vote.winner_id = first_movie.movie_id
                                and vote.loser_id = second_movie.movie_id
                        )
                        and not exists (
                            select 1
                            from user_movie_challenge_vote vote
                            where vote.user_id = first_movie.user_id
                                and vote.winner_id = second_movie.movie_id
                                and vote.loser_id = first_movie.movie_id
                        )
                    order by movie2_direct_comparison_order,
                        rank_distance,
                        movie2_id
                    limit 1
                )
                select selected_pair.user_id,
                    selected_pair.movie1_id,
                    movie1.title as movie1_title,
                    movie1.poster as movie1_poster,
                    selected_pair.movie2_id,
                    movie2.title as movie2_title,
                    movie2.poster as movie2_poster
                from selected_pair
                join movies movie1
                    on movie1.imdb_id = selected_pair.movie1_id
                join movies movie2
                    on movie2.imdb_id = selected_pair.movie2_id
                """;
        Map<String, Object> params = Map.of(
                "username", username,
                "minimalDirectComparisonsMinimum", MINIMAL_DIRECT_COMPARISONS_MINIMUM,
                "minimalDirectComparisonsReferenceRecommendationCount",
                        MINIMAL_DIRECT_COMPARISONS_REFERENCE_RECOMMENDATION_COUNT,
                "minimalDirectComparisonsReferenceValue", MINIMAL_DIRECT_COMPARISONS_REFERENCE_VALUE,
                "comparisonBalanceThreshold", COMPARISON_BALANCE_THRESHOLD,
                "minimalDistanceFromMaximumDirectComparisons", MINIMAL_DISTANCE_FROM_MAXIMUM_DIRECT_COMPARISONS);
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

        String sql = """
                insert into user_movie_rank (user_id, movie_id, rank_position, score, direct_comparisons)
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
                        wins + losses as direct_comparisons
                    from movie_stats
                )
                select user_id, movie_id, rank_position, score, direct_comparisons
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

    private Map<String, Object> params(String username, String movieId) {
        return Map.of("username", username, "movieId", movieId);
    }
}
