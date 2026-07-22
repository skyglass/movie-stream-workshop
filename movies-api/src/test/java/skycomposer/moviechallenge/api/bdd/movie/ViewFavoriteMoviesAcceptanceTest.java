package skycomposer.moviechallenge.api.bdd.movie;

import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.ViewFavoriteMoviesUseCase;
import skycomposer.moviechallenge.api.bdd.fixture.RestApiFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ViewFavoriteMoviesAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final ViewFavoriteMoviesUseCase viewFavoriteMovies;
    private final RestApiFixture restApi;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public ViewFavoriteMoviesAcceptanceTest(MovieCatalogFixture fixture, ViewFavoriteMoviesUseCase viewFavoriteMovies,
                                            RestApiFixture restApi, MockMvc mockMvc, ObjectMapper objectMapper,
                                            JdbcTemplate jdbc) {
        this.fixture = fixture;
        this.viewFavoriteMovies = viewFavoriteMovies;
        this.restApi = restApi;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @When("regular user {string} submits favorite movie ranks {string}")
    public void submitsFavoriteMovieRanks(String username, String commaSeparatedImdbIds) throws Exception {
        var imdbIds = Arrays.stream(commaSeparatedImdbIds.split(",")).map(String::trim).toList();
        var result = mockMvc.perform(post("/api/favorite-movies/ranking")
                .with(restApi.jwt(username, "MOVIES_USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("orderedImdbIds", imdbIds)))).andReturn();
        fixture.lastResponse(result);
    }

    @Then("movie {string} keeps exact rank {int} for {string}")
    public void movieKeepsExactRank(String imdbId, int rank, String username) {
        Integer actual = jdbc.queryForObject(
                "select rank_position from user_movie_rank where user_id=? and movie_id=?",
                Integer.class, username, imdbId);
        assertEquals(rank, actual);
    }

    @When("regular user {string} requests favorite movies")
    public void regularUserRequestsFavoriteMovies(String username) {
        fixture.moviePage(viewFavoriteMovies.viewFavoriteMovies(username, fixture.firstMoviePage()));
    }

    @When("regular user {string} requests page {int} of favorite movies with {int} movies per page")
    public void regularUserRequestsPageOfFavoriteMoviesWithMoviesPerPage(String username, int page, int pageSize) {
        fixture.moviePage(viewFavoriteMovies.viewFavoriteMovies(username, fixture.moviePage(page, pageSize)));
    }

    @When("regular user {string} requests favorite movies filtered by {string}")
    public void regularUserRequestsFavoriteMoviesFilteredBy(String username, String filter) {
        fixture.moviePage(viewFavoriteMovies.viewFavoriteMovies(username, fixture.firstMoviePage(), filter));
    }

    @Then("favorite movies show {string} before {string}")
    public void favoriteMoviesShowBefore(String firstImdbId, String secondImdbId) {
        fixture.assertMovieListOrdersImdbIdBefore(firstImdbId, secondImdbId);
    }

    @Then("favorite movies contain {int} movies")
    public void favoriteMoviesContainMovies(int count) {
        fixture.assertMovieListSizeIs(count);
    }

    @Then("favorite movies total count is {int}")
    public void favoriteMoviesTotalCountIs(int count) {
        fixture.assertMovieListTotalCountIs(count);
    }

    @Then("favorite movies contain movie {string}")
    public void favoriteMoviesContainMovie(String imdbId) {
        fixture.assertMovieListContainsImdbId(imdbId);
    }

    @Then("favorite movies do not contain movie {string}")
    public void favoriteMoviesDoNotContainMovie(String imdbId) {
        fixture.assertMovieListDoesNotContainImdbId(imdbId);
    }
}
