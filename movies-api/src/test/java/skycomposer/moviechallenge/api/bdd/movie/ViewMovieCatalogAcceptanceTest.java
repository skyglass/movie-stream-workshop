package skycomposer.moviechallenge.api.bdd.movie;

import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.ViewMovieCatalogUseCase;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class ViewMovieCatalogAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final ViewMovieCatalogUseCase viewMovieCatalog;

    public ViewMovieCatalogAcceptanceTest(MovieCatalogFixture fixture, ViewMovieCatalogUseCase viewMovieCatalog) {
        this.fixture = fixture;
        this.viewMovieCatalog = viewMovieCatalog;
    }

    @Given("the catalog discovery list contains {string} and {string}")
    public void theCatalogDiscoveryListContains(String firstTitle, String secondTitle) {
        fixture.saveMoviesWithTitles(firstTitle, secondTitle);
    }

    @When("the viewer requests the movie catalog")
    public void theViewerRequestsTheMovieCatalog() {
        fixture.moviePage(viewMovieCatalog.viewCatalog(fixture.firstMoviePage()));
    }

    @When("regular user {string} requests the personalized movie catalog")
    public void regularUserRequestsThePersonalizedMovieCatalog(String username) {
        fixture.moviePage(viewMovieCatalog.viewCatalog(username, fixture.firstMoviePage()));
    }

    @When("the viewer requests page {int} of the movie catalog with {int} movies per page")
    public void theViewerRequestsPageOfTheMovieCatalogWithMoviesPerPage(int page, int pageSize) {
        fixture.moviePage(viewMovieCatalog.viewCatalog(fixture.moviePage(page, pageSize)));
    }

    @Then("the catalog discovery list shows {string} before {string}")
    public void theCatalogDiscoveryListShowsBefore(String firstTitle, String secondTitle) {
        fixture.assertMovieListOrdersTitleBefore(firstTitle, secondTitle);
    }

    @Then("the catalog discovery list contains {int} movies")
    public void theCatalogDiscoveryListContainsMovies(int count) {
        fixture.assertMovieListSizeIs(count);
    }

    @Then("the catalog discovery list total count is {int}")
    public void theCatalogDiscoveryListTotalCountIs(int count) {
        fixture.assertMovieListTotalCountIs(count);
    }

    @Then("catalog movie {string} is marked recommended")
    public void catalogMovieIsMarkedRecommended(String imdbId) {
        fixture.assertMovieListItemRecommendationIs(imdbId, true);
    }

    @Then("catalog movie {string} is marked disliked")
    public void catalogMovieIsMarkedDisliked(String imdbId) {
        fixture.assertMovieListItemDislikedIs(imdbId, true);
    }
}
