package skycomposer.moviechallenge.api.bdd.movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.ViewCategorySimilarMoviesUseCase;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ViewSimilarMoviesAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final ViewCategorySimilarMoviesUseCase viewCategorySimilarMovies;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ViewSimilarMoviesAcceptanceTest(MovieCatalogFixture fixture,
                                           ViewCategorySimilarMoviesUseCase viewCategorySimilarMovies,
                                           MockMvc mockMvc,
                                           ObjectMapper objectMapper) {
        this.fixture = fixture;
        this.viewCategorySimilarMovies = viewCategorySimilarMovies;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @When("regular user {string} requests movies similar to movie {string}")
    public void regularUserRequestsMoviesSimilarToMovie(String username, String imdbId) {
        fixture.moviePage(viewCategorySimilarMovies.viewSimilarMoviesTo(username, imdbId, fixture.firstMoviePage(), null, null, List.of()));
    }

    // Open to anonymous viewers (unlike "similar to favorite movies", which stays account-only) -- a signed-in
    // viewer's own rated movies personalize the categories being scored, an anonymous one simply has none, so
    // this degrades to an empty page rather than a 401/403.
    @When("anonymous viewer requests movies similar to movie {string}")
    public void anonymousViewerRequestsMoviesSimilarToMovie(String imdbId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/movies/{imdbId}/similar-movies", imdbId)).andReturn();
        fixture.lastResponse(result);
        fixture.moviePage(objectMapper.readValue(result.getResponse().getContentAsString(), MoviePageDto.class));
    }

    @Then("movies similar to that movie contain movie {string}")
    public void moviesSimilarToThatMovieContainMovie(String imdbId) {
        fixture.assertMovieListContainsImdbId(imdbId);
    }

    @Then("movies similar to that movie do not contain {string}")
    public void moviesSimilarToThatMovieDoNotContain(String imdbId) {
        fixture.assertMovieListDoesNotContainImdbId(imdbId);
    }

    @Then("movies similar to that movie contain {int} movies")
    public void moviesSimilarToThatMovieContainMovies(int count) {
        fixture.assertMovieListSizeIs(count);
    }
}
