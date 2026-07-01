package com.ivanfranchin.moviesapi.bdd.movie;

import com.ivanfranchin.moviesapi.bdd.movie.fixture.MovieCatalogFixture;
import com.ivanfranchin.moviesapi.movie.application.service.ViewUsersRecommendedMoviesUseCase;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class ViewUsersRecommendedMoviesAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final ViewUsersRecommendedMoviesUseCase viewUsersRecommendedMovies;

    public ViewUsersRecommendedMoviesAcceptanceTest(MovieCatalogFixture fixture,
                                                 ViewUsersRecommendedMoviesUseCase viewUsersRecommendedMovies) {
        this.fixture = fixture;
        this.viewUsersRecommendedMovies = viewUsersRecommendedMovies;
    }

    @Given("user {string} has already chosen {string} over {string} in movie challenges")
    public void userHasAlreadyChosenOverInMovieChallenges(String username, String winnerId, String loserId) {
        fixture.recordWinnerLoser(username, winnerId, loserId);
    }

    @When("regular user {string} requests users recommended movies")
    public void regularUserRequestsUsersRecommendedMovies(String username) {
        fixture.movieList(viewUsersRecommendedMovies.viewUsersRecommendedMovies(username));
    }

    @Then("users recommended movies show {string} before {string}")
    public void usersRecommendedMoviesShowBefore(String firstImdbId, String secondImdbId) {
        fixture.assertMovieListOrdersImdbIdBefore(firstImdbId, secondImdbId);
    }

    @Then("users recommended movies do not contain {string}")
    public void usersRecommendedMoviesDoNotContain(String imdbId) {
        fixture.assertMovieListDoesNotContainImdbId(imdbId);
    }

    @Then("users recommended movies contain {int} movies")
    public void usersRecommendedMoviesContainMovies(int count) {
        fixture.assertMovieListSizeIs(count);
    }
}
