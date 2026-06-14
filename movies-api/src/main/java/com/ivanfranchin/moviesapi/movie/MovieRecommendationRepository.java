package com.ivanfranchin.moviesapi.movie;

import com.ivanfranchin.moviesapi.movie.model.MovieRecommendation;
import com.ivanfranchin.moviesapi.movie.model.MovieRecommendationId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRecommendationRepository extends JpaRepository<MovieRecommendation, MovieRecommendationId> {

    boolean existsByUsernameAndMovieImdbId(String username, String movieImdbId);

    void deleteByUsernameAndMovieImdbId(String username, String movieImdbId);

    List<MovieRecommendation> findByUsername(String username);
}
