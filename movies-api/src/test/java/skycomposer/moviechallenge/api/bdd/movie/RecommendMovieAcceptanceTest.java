package skycomposer.moviechallenge.api.bdd.movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.RecommendMovieUseCase;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.model.MovieType;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class RecommendMovieAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final RecommendMovieUseCase recommendMovie;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public RecommendMovieAcceptanceTest(MovieCatalogFixture fixture,
                                     RecommendMovieUseCase recommendMovie,
                                     MockMvc mockMvc,
                                     ObjectMapper objectMapper) {
        this.fixture = fixture;
        this.recommendMovie = recommendMovie;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @When("regular user {string} recommends movie {string}")
    public void regularUserRecommendsMovie(String username, String imdbId) {
        fixture.selectedMovie(recommendMovie.recommendMovie(username, imdbId));
    }

    @When("regular user {string} recommends new movie {string} titled {string} with director {string}, writer {string}, year {string}, genre {string}, country {string}, and type {string}")
    public void regularUserRecommendsNewMovieWithMetadata(String username, String imdbId, String title, String director,
                                                          String writer, String year, String genre, String country,
                                                          String type)
            throws Exception {
        fixture.authenticateRegularUser(username);
        MvcResult result = mockMvc.perform(post("/api/movies/recommendation")
                        .with(fixture.jwtForCurrentUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "imdbId", imdbId,
                                "title", title,
                                "director", director,
                                "writer", writer,
                                "year", year,
                                "genre", genre,
                                "country", country,
                                "type", MovieType.fromValue(type).name()))))
                .andReturn();

        fixture.lastResponse(result);
        fixture.selectedMovie(objectMapper.readValue(result.getResponse().getContentAsString(), MovieDto.class));
    }

    @When("regular user {string} tries to recommend new movie {string} titled {string} through the movie API with type {string}")
    public void regularUserTriesToRecommendNewMovieWithType(String username, String imdbId, String title, String type)
            throws Exception {
        fixture.authenticateRegularUser(username);
        fixture.lastResponse(mockMvc.perform(post("/api/movies/recommendation")
                        .with(fixture.jwtForCurrentUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "imdbId", imdbId,
                                "title", title,
                                "director", "N/A",
                                "writer", "N/A",
                                "year", "N/A",
                                "type", type))))
                .andReturn());
    }

    @Then("movie {string} is recommended by {string}")
    public void movieIsRecommendedBy(String imdbId, String username) {
        fixture.assertMovieRecommendedBy(imdbId, username);
    }

    @Then("the recommendation response marks movie {string} as recommended")
    public void theRecommendationResponseMarksMovieAsRecommended(String imdbId) {
        fixture.assertSelectedMovieImdbIdIs(imdbId);
        fixture.assertSelectedMovieRecommendationIs(true);
    }

    @When("regular user {string} unrecommends movie {string}")
    public void regularUserUnrecommendsMovie(String username, String imdbId) {
        fixture.selectedMovie(recommendMovie.unrecommendMovie(username, imdbId));
    }

    @When("regular user {string} dislikes movie {string}")
    public void regularUserDislikesMovie(String username, String imdbId) {
        fixture.selectedMovie(recommendMovie.dislikeMovie(username, imdbId));
    }

    @Then("movie {string} is not recommended by {string}")
    public void movieIsNotRecommendedBy(String imdbId, String username) {
        fixture.assertMovieNotRecommendedBy(imdbId, username);
    }

    @Then("movie {string} is disliked by {string}")
    public void movieIsDislikedBy(String imdbId, String username) {
        fixture.assertMovieDislikedBy(imdbId, username);
    }

    @Then("movie {string} is not disliked by {string}")
    public void movieIsNotDislikedBy(String imdbId, String username) {
        fixture.assertMovieNotDislikedBy(imdbId, username);
    }

    @Then("the recommendation response marks movie {string} as not recommended")
    public void theRecommendationResponseMarksMovieAsNotRecommended(String imdbId) {
        fixture.assertSelectedMovieImdbIdIs(imdbId);
        fixture.assertSelectedMovieRecommendationIs(false);
    }

    @Then("the recommendation response marks movie {string} as disliked")
    public void theRecommendationResponseMarksMovieAsDisliked(String imdbId) {
        fixture.assertSelectedMovieImdbIdIs(imdbId);
        fixture.assertSelectedMovieRecommendationIs(false);
        fixture.assertSelectedMovieDislikedIs(true);
    }

    @Then("the recommendation response marks movie {string} as not disliked")
    public void theRecommendationResponseMarksMovieAsNotDisliked(String imdbId) {
        fixture.assertSelectedMovieImdbIdIs(imdbId);
        fixture.assertSelectedMovieDislikedIs(false);
    }

    @When("an anonymous caller tries to recommend movie {string}")
    public void anAnonymousCallerTriesToRecommendMovie(String imdbId) throws Exception {
        fixture.lastResponse(mockMvc.perform(post("/api/movies/{imdbId}/recommendation", imdbId))
                .andReturn());
    }
}
