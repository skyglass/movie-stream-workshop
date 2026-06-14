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
