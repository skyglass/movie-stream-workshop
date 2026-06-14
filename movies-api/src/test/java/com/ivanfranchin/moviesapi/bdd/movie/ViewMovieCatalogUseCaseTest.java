package com.ivanfranchin.moviesapi.bdd.movie;

import com.ivanfranchin.moviesapi.bdd.movie.fixture.MovieCatalogFixture;
import com.ivanfranchin.moviesapi.movie.application.service.ViewMovieCatalogUseCase;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class ViewMovieCatalogUseCaseTest {

    private final MovieCatalogFixture fixture;
    private final ViewMovieCatalogUseCase viewMovieCatalog;

    public ViewMovieCatalogUseCaseTest(MovieCatalogFixture fixture, ViewMovieCatalogUseCase viewMovieCatalog) {
        this.fixture = fixture;
        this.viewMovieCatalog = viewMovieCatalog;
    }

    @Given("the catalog discovery list contains {string} and {string}")
    public void theCatalogDiscoveryListContains(String firstTitle, String secondTitle) {
        fixture.saveMoviesWithTitles(firstTitle, secondTitle);
    }

    @When("the viewer requests the movie catalog")
    public void theViewerRequestsTheMovieCatalog() {
        fixture.movieList(viewMovieCatalog.viewCatalog());
    }

    @When("regular user {string} requests the personalized movie catalog")
    public void regularUserRequestsThePersonalizedMovieCatalog(String username) {
        fixture.movieList(viewMovieCatalog.viewCatalog(username));
    }

    @Then("the catalog discovery list shows {string} before {string}")
    public void theCatalogDiscoveryListShowsBefore(String firstTitle, String secondTitle) {
        fixture.assertMovieListOrdersTitleBefore(firstTitle, secondTitle);
    }

    @Then("the catalog discovery list contains {int} movies")
    public void theCatalogDiscoveryListContainsMovies(int count) {
        fixture.assertMovieListSizeIs(count);
    }

    @Then("catalog movie {string} is marked recommended")
    public void catalogMovieIsMarkedRecommended(String imdbId) {
        fixture.assertMovieListItemRecommendationIs(imdbId, true);
    }
}
