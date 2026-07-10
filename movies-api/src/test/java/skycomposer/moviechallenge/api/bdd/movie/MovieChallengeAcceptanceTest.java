package skycomposer.moviechallenge.api.bdd.movie;

import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.MovieChallengeUseCase;
import skycomposer.moviechallenge.api.movie.dto.SelectMovieChallengeRequest;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    @Given("movie {string} has rank {int} and {int} direct comparison(s) for {string}")
    public void movieHasRankAndDirectComparisonsFor(String imdbId, int rankPosition, int directComparisons, String username) {
        fixture.setMovieRank(imdbId, username, rankPosition, directComparisons);
    }

    @Given("movie {string} has rank {int}, {int} direct comparison(s), mu {string}, and sigma {string} for {string}")
    public void movieHasRankDirectComparisonsMuAndSigmaFor(String imdbId,
                                                           int rankPosition,
                                                           int directComparisons,
                                                           String mu,
                                                           String sigma,
                                                           String username) {
        fixture.setMovieRank(
                imdbId,
                username,
                rankPosition,
                directComparisons,
                new BigDecimal(mu),
                new BigDecimal(sigma));
    }

    @Given("user {string} has already ranked movies {string} from best to worst except pair {string} and {string}")
    public void userHasAlreadyRankedMoviesFromBestToWorstExceptPair(String username,
                                                                    String orderedMovieIds,
                                                                    String firstExcludedMovieId,
                                                                    String secondExcludedMovieId) {
        fixture.recordOrderedRankingExceptPair(username, orderedMovieIds, firstExcludedMovieId, secondExcludedMovieId);
    }

    @Given("user {string} has already ranked movies {string} from best to worst")
    public void userHasAlreadyRankedMoviesFromBestToWorst(String username, String orderedMovieIds) {
        fixture.recordOrderedRanking(username, orderedMovieIds);
    }

    @When("regular user {string} requests the next movie challenge")
    public void regularUserRequestsTheNextMovieChallenge(String username) {
        fixture.selectedMovieChallenge(movieChallenge.nextChallenge(username).orElse(null));
    }

    @When("regular user {string} requests suggested movie challenges page {int} with size {int}")
    public void regularUserRequestsSuggestedMovieChallengesPageWithSize(String username, int page, int pageSize) {
        fixture.suggestedMovieChallengePage(movieChallenge.suggestedChallenges(username, fixture.moviePage(page, pageSize)));
    }

    @When("regular user {string} selects movie {string} from movie challenge pair {string} and {string}")
    public void regularUserSelectsMovieFromMovieChallengePair(String username,
                                                              String selectedMovieId,
                                                              String firstMovieId,
                                                              String secondMovieId) {
        movieChallenge.selectMovie(username, firstMovieId, secondMovieId, selectedMovieId);
    }

    @When("regular user {string} submits movie challenge selections")
    public void regularUserSubmitsMovieChallengeSelections(String username, DataTable dataTable) {
        List<SelectMovieChallengeRequest> selections = dataTable.asMaps().stream()
                .map(this::selectMovieChallengeRequest)
                .toList();
        movieChallenge.selectMovies(username, selections);
    }

    @Then("the movie challenge contains movies {string} and {string}")
    public void theMovieChallengeContainsMoviesAnd(String firstMovieId, String secondMovieId) {
        fixture.assertSelectedMovieChallengeContains(firstMovieId, secondMovieId);
    }

    @Then("the movie challenge is movie {string} against movie {string}")
    public void theMovieChallengeIsMovieAgainstMovie(String firstMovieId, String secondMovieId) {
        fixture.assertSelectedMovieChallengeIs(firstMovieId, secondMovieId);
    }

    @Then("the movie challenge does not contain movies {string} and {string}")
    public void theMovieChallengeDoesNotContainMoviesAnd(String firstMovieId, String secondMovieId) {
        fixture.assertSelectedMovieChallengeDoesNotContain(firstMovieId, secondMovieId);
    }

    @Then("no movie challenge is available")
    public void noMovieChallengeIsAvailable() {
        fixture.assertNoMovieChallengeAvailable();
    }

    @Then("the suggested movie challenge list contains {int} challenge(s)")
    public void theSuggestedMovieChallengeListContainsChallenges(int count) {
        fixture.assertSuggestedMovieChallengeListSizeIs(count);
    }

    @Then("the suggested movie challenge total count is {int}")
    public void theSuggestedMovieChallengeTotalCountIs(int count) {
        fixture.assertSuggestedMovieChallengeTotalCountIs(count);
    }

    @Then("suggested movie challenge {int} is movie {string} against movie {string}")
    public void suggestedMovieChallengeIsMovieAgainstMovie(int number, String firstMovieId, String secondMovieId) {
        fixture.assertSuggestedMovieChallengeIs(number, firstMovieId, secondMovieId);
    }

    @Then("suggested movie challenge {int} movie {string} has win chance {int} percent and rank {int}")
    public void suggestedMovieChallengeMovieHasWinChanceAndRank(int number,
                                                                String movieId,
                                                                int winProbabilityPercent,
                                                                int rankPosition) {
        fixture.assertSuggestedMovieChallengeMovieProbabilityAndRank(
                number,
                movieId,
                winProbabilityPercent,
                rankPosition);
    }

    @Then("movie {string} has {int} direct win(s) by {string}")
    public void movieHasDirectWinsBy(String imdbId, int count, String username) {
        fixture.assertMovieVoteCount(imdbId, username, count);
    }

    @Then("movie {string} has {int} direct comparison(s) for {string}")
    public void movieHasDirectComparisonsFor(String imdbId, int count, String username) {
        fixture.assertMovieChallengeCount(imdbId, username, count);
    }

    @Then("movie {string} has rank {int} and rating {string} for {string}")
    public void movieHasRankAndRatingFor(String imdbId, int rankPosition, String rating, String username) {
        fixture.assertMovieRankAndRating(imdbId, username, rankPosition, rating);
    }

    @Then("movie pair {string} and {string} is completed for {string}")
    public void moviePairIsCompletedFor(String firstMovieId, String secondMovieId, String username) {
        fixture.assertMoviePairChallengeExists(username, firstMovieId, secondMovieId);
    }

    @Then("movie {string} is recorded as winner over {string} for {string}")
    public void movieIsRecordedAsWinnerOverFor(String winnerId, String loserId, String username) {
        fixture.assertDirectWinnerLoserExists(username, winnerId, loserId);
    }

    @Then("movie {string} is not recorded as direct winner over {string} for {string}")
    public void movieIsNotRecordedAsDirectWinnerOverFor(String winnerId, String loserId, String username) {
        fixture.assertDirectWinnerLoserDoesNotExist(username, winnerId, loserId);
    }

    @Then("movie {string} is ranked over {string} for {string}")
    public void movieIsRankedOverFor(String winnerId, String loserId, String username) {
        fixture.assertTransitiveWinnerLoserExists(username, winnerId, loserId);
    }

    private SelectMovieChallengeRequest selectMovieChallengeRequest(Map<String, String> row) {
        return new SelectMovieChallengeRequest(
                row.get("movie1Id"),
                row.get("movie2Id"),
                row.get("selectedMovieId"));
    }
}
