package com.ivanfranchin.moviesapi.bdd;

import com.ivanfranchin.moviesapi.movie.MovieRepository;
import com.ivanfranchin.moviesapi.movie.model.Movie;
import com.ivanfranchin.moviesapi.movie.model.MovieComment;
import com.ivanfranchin.moviesapi.userextra.UserExtraRepository;
import com.ivanfranchin.moviesapi.userextra.model.UserExtra;
import java.time.Instant;

/**
 * Test-only fixture API for Cucumber scenarios.
 */
public class MovieStreamCucumberFixture {

    private final MovieRepository movieRepository;
    private final UserExtraRepository userExtraRepository;

    MovieStreamCucumberFixture(MovieRepository movieRepository, UserExtraRepository userExtraRepository) {
        this.movieRepository = movieRepository;
        this.userExtraRepository = userExtraRepository;
    }

    public void resetPersistentScenarioState() {
        movieRepository.deleteAll();
        userExtraRepository.deleteAll();
        userExtraRepository.save(new UserExtra("admin", "admin@example.com"));
        userExtraRepository.save(new UserExtra("user", "user@example.com"));
        userExtraRepository.flush();
    }

    public void clearMovies() {
        movieRepository.deleteAll();
        movieRepository.flush();
    }

    public Movie saveMovie(String imdbId, String title) {
        Movie movie = new Movie();
        movie.setImdbId(imdbId);
        movie.setTitle(title);
        movie.setDirector("N/A");
        movie.setYear("N/A");
        movie.setPoster("");
        return movieRepository.saveAndFlush(movie);
    }

    public void addComment(String imdbId, String username, String text) {
        Movie movie = movieRepository.findById(imdbId).orElseThrow();
        movie.addComment(new MovieComment(username, text, Instant.now()));
        movieRepository.saveAndFlush(movie);
    }
}
