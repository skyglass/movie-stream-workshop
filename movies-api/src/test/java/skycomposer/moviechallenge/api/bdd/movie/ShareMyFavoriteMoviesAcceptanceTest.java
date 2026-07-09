package skycomposer.moviechallenge.api.bdd.movie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class ShareMyFavoriteMoviesAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private String lastSharePath;

    public ShareMyFavoriteMoviesAcceptanceTest(MovieCatalogFixture fixture, MockMvc mockMvc, ObjectMapper objectMapper) {
        this.fixture = fixture;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @When("regular user {string} shares own favorite movies")
    public void regularUserSharesOwnFavoriteMovies(String username) throws Exception {
        fixture.authenticateRegularUser(username);
        var result = mockMvc.perform(post("/api/favorite-movies/share")
                        .with(fixture.jwtForCurrentUser()))
                .andReturn();
        fixture.lastResponse(result);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        lastSharePath = body.get("sharePath").asText();
    }

    @When("regular user {string} makes own favorite movies private")
    public void regularUserMakesOwnFavoriteMoviesPrivate(String username) throws Exception {
        fixture.authenticateRegularUser(username);
        var result = mockMvc.perform(delete("/api/favorite-movies/share")
                        .with(fixture.jwtForCurrentUser()))
                .andReturn();
        fixture.lastResponse(result);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        lastSharePath = body.get("sharePath").asText();
    }

    @When("anonymous viewer requests shared favorite movies for encoded username {string}")
    public void anonymousViewerRequestsSharedFavoriteMoviesForEncodedUsername(String encodedUsername) throws Exception {
        var result = mockMvc.perform(get(URI.create("/api/my-favorite-movies/" + encodedUsername)))
                .andReturn();
        fixture.lastResponse(result);
        if (result.getResponse().getStatus() < 400) {
            fixture.moviePage(objectMapper.readValue(result.getResponse().getContentAsString(), MoviePageDto.class));
        }
    }

    @Then("favorite movies sharing for {string} is public")
    public void favoriteMoviesSharingForIsPublic(String username) {
        fixture.assertMyFavoriteMoviesPublic(username, true);
    }

    @Then("favorite movies sharing for {string} is private")
    public void favoriteMoviesSharingForIsPrivate(String username) {
        fixture.assertMyFavoriteMoviesPublic(username, false);
    }

    @Then("my favorite movies share path is {string}")
    public void myFavoriteMoviesSharePathIs(String sharePath) {
        assertEquals(sharePath, lastSharePath);
    }
}
