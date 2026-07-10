package skycomposer.moviechallenge.api.bdd.movie;

import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.ViewUsersFavoriteMoviesUseCase;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class ViewUsersFavoriteMoviesAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final ViewUsersFavoriteMoviesUseCase viewUsersFavoriteMovies;

    public ViewUsersFavoriteMoviesAcceptanceTest(MovieCatalogFixture fixture,
                                              ViewUsersFavoriteMoviesUseCase viewUsersFavoriteMovies) {
        this.fixture = fixture;
        this.viewUsersFavoriteMovies = viewUsersFavoriteMovies;
    }

    @When("regular user {string} requests users favorite movies")
    public void regularUserRequestsUsersFavoriteMovies(String username) {
        fixture.moviePage(viewUsersFavoriteMovies.viewUsersFavoriteMovies(username, fixture.firstMoviePage()));
    }

    @When("regular user {string} requests page {int} of users favorite movies with {int} movies per page")
    public void regularUserRequestsPageOfUsersFavoriteMoviesWithMoviesPerPage(String username, int page, int pageSize) {
        fixture.moviePage(viewUsersFavoriteMovies.viewUsersFavoriteMovies(username, fixture.moviePage(page, pageSize)));
    }

    @When("regular user {string} requests users favorite movies filtered by {string}")
    public void regularUserRequestsUsersFavoriteMoviesFilteredBy(String username, String filter) {
        fixture.moviePage(viewUsersFavoriteMovies.viewUsersFavoriteMovies(username, fixture.firstMoviePage(), filter));
    }

    @Then("users favorite movies show {string} before {string}")
    public void usersFavoriteMoviesShowBefore(String firstImdbId, String secondImdbId) {
        fixture.assertMovieListOrdersImdbIdBefore(firstImdbId, secondImdbId);
    }

    @Then("users favorite movies contain {int} movies")
    public void usersFavoriteMoviesContainMovies(int count) {
        fixture.assertMovieListSizeIs(count);
    }

    @Then("users favorite movies total count is {int}")
    public void usersFavoriteMoviesTotalCountIs(int count) {
        fixture.assertMovieListTotalCountIs(count);
    }

    @Then("users favorite movies contain movie {string}")
    public void usersFavoriteMoviesContainMovie(String imdbId) {
        fixture.assertMovieListContainsImdbId(imdbId);
    }

    @Then("users favorite movies do not contain movie {string}")
    public void usersFavoriteMoviesDoNotContainMovie(String imdbId) {
        fixture.assertMovieListDoesNotContainImdbId(imdbId);
    }
}
