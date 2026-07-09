package skycomposer.moviechallenge.api.bdd.movie.fixture;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import skycomposer.moviechallenge.api.movie.model.MovieType;

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

    @Given("movie {string} exists with title {string}, director {string}, writer {string}, year {string}, genre {string}, country {string}, and type {string}")
    public void movieExistsWithMetadata(String imdbId, String title, String director, String writer, String year,
                                        String genre, String country, String type) {
        fixture.saveMovie(imdbId, title, director, writer, year, genre, country, MovieType.fromValue(type));
    }

    @Given("the movie catalog contains {int} titled movies")
    public void theMovieCatalogContainsTitledMovies(int count) {
        fixture.saveNumberedMovies(count);
    }

    @Given("movie {string} is already recommended by {string}")
    public void movieIsAlreadyRecommendedBy(String imdbId, String username) {
        fixture.recommendMovie(imdbId, username);
    }

    @Given("all numbered movies are already recommended by {string} with {int} direct comparisons")
    public void allNumberedMoviesAreAlreadyRecommendedByWithDirectComparisons(String username, int directComparisons) {
        fixture.recommendAndRankNumberedMovies(username, directComparisons);
    }

    @Given("movie {string} is already disliked by {string}")
    public void movieIsAlreadyDislikedBy(String imdbId, String username) {
        fixture.dislikeMovie(imdbId, username);
    }

    @Given("movie {string} has already won {int} direct challenge comparison(s) for {string}")
    public void movieHasAlreadyWonDirectChallengeComparisonsFor(String imdbId, int count, String username) {
        fixture.recordTransitiveWins(imdbId, username, count);
    }

    @Then("movie {string} exists in the catalog with title {string}")
    public void movieExistsInTheCatalogWithTitle(String imdbId, String title) {
        fixture.assertMovieTitleIs(imdbId, title);
    }

    @Then("movie {string} exists in the catalog with director {string}, writer {string}, year {string}, genre {string}, country {string}, and type {string}")
    public void movieExistsInTheCatalogWithMetadata(String imdbId, String director, String writer, String year,
                                                    String genre, String country, String type) {
        fixture.assertMovieMetadataIs(imdbId, director, writer, year, genre, country, MovieType.fromValue(type));
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
