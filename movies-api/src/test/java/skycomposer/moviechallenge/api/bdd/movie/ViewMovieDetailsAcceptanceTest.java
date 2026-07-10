package skycomposer.moviechallenge.api.bdd.movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.ViewMovieDetailsUseCase;
import skycomposer.moviechallenge.api.movie.dto.MovieRankHistoryDto;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Map;
import skycomposer.moviechallenge.api.movie.model.MovieType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ViewMovieDetailsAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final ViewMovieDetailsUseCase viewMovieDetails;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ViewMovieDetailsAcceptanceTest(MovieCatalogFixture fixture,
                                          ViewMovieDetailsUseCase viewMovieDetails,
                                          MockMvc mockMvc,
                                          ObjectMapper objectMapper) {
        this.fixture = fixture;
        this.viewMovieDetails = viewMovieDetails;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
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

    @Given("user {string} has rank history display values")
    public void userHasRankHistoryDisplayValues(String username, DataTable dataTable) {
        fixture.setMovieRanks(username, dataTable.asMaps());
    }

    @When("regular user {string} opens rank history for movie {string}")
    public void regularUserOpensRankHistoryForMovie(String username, String imdbId) throws Exception {
        fixture.authenticateRegularUser(username);
        var result = mockMvc.perform(get("/api/movies/{imdbId}/rank-history", imdbId)
                        .with(fixture.jwtForCurrentUser()))
                .andReturn();
        fixture.lastResponse(result);
        fixture.selectedMovieRankHistory(objectMapper.readValue(
                result.getResponse().getContentAsString(),
                MovieRankHistoryDto.class));
    }

    @When("anonymous viewer requests rank history for movie {string}")
    public void anonymousViewerRequestsRankHistoryForMovie(String imdbId) throws Exception {
        fixture.lastResponse(mockMvc.perform(get("/api/movies/{imdbId}/rank-history", imdbId))
                .andReturn());
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

    @Then("the higher rank history contains movies")
    public void theHigherRankHistoryContainsMovies(DataTable dataTable) {
        fixture.assertHigherRankHistoryMatches(rankHistoryRows(dataTable));
    }

    @Then("the lower rank history contains movies")
    public void theLowerRankHistoryContainsMovies(DataTable dataTable) {
        fixture.assertLowerRankHistoryMatches(rankHistoryRows(dataTable));
    }

    private List<Map<String, String>> rankHistoryRows(DataTable dataTable) {
        return dataTable.asMaps();
    }
}
