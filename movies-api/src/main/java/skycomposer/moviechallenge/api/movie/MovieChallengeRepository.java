package skycomposer.moviechallenge.api.movie;

import skycomposer.moviechallenge.api.movie.dto.MovieChallengeDto;
import skycomposer.moviechallenge.api.movie.dto.MovieChallengeDto.MovieChallengeMovieDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class MovieChallengeRepository {

    private static final String NEXT_CHALLENGE_CANDIDATE_SQL = """
            from movie_recommendations r1
            join movie_recommendations r2
                on r2.user_id = r1.user_id
                and r1.movie_id < r2.movie_id
                and r2.positive = true
            join movies m1 on m1.imdb_id = r1.movie_id
            join movies m2 on m2.imdb_id = r2.movie_id
            left join user_movie_challenge c1
                on c1.user_id = r1.user_id
                and c1.movie_id = r1.movie_id
            left join user_movie_challenge c2
                on c2.user_id = r2.user_id
                and c2.movie_id = r2.movie_id
            where r1.user_id = :username
                and r1.positive = true
                and not exists (
                    select 1
                    from user_movie_winner_loser_all winner_loser
                    where winner_loser.user_id = r1.user_id
                        and (r1.movie_id = winner_loser.winner_id or r1.movie_id = winner_loser.loser_id)
                        and (r2.movie_id = winner_loser.winner_id or r2.movie_id = winner_loser.loser_id)
                )
            """;

    private static final String NEXT_CHALLENGE_ORDER_SQL = """
            order by greatest(coalesce(c1.challenge_count, 0), coalesce(c2.challenge_count, 0)),
                coalesce(c1.challenge_count, 0) + coalesce(c2.challenge_count, 0),
                least(coalesce(c1.challenge_count, 0), coalesce(c2.challenge_count, 0)),
                r1.movie_id,
                r2.movie_id
            fetch first 1 row only
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Optional<MovieChallengeDto> findNextChallenge(String username) {
        String sql = """
                select m1.imdb_id as movie1_id,
                    m1.title as movie1_title,
                    m1.poster as movie1_poster,
                    m2.imdb_id as movie2_id,
                    m2.title as movie2_title,
                    m2.poster as movie2_poster
                """ + NEXT_CHALLENGE_CANDIDATE_SQL + NEXT_CHALLENGE_ORDER_SQL;

        List<MovieChallengeDto> challenges = jdbcTemplate.query(sql, Map.of("username", username), (rs, rowNum) ->
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
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Map.of("username", username), Boolean.class));
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
                        from user_movie_winner_loser_all winner_loser
                        where user_id = :username
                            and (:winnerId = winner_loser.winner_id or :winnerId = winner_loser.loser_id)
                            and (:loserId = winner_loser.winner_id or :loserId = winner_loser.loser_id)
                    )
                then true else false end
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, params(username, winnerId, loserId), Boolean.class));
    }

    public void insertWinnerLoserClosure(String username, String winnerId, String loserId) {
        String sql = """
                insert into user_movie_winner_loser_all (user_id, winner_id, loser_id)
                select :username, ancestors.winner_id, descendants.loser_id
                from (
                    select cast(:winnerId as varchar(32)) as winner_id
                    union
                    select winner_id
                    from user_movie_winner_loser_all
                    where user_id = :username
                        and loser_id = :winnerId
                ) ancestors
                cross join (
                    select cast(:loserId as varchar(32)) as loser_id
                    union
                    select loser_id
                    from user_movie_winner_loser_all
                    where user_id = :username
                        and winner_id = :loserId
                ) descendants
                where ancestors.winner_id <> descendants.loser_id
                    and not exists (
                        select 1
                        from user_movie_winner_loser_all existing
                        where existing.user_id = :username
                            and existing.winner_id = ancestors.winner_id
                            and existing.loser_id = descendants.loser_id
                    )
                """;
        jdbcTemplate.update(sql, params(username, winnerId, loserId));
    }

    public void insertDirectWinnerLoser(String username, String winnerId, String loserId) {
        String sql = """
                insert into user_movie_winner_loser (user_id, winner_id, loser_id)
                select :username, :winnerId, :loserId
                where :winnerId <> :loserId
                    and not exists (
                        select 1
                        from user_movie_winner_loser existing
                        where existing.user_id = :username
                            and existing.winner_id = :winnerId
                            and existing.loser_id = :loserId
                    )
                """;
        jdbcTemplate.update(sql, params(username, winnerId, loserId));
    }


    public void incrementChallengeCount(String username, String movieId) {
        String updateSql = """
                update user_movie_challenge
                set challenge_count = challenge_count + 1
                where user_id = :username
                    and movie_id = :movieId
                """;
        Map<String, String> params = Map.of("username", username, "movieId", movieId);
        if (jdbcTemplate.update(updateSql, params) == 0) {
            String insertSql = """
                    insert into user_movie_challenge (user_id, movie_id, challenge_count)
                    values (:username, :movieId, 1)
                    """;
            jdbcTemplate.update(insertSql, params);
        }
    }

    public int voteCount(String username, String movieId) {
        String sql = """
                select count(1)
                from user_movie_winner_loser_all
                where user_id = :username
                    and winner_id = :movieId
                """;
        return jdbcTemplate.queryForObject(sql, Map.of("username", username, "movieId", movieId), Integer.class);
    }

    public int challengeCount(String username, String movieId) {
        String sql = """
                select coalesce(max(challenge_count), 0)
                from user_movie_challenge
                where user_id = :username
                    and movie_id = :movieId
                """;
        return jdbcTemplate.queryForObject(sql, Map.of("username", username, "movieId", movieId), Integer.class);
    }

    public boolean transitiveWinnerLoserExists(String username, String winnerId, String loserId) {
        String sql = """
                select case when exists (
                    select 1
                    from user_movie_winner_loser_all
                    where user_id = :username
                        and winner_id = :winnerId
                        and loser_id = :loserId
                ) then true else false end
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, params(username, winnerId, loserId), Boolean.class));
    }

    public boolean directWinnerLoserExists(String username, String winnerId, String loserId) {
        String sql = """
                select case when exists (
                    select 1
                    from user_movie_winner_loser
                    where user_id = :username
                        and winner_id = :winnerId
                        and loser_id = :loserId
                ) then true else false end
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, params(username, winnerId, loserId), Boolean.class));
    }

    private Map<String, Object> params(String username, String winnerId, String loserId) {
        return Map.of("username", username, "winnerId", winnerId, "loserId", loserId);
    }
}
