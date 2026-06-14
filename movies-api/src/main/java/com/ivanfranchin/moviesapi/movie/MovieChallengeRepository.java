package com.ivanfranchin.moviesapi.movie;

import com.ivanfranchin.moviesapi.movie.dto.MovieChallengeDto;
import com.ivanfranchin.moviesapi.movie.dto.MovieChallengeDto.MovieChallengeMovieDto;
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
            join movies m1 on m1.imdb_id = r1.movie_id
            join movies m2 on m2.imdb_id = r2.movie_id
            left join user_movie_challenge c1
                on c1.user_id = r1.user_id
                and c1.movie_id = r1.movie_id
            left join user_movie_challenge c2
                on c2.user_id = r2.user_id
                and c2.movie_id = r2.movie_id
            where r1.user_id = :username
                and not exists (
                    select 1
                    from user_movie_pair_challenge pair_challenge
                    where pair_challenge.user_id = r1.user_id
                        and pair_challenge.movie1_id = r1.movie_id
                        and pair_challenge.movie2_id = r2.movie_id
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

    public boolean insertPairChallenge(String username, String movie1Id, String movie2Id) {
        String sql = """
                insert into user_movie_pair_challenge (user_id, movie1_id, movie2_id)
                select :username, :movie1Id, :movie2Id
                where :movie1Id < :movie2Id
                    and exists (
                        select 1
                        from movie_recommendations
                        where user_id = :username
                            and movie_id = :movie1Id
                    )
                    and exists (
                        select 1
                        from movie_recommendations
                        where user_id = :username
                            and movie_id = :movie2Id
                    )
                    and not exists (
                        select 1
                        from user_movie_pair_challenge
                        where user_id = :username
                            and movie1_id = :movie1Id
                            and movie2_id = :movie2Id
                    )
                """;
        return jdbcTemplate.update(sql, params(username, movie1Id, movie2Id)) == 1;
    }

    public void incrementVoteCount(String username, String movieId) {
        String updateSql = """
                update movie_user_votes
                set vote_count = vote_count + 1
                where user_id = :username
                    and movie_id = :movieId
                """;
        Map<String, String> params = Map.of("username", username, "movieId", movieId);
        if (jdbcTemplate.update(updateSql, params) == 0) {
            String insertSql = """
                    insert into movie_user_votes (user_id, movie_id, vote_count)
                    values (:username, :movieId, 1)
                    """;
            jdbcTemplate.update(insertSql, params);
        }
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
                select coalesce(max(vote_count), 0)
                from movie_user_votes
                where user_id = :username
                    and movie_id = :movieId
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

    public boolean pairChallengeExists(String username, String movie1Id, String movie2Id) {
        String sql = """
                select case when exists (
                    select 1
                    from user_movie_pair_challenge
                    where user_id = :username
                        and movie1_id = :movie1Id
                        and movie2_id = :movie2Id
                ) then true else false end
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, params(username, movie1Id, movie2Id), Boolean.class));
    }

    private Map<String, String> params(String username, String movie1Id, String movie2Id) {
        return Map.of("username", username, "movie1Id", movie1Id, "movie2Id", movie2Id);
    }
}
