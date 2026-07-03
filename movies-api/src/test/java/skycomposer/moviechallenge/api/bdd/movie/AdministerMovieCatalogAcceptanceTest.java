package skycomposer.moviechallenge.api.bdd.movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.AdministerMovieCatalogUseCase;
import skycomposer.moviechallenge.api.movie.application.service.AdministerMovieCatalogUseCase.UpdateMovieCommand;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

public class AdministerMovieCatalogAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final AdministerMovieCatalogUseCase administerMovieCatalog;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public AdministerMovieCatalogAcceptanceTest(MovieCatalogFixture fixture,
                                             AdministerMovieCatalogUseCase administerMovieCatalog,
                                             MockMvc mockMvc,
                                             ObjectMapper objectMapper) {
        this.fixture = fixture;
        this.administerMovieCatalog = administerMovieCatalog;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @When("admin updates movie {string} title to {string}")
    public void adminUpdatesMovieTitleTo(String imdbId, String title) {
        administerMovieCatalog.updateMovie(new UpdateMovieCommand(imdbId, title, null, null, null, null, null, null, null));
    }

    @When("regular user {string} tries to delete movie {string}")
    public void regularUserTriesToDeleteMovie(String username, String imdbId) throws Exception {
        fixture.authenticateRegularUser(username);
        fixture.lastResponse(mockMvc.perform(delete("/api/movies/{imdbId}", imdbId)
                        .with(fixture.jwtForCurrentUser()))
                .andReturn());
    }

    @When("regular user {string} tries to update movie {string} title to {string}")
    public void regularUserTriesToUpdateMovieTitleTo(String username, String imdbId, String title) throws Exception {
        fixture.authenticateRegularUser(username);
        fixture.lastResponse(mockMvc.perform(put("/api/movies/{imdbId}", imdbId)
                        .with(fixture.jwtForCurrentUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", title))))
                .andReturn());
    }
}
