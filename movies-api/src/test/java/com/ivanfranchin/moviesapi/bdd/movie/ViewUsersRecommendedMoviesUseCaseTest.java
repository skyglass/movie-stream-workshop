package com.ivanfranchin.moviesapi.bdd.movie;

import com.ivanfranchin.moviesapi.bdd.movie.fixture.MovieCatalogFixture;
import com.ivanfranchin.moviesapi.movie.application.service.ViewUsersRecommendedMoviesUseCase;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class ViewUsersRecommendedMoviesUseCaseTest {

    private final MovieCatalogFixture fixture;
    private final ViewUsersRecommendedMoviesUseCase viewUsersRecommendedMovies;

    public ViewUsersRecommendedMoviesUseCaseTest(MovieCatalogFixture fixture,
                                                 ViewUsersRecommendedMoviesUseCase viewUsersRecommendedMovies) {
        this.fixture = fixture;
        this.viewUsersRecommendedMovies = viewUsersRecommendedMovies;
    }

    @Given("user {string} has completed movie pair {string} and {string} with movie1_wins {word}")
    public void userHasCompletedMoviePairWithMovie1Wins(String username,
                                                        String firstMovieId,
                                                        String secondMovieId,
                                                        String movie1Wins) {
        fixture.recordMoviePairChallenge(username, firstMovieId, secondMovieId, Boolean.parseBoolean(movie1Wins));
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
