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
}
