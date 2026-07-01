package com.ivanfranchin.moviesapi.bdd.movie;

import com.ivanfranchin.moviesapi.bdd.movie.fixture.MovieCatalogFixture;
import com.ivanfranchin.moviesapi.movie.application.service.RecommendMovieUseCase;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class RecommendMovieAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final RecommendMovieUseCase recommendMovie;
    private final MockMvc mockMvc;

    public RecommendMovieAcceptanceTest(MovieCatalogFixture fixture,
                                     RecommendMovieUseCase recommendMovie,
                                     MockMvc mockMvc) {
        this.fixture = fixture;
        this.recommendMovie = recommendMovie;
        this.mockMvc = mockMvc;
    }

    @When("regular user {string} recommends movie {string}")
    public void regularUserRecommendsMovie(String username, String imdbId) {
        fixture.selectedMovie(recommendMovie.recommendMovie(username, imdbId));
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

    @Then("movie {string} is not recommended by {string}")
    public void movieIsNotRecommendedBy(String imdbId, String username) {
        fixture.assertMovieNotRecommendedBy(imdbId, username);
    }

    @Then("the recommendation response marks movie {string} as not recommended")
    public void theRecommendationResponseMarksMovieAsNotRecommended(String imdbId) {
        fixture.assertSelectedMovieImdbIdIs(imdbId);
        fixture.assertSelectedMovieRecommendationIs(false);
    }

    @When("an anonymous caller tries to recommend movie {string}")
    public void anAnonymousCallerTriesToRecommendMovie(String imdbId) throws Exception {
        fixture.lastResponse(mockMvc.perform(post("/api/movies/{imdbId}/recommendation", imdbId))
                .andReturn());
    }
}
