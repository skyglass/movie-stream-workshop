package com.ivanfranchin.moviesapi.bdd.movie;

import com.ivanfranchin.moviesapi.bdd.movie.fixture.MovieCatalogFixture;
import com.ivanfranchin.moviesapi.movie.application.service.ViewUsersFavoriteMoviesUseCase;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class ViewUsersFavoriteMoviesUseCaseTest {

    private final MovieCatalogFixture fixture;
    private final ViewUsersFavoriteMoviesUseCase viewUsersFavoriteMovies;

    public ViewUsersFavoriteMoviesUseCaseTest(MovieCatalogFixture fixture,
                                              ViewUsersFavoriteMoviesUseCase viewUsersFavoriteMovies) {
        this.fixture = fixture;
        this.viewUsersFavoriteMovies = viewUsersFavoriteMovies;
    }

    @When("regular user {string} requests users favorite movies")
    public void regularUserRequestsUsersFavoriteMovies(String username) {
        fixture.movieList(viewUsersFavoriteMovies.viewUsersFavoriteMovies(username));
    }

    @Then("users favorite movies show {string} before {string}")
    public void usersFavoriteMoviesShowBefore(String firstImdbId, String secondImdbId) {
        fixture.assertMovieListOrdersImdbIdBefore(firstImdbId, secondImdbId);
    }

    @Then("users favorite movies contain {int} movies")
    public void usersFavoriteMoviesContainMovies(int count) {
        fixture.assertMovieListSizeIs(count);
    }
}
