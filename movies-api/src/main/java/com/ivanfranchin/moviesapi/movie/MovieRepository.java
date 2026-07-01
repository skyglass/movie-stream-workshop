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
            join movie_user_votes votes on votes.movie_id = m.imdb_id
            where votes.user_id = :username
                and votes.vote_count > 0
            order by votes.vote_count desc, m.title asc, m.imdb_id asc
            """, nativeQuery = true)
    List<Movie> findFavoriteMoviesByUsername(@Param("username") String username);

    @Query(value = """
            select m.*
            from movies m
            join movie_user_votes votes on votes.movie_id = m.imdb_id
            group by m.imdb_id, m.title, m.director, m.release_year, m.poster
            having sum(votes.vote_count) > 0
            order by sum(votes.vote_count) desc,
                count(distinct votes.user_id) desc,
                m.title asc,
                m.imdb_id asc
            """, nativeQuery = true)
    List<Movie> findUsersFavoriteMovies();

    @Query(value = """
            with relative_user_rating as (
                select other_challenge.user_id,
                    count(*) as relative_rating
                from user_movie_pair_challenge current_challenge
                join user_movie_pair_challenge other_challenge
                    on other_challenge.movie1_id = current_challenge.movie1_id
                    and other_challenge.movie2_id = current_challenge.movie2_id
                    and other_challenge.movie1_wins = current_challenge.movie1_wins
                    and other_challenge.user_id <> current_challenge.user_id
                where current_challenge.user_id = :username
                group by other_challenge.user_id
            ),
            total_relative_rating as (
                select sum(relative_rating) as rating_sum
                from relative_user_rating
            )
            select m.*
            from movies m
            join movie_user_votes votes on votes.movie_id = m.imdb_id
            join relative_user_rating user_rating on user_rating.user_id = votes.user_id
            cross join total_relative_rating total_rating
            where votes.vote_count > 0
                and total_rating.rating_sum > 0
                and not exists (
                    select 1
                    from movie_recommendations recommendation
                    where recommendation.user_id = :username
                        and recommendation.movie_id = m.imdb_id
                )
            group by m.imdb_id, m.title, m.director, m.release_year, m.poster, total_rating.rating_sum
            having sum(votes.vote_count * user_rating.relative_rating) > 0
            order by cast(sum(votes.vote_count * user_rating.relative_rating) as decimal(19, 6))
                    / total_rating.rating_sum desc,
                sum(votes.vote_count * user_rating.relative_rating) desc,
                count(distinct votes.user_id) desc,
                m.title asc,
                m.imdb_id asc
            """, nativeQuery = true)
    List<Movie> findUsersRecommendedMoviesByUsername(@Param("username") String username);
}
