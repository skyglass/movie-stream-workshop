package com.ivanfranchin.moviesapi.movie;

import com.ivanfranchin.moviesapi.movie.model.Movie;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, String> {

    @Query(value = """
            select m.*
            from movies m
            join (
                select winner_id, count(1) as win_count
                from user_movie_winner_loser_all
                where user_id = :username
                group by winner_id
            ) wins on wins.winner_id = m.imdb_id
            order by wins.win_count desc, m.title asc, m.imdb_id asc
            """, nativeQuery = true)
    List<Movie> findFavoriteMoviesByUsername(@Param("username") String username);

    @Query(value = """
            select m.*
            from movies m
            join user_movie_winner_loser_all wins on wins.winner_id = m.imdb_id
            group by m.imdb_id, m.title, m.director, m.release_year, m.poster
            order by count(1) desc,
                count(distinct wins.user_id) desc,
                m.title asc,
                m.imdb_id asc
            """, nativeQuery = true)
    List<Movie> findUsersFavoriteMovies();

    @Query(value = """
            select m.*
            from movies m
            join (
                select user_id, winner_id as movie_id, count(1) as win_count
                from user_movie_winner_loser_all
                group by user_id, winner_id
            ) wins on wins.movie_id = m.imdb_id
            join (
                select other_winner_loser.user_id,
                    count(*) as relative_rating
                from user_movie_winner_loser_all current_winner_loser
                join user_movie_winner_loser_all other_winner_loser
                    on other_winner_loser.winner_id = current_winner_loser.winner_id
                    and other_winner_loser.loser_id = current_winner_loser.loser_id
                    and other_winner_loser.user_id <> current_winner_loser.user_id
                where current_winner_loser.user_id = :username
                group by other_winner_loser.user_id
            ) user_rating on user_rating.user_id = wins.user_id
            where not exists (
                    select 1
                    from movie_recommendations recommendation
                    where recommendation.user_id = :username
                        and recommendation.movie_id = m.imdb_id
                )
            group by m.imdb_id, m.title, m.director, m.release_year, m.poster
            having sum(wins.win_count * user_rating.relative_rating) > 0
            order by sum(wins.win_count * user_rating.relative_rating) desc,
                count(distinct wins.user_id) desc,
                m.title asc,
                m.imdb_id asc
            """, nativeQuery = true)
    List<Movie> findUsersRecommendedMoviesByUsername(@Param("username") String username);
}
