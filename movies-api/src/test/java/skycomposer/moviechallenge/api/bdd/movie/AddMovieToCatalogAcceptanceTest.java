package skycomposer.moviechallenge.api.bdd.movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.AddMovieToCatalogUseCase;
import skycomposer.moviechallenge.api.movie.application.service.AddMovieToCatalogUseCase.AddMovieCommand;
import skycomposer.moviechallenge.api.movie.model.MovieType;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class AddMovieToCatalogAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final AddMovieToCatalogUseCase addMovieToCatalog;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public AddMovieToCatalogAcceptanceTest(MovieCatalogFixture fixture,
                                        AddMovieToCatalogUseCase addMovieToCatalog,
                                        MockMvc mockMvc,
                                        ObjectMapper objectMapper) {
        this.fixture = fixture;
        this.addMovieToCatalog = addMovieToCatalog;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @When("contributor {string} adds movie {string} titled {string}")
    public void contributorAddsMovieTitled(String username, String imdbId, String title) {
        addMovieToCatalog.addMovie(new AddMovieCommand(imdbId, title, "N/A", "N/A", "N/A", "", null, null, MovieType.MOVIE));
    }

    @When("contributor {string} adds movie {string} titled {string} with director {string}, writer {string}, year {string}, genre {string}, country {string}, and type {string}")
    public void contributorAddsMovieWithMetadata(String username, String imdbId, String title, String director,
                                                 String writer, String year, String genre, String country, String type) {
        addMovieToCatalog.addMovie(new AddMovieCommand(imdbId, title, director, writer, year, "", genre, country,
                MovieType.fromValue(type)));
    }

    @When("an anonymous caller tries to add movie {string}")
    public void anonymousCallerTriesToAddMovie(String imdbId) throws Exception {
        fixture.lastResponse(mockMvc.perform(post("/api/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "imdbId", imdbId,
                                "title", "Anonymous Movie",
                                "director", "N/A",
                                "year", "N/A"))))
                .andReturn());
    }
}
