package skycomposer.moviechallenge.api.bdd.movie;

import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.ViewMovieDetailsUseCase;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import skycomposer.moviechallenge.api.movie.model.MovieType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ViewMovieDetailsAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final ViewMovieDetailsUseCase viewMovieDetails;
    private final MockMvc mockMvc;

    public ViewMovieDetailsAcceptanceTest(MovieCatalogFixture fixture,
                                          ViewMovieDetailsUseCase viewMovieDetails,
                                          MockMvc mockMvc) {
        this.fixture = fixture;
        this.viewMovieDetails = viewMovieDetails;
        this.mockMvc = mockMvc;
    }

    @When("the viewer opens details for movie {string}")
    public void theViewerOpensDetailsForMovie(String imdbId) {
        fixture.selectedMovie(viewMovieDetails.viewMovie(imdbId));
    }

    @When("regular user {string} opens details for movie {string}")
    public void regularUserOpensDetailsForMovie(String username, String imdbId) {
        fixture.selectedMovie(viewMovieDetails.viewMovie(imdbId, username));
    }

    @Given("movie {string} for detail viewing has comment {string} by {string}")
    public void movieForDetailViewingHasCommentBy(String imdbId, String text, String username) {
        fixture.addComment(imdbId, username, text);
    }

    @Then("the viewed movie details show title {string}")
    public void theViewedMovieDetailsShowTitle(String title) {
        fixture.assertSelectedMovieTitleIs(title);
    }

    @Then("the viewed movie details show director {string}, writer {string}, year {string}, genre {string}, country {string}, and type {string}")
    public void theViewedMovieDetailsShowMetadata(String director, String writer, String year, String genre,
                                                  String country, String type) {
        fixture.assertSelectedMovieMetadataIs(director, writer, year, genre, country, MovieType.fromValue(type));
    }

    @Then("the first viewed movie comment is {string}")
    public void theFirstViewedMovieCommentIs(String text) {
        fixture.assertFirstSelectedMovieCommentTextIs(text);
    }

    @Then("the viewed movie is marked recommended")
    public void theViewedMovieIsMarkedRecommended() {
        fixture.assertSelectedMovieRecommendationIs(true);
    }

    @When("anonymous viewer requests movie details for {string}")
    public void anonymousViewerRequestsMovieDetailsFor(String imdbId) throws Exception {
        fixture.lastResponse(mockMvc.perform(get("/api/movies/{imdbId}", imdbId)).andReturn());
    }
}
