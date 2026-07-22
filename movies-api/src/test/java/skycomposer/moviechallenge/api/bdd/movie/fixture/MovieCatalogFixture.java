package skycomposer.moviechallenge.api.bdd.movie.fixture;

import skycomposer.moviechallenge.api.movie.MovieChallengeRepository;
import skycomposer.moviechallenge.api.movie.MovieRepository;
import skycomposer.moviechallenge.api.movie.MovieRecommendationRepository;
import skycomposer.moviechallenge.api.movie.dto.MovieChallengeDto;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.dto.SuggestedMovieChallengeDto;
import skycomposer.moviechallenge.api.movie.dto.SuggestedMovieChallengePageDto;
import skycomposer.moviechallenge.api.movie.model.Movie;
import skycomposer.moviechallenge.api.movie.model.MovieComment;
import skycomposer.moviechallenge.api.movie.model.MovieRecommendation;
import skycomposer.moviechallenge.api.movie.model.MovieType;
import java.math.BigDecimal;
import java.util.Arrays;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    private static final BigDecimal SCORE_ERROR_80_PER_SIGMA = BigDecimal.valueOf(2.25 * 1.28155);

    private final MovieRepository movieRepository;
    private final MovieRecommendationRepository movieRecommendationRepository;
    private final MovieChallengeRepository movieChallengeRepository;
    private final JdbcTemplate jdbcTemplate;
    private List<MovieDto> movieList;
    private long movieListTotalCount;
    private MovieDto selectedMovie;
    private MovieChallengeDto selectedMovieChallenge;
    private List<SuggestedMovieChallengeDto> suggestedMovieChallenges;
    private long suggestedMovieChallengeTotalCount;
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
        movieListTotalCount = 0;
        selectedMovie = null;
        selectedMovieChallenge = null;
        suggestedMovieChallenges = null;
        suggestedMovieChallengeTotalCount = 0;
        lastError = null;
        lastResponse = null;
        currentUsername = null;
        currentRole = null;
    }

    public void clearMovies() {
        jdbcTemplate.update("delete from user_movie_rank");
        jdbcTemplate.update("delete from user_movie_challenge_vote");
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

    public void saveNumberedMovies(int count) {
        clearMovies();
        for (int i = 1; i <= count; i++) {
            saveMovie("tt-page-" + i, "Movie " + "%02d".formatted(i));
        }
    }

    public Movie saveMovie(String imdbId, String title) {
        Movie movie = new Movie();
        movie.setImdbId(imdbId);
        movie.setTitle(title);
        movie.setDirector("N/A");
        movie.setWriter("N/A");
        movie.setYear("N/A");
        movie.setPoster("");
        movie.setType(MovieType.MOVIE);
        return movieRepository.saveAndFlush(movie);
    }

    public Movie saveMovie(String imdbId, String title, String director, String writer, String year, String genre,
                           String country, MovieType type) {
        Movie movie = new Movie();
        movie.setImdbId(imdbId);
        movie.setTitle(title);
        movie.setDirector(director);
        movie.setWriter(writer);
        movie.setYear(year);
        movie.setPoster("");
        movie.setGenre(genre);
        movie.setCountry(country);
        movie.setType(type);
        return movieRepository.saveAndFlush(movie);
    }

    public void ensureUser(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from users where username = ?",
                Integer.class,
                username);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "insert into users (username, email, avatar) values (?, ?, ?)",
                    username,
                    username + "@skycomposer.net",
                    username);
        }
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
        ensureUser(username);
        movieRecommendationRepository.saveAndFlush(new MovieRecommendation(username, imdbId));
    }

    public void dislikeMovie(String imdbId, String username) {
        ensureUser(username);
        movieRecommendationRepository.saveAndFlush(new MovieRecommendation(username, imdbId, false));
    }

    public boolean movieIsRecommendedBy(String imdbId, String username) {
        return movieRecommendationRepository.existsByUsernameAndMovieImdbIdAndPositiveTrue(username, imdbId);
    }

    public boolean movieIsDislikedBy(String imdbId, String username) {
        return movieRecommendationRepository.existsByUsernameAndMovieImdbIdAndPositiveFalse(username, imdbId);
    }

    public void incrementChallengeCount(String imdbId, String username, int times) {
        ensureUser(username);
        for (int i = 0; i < times; i++) {
            String loserId = imdbId + "-comparison-" + i + "-" + username;
            if (!movieRepository.existsById(loserId)) {
                saveMovie(loserId, "Synthetic Comparison " + i + " for " + imdbId);
            }
            recordWinnerLoser(username, imdbId, loserId);
        }
    }

    public void completeMoviePairChallenge(String username, String firstMovieId, String secondMovieId) {
        ensureUser(username);
        recordWinnerLoser(username, firstMovieId, secondMovieId);
    }

    public void recordWinnerLoser(String username, String winnerId, String loserId) {
        ensureUser(username);
        recommendMovie(winnerId, username);
        recommendMovie(loserId, username);
        movieChallengeRepository.insertDirectWinnerLoser(username, winnerId, loserId);
        movieChallengeRepository.rebuildUserMovieRanks(username);
    }

    public void recordOrderedRankingExceptPair(String username,
                                               String orderedMovieIds,
                                               String firstExcludedMovieId,
                                               String secondExcludedMovieId) {
        List<String> movieIds = Arrays.stream(orderedMovieIds.split(","))
                .map(String::trim)
                .filter(movieId -> !movieId.isBlank())
                .toList();
        MoviePair excludedPair = sortedPair(firstExcludedMovieId, secondExcludedMovieId);

        for (int winnerIndex = 0; winnerIndex < movieIds.size(); winnerIndex++) {
            for (int loserIndex = winnerIndex + 1; loserIndex < movieIds.size(); loserIndex++) {
                String winnerId = movieIds.get(winnerIndex);
                String loserId = movieIds.get(loserIndex);
                if (!sortedPair(winnerId, loserId).equals(excludedPair)) {
                    recordWinnerLoser(username, winnerId, loserId);
                }
            }
        }
    }

    public void recordOrderedRanking(String username, String orderedMovieIds) {
        List<String> movieIds = Arrays.stream(orderedMovieIds.split(","))
                .map(String::trim)
                .filter(movieId -> !movieId.isBlank())
                .toList();

        for (int winnerIndex = 0; winnerIndex < movieIds.size(); winnerIndex++) {
            for (int loserIndex = winnerIndex + 1; loserIndex < movieIds.size(); loserIndex++) {
                recordWinnerLoser(username, movieIds.get(winnerIndex), movieIds.get(loserIndex));
            }
        }
    }

    public void recordTransitiveWins(String imdbId, String username, int count) {
        ensureUser(username);
        for (int i = 1; i <= count; i++) {
            String loserId = imdbId + "-loser-" + i + "-" + username;
            if (!movieRepository.existsById(loserId)) {
                saveMovie(loserId, "Synthetic Loser " + i + " for " + imdbId);
            }
            recordWinnerLoser(username, imdbId, loserId);
        }
    }

    public void setMovieRank(String imdbId, String username, int rankPosition, int directComparisons) {
        BigDecimal score = BigDecimal.valueOf(Math.max(1, 11 - rankPosition));
        setMovieRank(
                imdbId,
                username,
                rankPosition,
                directComparisons,
                score.subtract(BigDecimal.valueOf(5.5)),
                BigDecimal.valueOf(2.0 / Math.sqrt(Math.max(directComparisons, 1))));
    }

    public void setMovieRank(String imdbId,
                             String username,
                             int rankPosition,
                             int directComparisons,
                             BigDecimal mu,
                             BigDecimal sigma) {
        ensureUser(username);
        jdbcTemplate.update(
                "delete from user_movie_rank where user_id = ? and movie_id = ?",
                username,
                imdbId);
        jdbcTemplate.update(
                """
                insert into user_movie_rank (
                    user_id,
                    movie_id,
                    rank_position,
                    score,
                    direct_comparisons,
                    mu,
                    sigma,
                    score_error_80
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                username,
                imdbId,
                rankPosition,
                BigDecimal.valueOf(Math.max(1, 11 - rankPosition)),
                directComparisons,
                mu,
                sigma,
                SCORE_ERROR_80_PER_SIGMA.multiply(sigma).min(BigDecimal.valueOf(9)));
    }

    public void assertMovieListOrdersTitleBefore(String firstTitle, String secondTitle) {
        List<String> titles = movieList.stream().map(MovieDto::title).toList();
        assertTrue(titles.indexOf(firstTitle) < titles.indexOf(secondTitle),
                "Expected " + firstTitle + " before " + secondTitle + " but got " + titles);
    }

    public void assertMovieListOrdersImdbIdBefore(String firstImdbId, String secondImdbId) {
        List<String> imdbIds = movieList.stream().map(MovieDto::imdbId).toList();
        assertTrue(imdbIds.indexOf(firstImdbId) < imdbIds.indexOf(secondImdbId),
                "Expected " + firstImdbId + " before " + secondImdbId + " but got " + imdbIds);
    }

    public void assertMovieListOrderedExactly(String commaSeparatedImdbIds) {
        List<String> expected = Arrays.stream(commaSeparatedImdbIds.split(","))
                .map(String::trim).filter(id -> !id.isBlank()).toList();
        List<String> actual = movieList.stream().map(MovieDto::imdbId).toList();
        assertEquals(expected, actual);
    }

    public void assertMovieListDoesNotContainImdbId(String imdbId) {
        List<String> imdbIds = movieList.stream().map(MovieDto::imdbId).toList();
        assertFalse(imdbIds.contains(imdbId), "Expected movie list not to contain " + imdbId + " but got " + imdbIds);
    }

    public void assertMovieListContainsImdbId(String imdbId) {
        List<String> imdbIds = movieList.stream().map(MovieDto::imdbId).toList();
        assertTrue(imdbIds.contains(imdbId), "Expected movie list to contain " + imdbId + " but got " + imdbIds);
    }

    public void assertMovieListSizeIs(int count) {
        assertEquals(count, movieList.size());
    }

    public void assertMovieListTotalCountIs(long count) {
        assertEquals(count, movieListTotalCount);
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

    public void assertSelectedMovieDislikedIs(boolean disliked) {
        assertEquals(disliked, selectedMovie.disliked());
    }

    public void assertSelectedMovieChallengeContains(String firstMovieId, String secondMovieId) {
        assertNotNull(selectedMovieChallenge, "Expected a movie challenge to be available");
        MoviePair expected = sortedPair(firstMovieId, secondMovieId);
        MoviePair actual = sortedPair(
                selectedMovieChallenge.movie1().imdbId(),
                selectedMovieChallenge.movie2().imdbId());
        assertEquals(expected, actual);
    }

    public void assertSelectedMovieChallengeIs(String firstMovieId, String secondMovieId) {
        assertNotNull(selectedMovieChallenge, "Expected a movie challenge to be available");
        assertEquals(firstMovieId, selectedMovieChallenge.movie1().imdbId());
        assertEquals(secondMovieId, selectedMovieChallenge.movie2().imdbId());
    }

    public void assertSelectedMovieChallengeDoesNotContain(String firstMovieId, String secondMovieId) {
        assertNotNull(selectedMovieChallenge, "Expected a movie challenge to be available");
        MoviePair unexpected = sortedPair(firstMovieId, secondMovieId);
        MoviePair actual = sortedPair(
                selectedMovieChallenge.movie1().imdbId(),
                selectedMovieChallenge.movie2().imdbId());
        assertFalse(unexpected.equals(actual), "Expected challenge not to contain " + unexpected);
    }

    public void assertNoMovieChallengeAvailable() {
        assertNull(selectedMovieChallenge);
    }

    public void assertSuggestedMovieChallengeListSizeIs(int count) {
        assertNotNull(suggestedMovieChallenges, "Expected suggested movie challenges to be loaded");
        assertEquals(count, suggestedMovieChallenges.size());
    }

    public void assertSuggestedMovieChallengeTotalCountIs(long count) {
        assertEquals(count, suggestedMovieChallengeTotalCount);
    }

    public void assertSuggestedMovieChallengeIs(int number, String firstMovieId, String secondMovieId) {
        SuggestedMovieChallengeDto challenge = suggestedMovieChallenge(number);
        assertEquals(firstMovieId, challenge.movie1().imdbId(), suggestedMovieChallengeSummary());
        assertEquals(secondMovieId, challenge.movie2().imdbId(), suggestedMovieChallengeSummary());
    }

    public void assertSuggestedMovieChallengeMovieProbabilityAndRank(int number,
                                                                     String movieId,
                                                                     int winProbabilityPercent,
                                                                     int rankPosition) {
        SuggestedMovieChallengeDto challenge = suggestedMovieChallenge(number);
        SuggestedMovieChallengeDto.SuggestedMovieChallengeMovieDto movie = List.of(challenge.movie1(), challenge.movie2())
                .stream()
                .filter(candidate -> candidate.imdbId().equals(movieId))
                .findFirst()
                .orElseThrow();
        assertEquals(winProbabilityPercent, movie.winProbabilityPercent());
        assertEquals(rankPosition, movie.rankPosition());
    }

    public void assertMovieVoteCount(String imdbId, String username, int expectedCount) {
        assertEquals(expectedCount, movieChallengeRepository.voteCount(username, imdbId));
    }

    public void assertMovieChallengeCount(String imdbId, String username, int expectedCount) {
        assertEquals(expectedCount, movieChallengeRepository.challengeCount(username, imdbId));
    }

    public void assertMovieRankAndRating(String imdbId, String username, int rankPosition, String rating) {
        var movieRating = movieChallengeRepository.movieRating(username, imdbId).orElseThrow();
        assertEquals(rankPosition, movieRating.rankPosition());
        assertEquals(0, new BigDecimal(rating).compareTo(movieRating.rating()));
    }

    public void assertMovieHasNoRankAndRating(String imdbId, String username) {
        assertTrue(movieChallengeRepository.movieRating(username, imdbId).isEmpty());
    }

    public void assertMovieHasNoDirectChallengeVotes(String imdbId, String username) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(1)
                from user_movie_challenge_vote
                where user_id = ?
                    and (winner_id = ? or loser_id = ?)
                """,
                Integer.class,
                username,
                imdbId,
                imdbId);
        assertEquals(0, count);
    }

    public void assertMovieHasNoChallengeCount(String imdbId, String username) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(1)
                from user_movie_challenge
                where user_id = ?
                    and movie_id = ?
                """,
                Integer.class,
                username,
                imdbId);
        assertEquals(0, count);
    }

    public void assertMoviePairChallengeExists(String username, String firstMovieId, String secondMovieId) {
        assertTrue(movieChallengeRepository.directWinnerLoserExists(username, firstMovieId, secondMovieId)
                || movieChallengeRepository.directWinnerLoserExists(username, secondMovieId, firstMovieId));
    }

    public void assertDirectWinnerLoserExists(String username, String winnerId, String loserId) {
        assertTrue(movieChallengeRepository.directWinnerLoserExists(username, winnerId, loserId));
    }

    public void assertDirectWinnerLoserDoesNotExist(String username, String winnerId, String loserId) {
        assertFalse(movieChallengeRepository.directWinnerLoserExists(username, winnerId, loserId));
    }

    public void assertTransitiveWinnerLoserExists(String username, String winnerId, String loserId) {
        assertTrue(movieChallengeRepository.transitiveWinnerLoserExists(username, winnerId, loserId));
    }

    public void assertMovieListItemRecommendationIs(String imdbId, boolean recommended) {
        MovieDto movie = movieList.stream()
                .filter(item -> item.imdbId().equals(imdbId))
                .findFirst()
                .orElseThrow();
        assertEquals(recommended, movie.recommended());
    }

    public void assertMovieListItemDislikedIs(String imdbId, boolean disliked) {
        MovieDto movie = movieList.stream()
                .filter(item -> item.imdbId().equals(imdbId))
                .findFirst()
                .orElseThrow();
        assertEquals(disliked, movie.disliked());
    }

    public void assertMovieTitleIs(String imdbId, String title) {
        Movie movie = movieRepository.findById(imdbId).orElseThrow();
        assertEquals(title, movie.getTitle());
    }

    public void assertMovieMetadataIs(String imdbId, String director, String writer, String year, String genre,
                                      String country, MovieType type) {
        Movie movie = movieRepository.findById(imdbId).orElseThrow();
        assertEquals(director, movie.getDirector());
        assertEquals(writer, movie.getWriter());
        assertEquals(year, movie.getYear());
        assertEquals(genre, movie.getGenre());
        assertEquals(country, movie.getCountry());
        assertEquals(type, movie.getType());
    }

    public void assertSelectedMovieMetadataIs(String director, String writer, String year, String genre,
                                              String country, MovieType type) {
        assertEquals(director, selectedMovie.director());
        assertEquals(writer, selectedMovie.writer());
        assertEquals(year, selectedMovie.year());
        assertEquals(genre, selectedMovie.genre());
        assertEquals(country, selectedMovie.country());
        assertEquals(type, selectedMovie.type());
        assertEquals(type.getDescription(), selectedMovie.typeDescription());
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

    public void assertMovieDislikedBy(String imdbId, String username) {
        assertTrue(movieIsDislikedBy(imdbId, username));
    }

    public void assertMovieNotDislikedBy(String imdbId, String username) {
        assertFalse(movieIsDislikedBy(imdbId, username));
    }

    public void assertMyFavoriteMoviesPublic(String username, boolean expected) {
        Boolean actual = jdbcTemplate.queryForObject(
                """
                select is_my_favorite_movies_public
                from user_settings
                where username = ?
                """,
                Boolean.class,
                username);
        assertEquals(expected, actual);
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

    public void authenticateAdminUser(String username) {
        currentUsername = username;
        currentRole = "MOVIES_ADMIN";
    }

    public String currentUserEmail() {
        return currentUsername + "@skycomposer.net";
    }

    public RequestPostProcessor jwtForCurrentUser() {
        return jwt()
                .jwt(jwt -> jwt
                        .subject(currentUsername)
                        .claim("preferred_username", currentUsername)
                        .claim("email", currentUserEmail()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + currentRole));
    }

    public Pageable firstMoviePage() {
        return moviePage(1, 50);
    }

    public Pageable moviePage(int page, int pageSize) {
        return PageRequest.of(page - 1, pageSize);
    }

    public void moviePage(MoviePageDto moviePage) {
        this.movieList = moviePage.movies();
        this.movieListTotalCount = moviePage.totalCount();
    }

    public void movieList(List<MovieDto> movieList) {
        this.movieList = movieList;
        this.movieListTotalCount = movieList.size();
    }

    public void selectedMovie(MovieDto selectedMovie) {
        this.selectedMovie = selectedMovie;
    }

    public void selectedMovieChallenge(MovieChallengeDto selectedMovieChallenge) {
        this.selectedMovieChallenge = selectedMovieChallenge;
    }

    public void suggestedMovieChallengePage(SuggestedMovieChallengePageDto suggestedMovieChallengePage) {
        this.suggestedMovieChallenges = suggestedMovieChallengePage.challenges();
        this.suggestedMovieChallengeTotalCount = suggestedMovieChallengePage.totalCount();
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

    private SuggestedMovieChallengeDto suggestedMovieChallenge(int number) {
        assertNotNull(suggestedMovieChallenges, "Expected suggested movie challenges to be loaded");
        return suggestedMovieChallenges.get(number - 1);
    }

    private String suggestedMovieChallengeSummary() {
        if (suggestedMovieChallenges == null) {
            return "No suggested movie challenges loaded";
        }
        return suggestedMovieChallenges.stream()
                .map(challenge -> challenge.movie1().imdbId()
                        + "(" + challenge.movie1().rating() + ")"
                        + " vs "
                        + challenge.movie2().imdbId()
                        + "(" + challenge.movie2().rating() + ")")
                .toList()
                .toString();
    }

    private record MoviePair(String movie1Id, String movie2Id) {
    }
}
