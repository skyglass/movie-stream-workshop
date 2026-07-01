package com.ivanfranchin.moviesapi.bdd.movie;

import com.ivanfranchin.moviesapi.bdd.movie.fixture.MovieCatalogFixture;
import com.ivanfranchin.moviesapi.movie.application.service.ViewFavoriteMoviesUseCase;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class ViewFavoriteMoviesUseCaseTest {

    private final MovieCatalogFixture fixture;
    private final ViewFavoriteMoviesUseCase viewFavoriteMovies;

    public ViewFavoriteMoviesUseCaseTest(MovieCatalogFixture fixture, ViewFavoriteMoviesUseCase viewFavoriteMovies) {
        this.fixture = fixture;
        this.viewFavoriteMovies = viewFavoriteMovies;
    }

    @When("regular user {string} requests favorite movies")
    public void regularUserRequestsFavoriteMovies(String username) {
        fixture.movieList(viewFavoriteMovies.viewFavoriteMovies(username));
    }

    @Then("favorite movies show {string} before {string}")
    public void favoriteMoviesShowBefore(String firstImdbId, String secondImdbId) {
        fixture.assertMovieListOrdersImdbIdBefore(firstImdbId, secondImdbId);
    }

    @Then("favorite movies contain {int} movies")
    public void favoriteMoviesContainMovies(int count) {
        fixture.assertMovieListSizeIs(count);
    }
}
