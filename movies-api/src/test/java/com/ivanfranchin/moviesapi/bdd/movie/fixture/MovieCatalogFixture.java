package com.ivanfranchin.moviesapi.bdd.movie.fixture;

import com.ivanfranchin.moviesapi.movie.MovieChallengeRepository;
import com.ivanfranchin.moviesapi.movie.MovieRepository;
import com.ivanfranchin.moviesapi.movie.MovieRecommendationRepository;
import com.ivanfranchin.moviesapi.movie.dto.MovieChallengeDto;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.movie.model.Movie;
import com.ivanfranchin.moviesapi.movie.model.MovieComment;
import com.ivanfranchin.moviesapi.movie.model.MovieRecommendation;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public class MovieCatalogFixture {

    private final MovieRepository movieRepository;
    private final MovieRecommendationRepository movieRecommendationRepository;
    private final MovieChallengeRepository movieChallengeRepository;
    private final JdbcTemplate jdbcTemplate;
    private List<MovieDto> movieList;
    private MovieDto selectedMovie;
    private MovieChallengeDto selectedMovieChallenge;
    private RuntimeException lastError;
    private MvcResult lastResponse;
    private String currentUsername;
    private String currentRole;

    public MovieCatalogFixture(MovieRepository movieRepository,
                               MovieRecommendationRepository movieRecommendationRepository,
                               MovieChallengeRepository movieChallengeRepository,
                               JdbcTemplate jdbcTemplate) {
        this.movieRepository = movieRepository;
        this.movieRecommendationRepository = movieRecommendationRepository;
        this.movieChallengeRepository = movieChallengeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void resetPersistentScenarioState() {
        resetScenarioState();
        clearMovies();
    }

    public void resetScenarioState() {
        movieList = null;
        selectedMovie = null;
        selectedMovieChallenge = null;
        lastError = null;
        lastResponse = null;
        currentUsername = null;
        currentRole = null;
    }

    public void clearMovies() {
        jdbcTemplate.update("delete from movie_user_votes");
        jdbcTemplate.update("delete from user_movie_pair_challenge");
        jdbcTemplate.update("delete from user_movie_challenge");
        movieRecommendationRepository.deleteAll();
        movieRecommendationRepository.flush();
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

    public void recommendMovie(String imdbId, String username) {
        movieRecommendationRepository.saveAndFlush(new MovieRecommendation(username, imdbId));
    }

    public boolean movieIsRecommendedBy(String imdbId, String username) {
        return movieRecommendationRepository.existsByUsernameAndMovieImdbId(username, imdbId);
    }

    public void incrementChallengeCount(String imdbId, String username, int times) {
        for (int i = 0; i < times; i++) {
            movieChallengeRepository.incrementChallengeCount(username, imdbId);
        }
    }

    public void completeMoviePairChallenge(String username, String firstMovieId, String secondMovieId) {
        MoviePair pair = sortedPair(firstMovieId, secondMovieId);
        assertTrue(movieChallengeRepository.insertPairChallenge(username, pair.movie1Id(), pair.movie2Id()));
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

    public void assertSelectedMovieImdbIdIs(String imdbId) {
        assertEquals(imdbId, selectedMovie.imdbId());
    }

    public void assertFirstSelectedMovieCommentTextIs(String text) {
        assertEquals(text, selectedMovie.comments().getFirst().text());
    }

    public void assertSelectedMovieRecommendationIs(boolean recommended) {
        assertEquals(recommended, selectedMovie.recommended());
    }

    public void assertSelectedMovieChallengeContains(String firstMovieId, String secondMovieId) {
        assertNotNull(selectedMovieChallenge, "Expected a movie challenge to be available");
        MoviePair expected = sortedPair(firstMovieId, secondMovieId);
        MoviePair actual = sortedPair(
                selectedMovieChallenge.movie1().imdbId(),
                selectedMovieChallenge.movie2().imdbId());
        assertEquals(expected, actual);
    }

    public void assertNoMovieChallengeAvailable() {
        assertNull(selectedMovieChallenge);
    }

    public void assertMovieVoteCount(String imdbId, String username, int expectedCount) {
        assertEquals(expectedCount, movieChallengeRepository.voteCount(username, imdbId));
    }

    public void assertMovieChallengeCount(String imdbId, String username, int expectedCount) {
        assertEquals(expectedCount, movieChallengeRepository.challengeCount(username, imdbId));
    }

    public void assertMoviePairChallengeExists(String username, String firstMovieId, String secondMovieId) {
        MoviePair pair = sortedPair(firstMovieId, secondMovieId);
        assertTrue(movieChallengeRepository.pairChallengeExists(username, pair.movie1Id(), pair.movie2Id()));
    }

    public void assertMovieListItemRecommendationIs(String imdbId, boolean recommended) {
        MovieDto movie = movieList.stream()
                .filter(item -> item.imdbId().equals(imdbId))
                .findFirst()
                .orElseThrow();
        assertEquals(recommended, movie.recommended());
    }

    public void assertMovieTitleIs(String imdbId, String title) {
        Movie movie = movieRepository.findById(imdbId).orElseThrow();
        assertEquals(title, movie.getTitle());
    }

    public void assertMovieHasComment(String imdbId, String username, String text) {
        assertTrue(movieHasComment(imdbId, username, text));
    }

    public void assertMovieRecommendedBy(String imdbId, String username) {
        assertTrue(movieIsRecommendedBy(imdbId, username));
    }

    public void assertMovieNotRecommendedBy(String imdbId, String username) {
        assertFalse(movieIsRecommendedBy(imdbId, username));
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

    public void selectedMovieChallenge(MovieChallengeDto selectedMovieChallenge) {
        this.selectedMovieChallenge = selectedMovieChallenge;
    }

    public void lastError(RuntimeException lastError) {
        this.lastError = lastError;
    }

    public void lastResponse(MvcResult lastResponse) {
        this.lastResponse = lastResponse;
    }

    private MoviePair sortedPair(String firstMovieId, String secondMovieId) {
        return firstMovieId.compareTo(secondMovieId) < 0
                ? new MoviePair(firstMovieId, secondMovieId)
                : new MoviePair(secondMovieId, firstMovieId);
    }

    private record MoviePair(String movie1Id, String movie2Id) {
    }
}
