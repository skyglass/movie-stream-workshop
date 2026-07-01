package com.ivanfranchin.moviesapi.bdd.movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanfranchin.moviesapi.bdd.movie.fixture.MovieCatalogFixture;
import com.ivanfranchin.moviesapi.movie.application.service.AddMovieToCatalogUseCase;
import com.ivanfranchin.moviesapi.movie.application.service.AddMovieToCatalogUseCase.AddMovieCommand;
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
        addMovieToCatalog.addMovie(new AddMovieCommand(imdbId, title, "N/A", "N/A", ""));
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
