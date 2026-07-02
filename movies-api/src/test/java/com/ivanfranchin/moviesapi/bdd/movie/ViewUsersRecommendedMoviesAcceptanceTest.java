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
        fixture.moviePage(viewUsersRecommendedMovies.viewUsersRecommendedMovies(username, fixture.firstMoviePage()));
    }

    @When("regular user {string} requests page {int} of users recommended movies with {int} movies per page")
    public void regularUserRequestsPageOfUsersRecommendedMoviesWithMoviesPerPage(String username, int page, int pageSize) {
        fixture.moviePage(viewUsersRecommendedMovies.viewUsersRecommendedMovies(username, fixture.moviePage(page, pageSize)));
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

    @Then("users recommended movies total count is {int}")
    public void usersRecommendedMoviesTotalCountIs(int count) {
        fixture.assertMovieListTotalCountIs(count);
    }

    @Then("users recommended movies contain movie {string}")
    public void usersRecommendedMoviesContainMovie(String imdbId) {
        fixture.assertMovieListContainsImdbId(imdbId);
    }
}
