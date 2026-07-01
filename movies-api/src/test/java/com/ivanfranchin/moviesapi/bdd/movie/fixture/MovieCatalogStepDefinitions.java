package com.ivanfranchin.moviesapi.bdd.movie.fixture;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

public class MovieCatalogStepDefinitions {

    private final MovieCatalogFixture fixture;

    public MovieCatalogStepDefinitions(MovieCatalogFixture fixture) {
        this.fixture = fixture;
    }

    @Given("the movie catalog is empty")
    public void theMovieCatalogIsEmpty() {
        fixture.clearMovies();
    }

    @Given("movie {string} exists with title {string}")
    public void movieExistsWithTitle(String imdbId, String title) {
        fixture.saveMovie(imdbId, title);
    }

    @Given("movie {string} is already recommended by {string}")
    public void movieIsAlreadyRecommendedBy(String imdbId, String username) {
        fixture.recommendMovie(imdbId, username);
    }

    @Given("movie {string} has already won {int} challenge comparison(s) for {string}")
    public void movieHasAlreadyWonChallengeComparisonsFor(String imdbId, int count, String username) {
        fixture.recordTransitiveWins(imdbId, username, count);
    }

    @Then("movie {string} exists in the catalog with title {string}")
    public void movieExistsInTheCatalogWithTitle(String imdbId, String title) {
        fixture.assertMovieTitleIs(imdbId, title);
    }

    @Then("the movie API response status is {int}")
    public void theMovieApiResponseStatusIs(int status) {
        fixture.assertLastResponseStatus(status);
    }

    @Then("the movie API response status is 401 or 403")
    public void theMovieApiResponseStatusIsUnauthorizedOrForbidden() {
        fixture.assertLastResponseStatusUnauthorizedOrForbidden();
    }
}
