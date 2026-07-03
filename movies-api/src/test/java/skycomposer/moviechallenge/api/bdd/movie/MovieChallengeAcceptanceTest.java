package skycomposer.moviechallenge.api.bdd.movie;

import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.MovieChallengeUseCase;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class MovieChallengeAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final MovieChallengeUseCase movieChallenge;

    public MovieChallengeAcceptanceTest(MovieCatalogFixture fixture, MovieChallengeUseCase movieChallenge) {
        this.fixture = fixture;
        this.movieChallenge = movieChallenge;
    }

    @Given("movie {string} has already participated {int} time(s) in challenges for {string}")
    public void movieHasAlreadyParticipatedInChallengesFor(String imdbId, int count, String username) {
        fixture.incrementChallengeCount(imdbId, username, count);
    }

    @Given("movie pair {string} and {string} is already completed for {string}")
    public void moviePairIsAlreadyCompletedFor(String firstMovieId, String secondMovieId, String username) {
        fixture.completeMoviePairChallenge(username, firstMovieId, secondMovieId);
    }

    @Given("movie {string} has already beaten movie {string} for {string}")
    public void movieHasAlreadyBeatenMovieFor(String winnerId, String loserId, String username) {
        fixture.recordWinnerLoser(username, winnerId, loserId);
    }

    @When("regular user {string} requests the next movie challenge")
    public void regularUserRequestsTheNextMovieChallenge(String username) {
        fixture.selectedMovieChallenge(movieChallenge.nextChallenge(username).orElse(null));
    }

    @When("regular user {string} selects movie {string} from movie challenge pair {string} and {string}")
    public void regularUserSelectsMovieFromMovieChallengePair(String username,
                                                              String selectedMovieId,
                                                              String firstMovieId,
                                                              String secondMovieId) {
        movieChallenge.selectMovie(username, firstMovieId, secondMovieId, selectedMovieId);
    }

    @Then("the movie challenge contains movies {string} and {string}")
    public void theMovieChallengeContainsMoviesAnd(String firstMovieId, String secondMovieId) {
        fixture.assertSelectedMovieChallengeContains(firstMovieId, secondMovieId);
    }

    @Then("the movie challenge does not contain movies {string} and {string}")
    public void theMovieChallengeDoesNotContainMoviesAnd(String firstMovieId, String secondMovieId) {
        fixture.assertSelectedMovieChallengeDoesNotContain(firstMovieId, secondMovieId);
    }

    @Then("no movie challenge is available")
    public void noMovieChallengeIsAvailable() {
        fixture.assertNoMovieChallengeAvailable();
    }

    @Then("movie {string} has {int} transitive win(s) by {string}")
    public void movieHasTransitiveWinsBy(String imdbId, int count, String username) {
        fixture.assertMovieVoteCount(imdbId, username, count);
    }

    @Then("movie {string} has participated {int} time(s) in challenges for {string}")
    public void movieHasParticipatedInChallengesFor(String imdbId, int count, String username) {
        fixture.assertMovieChallengeCount(imdbId, username, count);
    }

    @Then("movie pair {string} and {string} is completed for {string}")
    public void moviePairIsCompletedFor(String firstMovieId, String secondMovieId, String username) {
        fixture.assertMoviePairChallengeExists(username, firstMovieId, secondMovieId);
    }

    @Then("movie {string} is recorded as winner over {string} for {string}")
    public void movieIsRecordedAsWinnerOverFor(String winnerId, String loserId, String username) {
        fixture.assertDirectWinnerLoserExists(username, winnerId, loserId);
    }

    @Then("movie {string} is transitively ranked over {string} for {string}")
    public void movieIsTransitivelyRankedOverFor(String winnerId, String loserId, String username) {
        fixture.assertTransitiveWinnerLoserExists(username, winnerId, loserId);
    }
}
