package com.ivanfranchin.moviesapi.bdd.movie.fixture;

import com.ivanfranchin.moviesapi.movie.MovieRepository;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.movie.model.Movie;
import com.ivanfranchin.moviesapi.movie.model.MovieComment;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public class MovieCatalogFixture {

    private final MovieRepository movieRepository;
    private List<MovieDto> movieList;
    private MovieDto selectedMovie;
    private RuntimeException lastError;
    private MvcResult lastResponse;
    private String currentUsername;
    private String currentRole;

    public MovieCatalogFixture(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public void resetPersistentScenarioState() {
        resetScenarioState();
        clearMovies();
    }

    public void resetScenarioState() {
        movieList = null;
        selectedMovie = null;
        lastError = null;
        lastResponse = null;
        currentUsername = null;
        currentRole = null;
    }

    public void clearMovies() {
        movieRepository.deleteAll();
        movieRepository.flush();
    }

    public void saveMoviesWithTitles(String firstTitle, String secondTitle) {
        clearMovies();
        saveMovie("tt-fixture-1", firstTitle);
        saveMovie("tt-fixture-2", secondTitle);
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

    public boolean movieHasComment(String imdbId, String username, String text) {
        return movieRepository.findById(imdbId).orElseThrow().getComments().stream()
                .anyMatch(comment -> comment.getUsername().equals(username) && comment.getText().equals(text));
    }

    public void assertMovieListOrdersTitleBefore(String firstTitle, String secondTitle) {
        List<String> titles = movieList.stream().map(MovieDto::title).toList();
        assertTrue(titles.indexOf(firstTitle) < titles.indexOf(secondTitle),
                "Expected " + firstTitle + " before " + secondTitle + " but got " + titles);
    }

    public void assertMovieListSizeIs(int count) {
        assertEquals(count, movieList.size());
    }

    public void assertSelectedMovieTitleIs(String title) {
        assertEquals(title, selectedMovie.title());
    }

    public void assertFirstSelectedMovieCommentTextIs(String text) {
        assertEquals(text, selectedMovie.comments().getFirst().text());
    }

    public void assertMovieTitleIs(String imdbId, String title) {
        Movie movie = movieRepository.findById(imdbId).orElseThrow();
        assertEquals(title, movie.getTitle());
    }

    public void assertMovieHasComment(String imdbId, String username, String text) {
        assertTrue(movieHasComment(imdbId, username, text));
    }

    public void assertLastErrorIsIllegalArgumentException() {
        assertTrue(lastError instanceof IllegalArgumentException);
    }

    public void assertLastResponseStatus(int status) {
        assertEquals(status, lastResponse.getResponse().getStatus());
    }

    public void assertLastResponseStatusUnauthorizedOrForbidden() {
        int status = lastResponse.getResponse().getStatus();
        assertTrue(status == 401 || status == 403, "Expected 401 or 403 but got " + status);
    }

    public void authenticateRegularUser(String username) {
        currentUsername = username;
        currentRole = "MOVIES_USER";
    }

    public String currentUserEmail() {
        return currentUsername + "@example.com";
    }

    public RequestPostProcessor jwtForCurrentUser() {
        return jwt()
                .jwt(jwt -> jwt
                        .subject(currentUsername)
                        .claim("preferred_username", currentUsername)
                        .claim("email", currentUserEmail()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + currentRole));
    }

    public void movieList(List<MovieDto> movieList) {
        this.movieList = movieList;
    }

    public void selectedMovie(MovieDto selectedMovie) {
        this.selectedMovie = selectedMovie;
    }

    public void lastError(RuntimeException lastError) {
        this.lastError = lastError;
    }

    public void lastResponse(MvcResult lastResponse) {
        this.lastResponse = lastResponse;
    }
}
