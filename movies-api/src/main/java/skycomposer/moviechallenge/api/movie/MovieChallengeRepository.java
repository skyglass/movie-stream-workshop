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

    private static final int MINIMUM_SKIP_COMPARISONS = 8;
    private static final int MINIMUM_SKIP_RANK_DISTANCE = 10;
    private static final double MINIMUM_SKIP_CONFIDENCE = 0.80;

    private static final String NEXT_CHALLENGE_CANDIDATE_SQL = """
            from movie_recommendations r1
            join movie_recommendations r2
                on r2.user_id = r1.user_id
                and r1.movie_id < r2.movie_id
                and r2.positive = true
            left join user_movie_rank rank1
                on rank1.user_id = r1.user_id
                and rank1.movie_id = r1.movie_id
            left join user_movie_rank rank2
                on rank2.user_id = r2.user_id
                and rank2.movie_id = r2.movie_id
            where r1.user_id = :username
                and r1.positive = true
                and not exists (
                    select 1
                    from user_movie_challenge_vote vote
                    where vote.user_id = r1.user_id
                        and (
                            (vote.winner_id = r1.movie_id and vote.loser_id = r2.movie_id)
                            or (vote.winner_id = r2.movie_id and vote.loser_id = r1.movie_id)
                        )
                )
                and not exists (
                    select 1
                    where rank1.movie_id is not null
                        and rank2.movie_id is not null
                        and rank1.direct_comparisons >= :minimumSkipComparisons
                        and rank2.direct_comparisons >= :minimumSkipComparisons
                        and rank1.confidence >= :minimumSkipConfidence
                        and rank2.confidence >= :minimumSkipConfidence
                        and abs(rank1.rank_position - rank2.rank_position) >= :minimumSkipRankDistance
                )
            """;

    private static final String NEXT_CHALLENGE_ORDER_SQL = """
            order by case
                    when rank1.movie_id is null and rank2.movie_id is null then 0
                    when rank1.movie_id is null or rank2.movie_id is null then 1
                    else 2
                end,
                least(coalesce(rank1.direct_comparisons, 0), coalesce(rank2.direct_comparisons, 0)),
                greatest(coalesce(rank1.direct_comparisons, 0), coalesce(rank2.direct_comparisons, 0)),
                case
                    when rank1.rank_position is null or rank2.rank_position is null then 0
                    else abs(rank1.rank_position - rank2.rank_position)
                end,
                r1.movie_id,
                r2.movie_id
            fetch first 1 row only
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Optional<MovieChallengeDto> findNextChallenge(String username) {
        String sql = """
                with next_pair as (
                    select r1.movie_id as movie1_id,
                        r2.movie_id as movie2_id
                """ + NEXT_CHALLENGE_CANDIDATE_SQL + NEXT_CHALLENGE_ORDER_SQL + """
                )
                select m1.imdb_id as movie1_id,
                    m1.title as movie1_title,
                    m1.poster as movie1_poster,
                    m2.imdb_id as movie2_id,
                    m2.title as movie2_title,
                    m2.poster as movie2_poster
                from next_pair
                join movies m1 on m1.imdb_id = next_pair.movie1_id
                join movies m2 on m2.imdb_id = next_pair.movie2_id
                """;

        List<MovieChallengeDto> challenges = jdbcTemplate.query(sql, challengeParams(username), (rs, rowNum) ->
                new MovieChallengeDto(
                        new MovieChallengeMovieDto(
                                rs.getString("movie1_id"),
                                rs.getString("movie1_title"),
                                rs.getString("movie1_poster")),
                        new MovieChallengeMovieDto(
                                rs.getString("movie2_id"),
                                rs.getString("movie2_title"),
                                rs.getString("movie2_poster"))));
        return challenges.stream().findFirst();
    }

    public boolean hasAvailableChallenge(String username) {
        String sql = """
                select case when exists (
                    select 1
                """ + NEXT_CHALLENGE_CANDIDATE_SQL + """
                ) then true else false end
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, challengeParams(username), Boolean.class));
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

    private Map<String, Object> challengeParams(String username) {
        return Map.of(
                "username", username,
                "minimumSkipComparisons", MINIMUM_SKIP_COMPARISONS,
                "minimumSkipConfidence", MINIMUM_SKIP_CONFIDENCE,
                "minimumSkipRankDistance", MINIMUM_SKIP_RANK_DISTANCE);
    }

    private Map<String, Object> params(String username, String winnerId, String loserId) {
        return Map.of("username", username, "winnerId", winnerId, "loserId", loserId);
    }
}
