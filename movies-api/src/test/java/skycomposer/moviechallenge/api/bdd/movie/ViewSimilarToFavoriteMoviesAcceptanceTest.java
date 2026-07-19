package skycomposer.moviechallenge.api.bdd.movie;

import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.ViewCategorySimilarMoviesUseCase;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;

public class ViewSimilarToFavoriteMoviesAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final ViewCategorySimilarMoviesUseCase viewCategorySimilarMovies;

    public ViewSimilarToFavoriteMoviesAcceptanceTest(MovieCatalogFixture fixture,
                                                     ViewCategorySimilarMoviesUseCase viewCategorySimilarMovies) {
        this.fixture = fixture;
        this.viewCategorySimilarMovies = viewCategorySimilarMovies;
    }

    @When("regular user {string} requests similar to favorite movies")
    public void regularUserRequestsSimilarToFavoriteMovies(String username) {
        fixture.moviePage(viewCategorySimilarMovies.viewSimilarToFavorites(username, fixture.firstMoviePage(), null, null, List.of()));
    }

    @When("regular user {string} requests similar to favorite movies filtered by {string}")
    public void regularUserRequestsSimilarToFavoriteMoviesFilteredBy(String username, String filter) {
        fixture.moviePage(viewCategorySimilarMovies.viewSimilarToFavorites(username, fixture.firstMoviePage(), filter, null, List.of()));
    }

    @Then("similar to favorite movies show {string} before {string}")
    public void similarToFavoriteMoviesShowBefore(String firstImdbId, String secondImdbId) {
        fixture.assertMovieListOrdersImdbIdBefore(firstImdbId, secondImdbId);
    }

    @Then("similar to favorite movies contain movie {string}")
    public void similarToFavoriteMoviesContainMovie(String imdbId) {
        fixture.assertMovieListContainsImdbId(imdbId);
    }

    @Then("similar to favorite movies do not contain {string}")
    public void similarToFavoriteMoviesDoNotContain(String imdbId) {
        fixture.assertMovieListDoesNotContainImdbId(imdbId);
    }

    @Then("similar to favorite movies contain {int} movies")
    public void similarToFavoriteMoviesContainMovies(int count) {
        fixture.assertMovieListSizeIs(count);
    }
}
