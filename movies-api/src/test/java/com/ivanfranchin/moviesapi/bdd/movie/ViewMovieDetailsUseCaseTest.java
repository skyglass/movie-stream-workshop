package com.ivanfranchin.moviesapi.bdd.movie;

import com.ivanfranchin.moviesapi.bdd.movie.fixture.MovieCatalogFixture;
import com.ivanfranchin.moviesapi.movie.application.service.ViewMovieDetailsUseCase;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class ViewMovieDetailsUseCaseTest {

    private final MovieCatalogFixture fixture;
    private final ViewMovieDetailsUseCase viewMovieDetails;

    public ViewMovieDetailsUseCaseTest(MovieCatalogFixture fixture, ViewMovieDetailsUseCase viewMovieDetails) {
        this.fixture = fixture;
        this.viewMovieDetails = viewMovieDetails;
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

    @Then("the first viewed movie comment is {string}")
    public void theFirstViewedMovieCommentIs(String text) {
        fixture.assertFirstSelectedMovieCommentTextIs(text);
    }

    @Then("the viewed movie is marked recommended")
    public void theViewedMovieIsMarkedRecommended() {
        fixture.assertSelectedMovieRecommendationIs(true);
    }
}
