package skycomposer.moviechallenge.api.bdd.movie;

import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.AddMovieCommentUseCase;
import skycomposer.moviechallenge.api.movie.application.service.AddMovieCommentUseCase.AddCommentCommand;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class AddMovieCommentAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final AddMovieCommentUseCase addMovieComment;

    public AddMovieCommentAcceptanceTest(MovieCatalogFixture fixture, AddMovieCommentUseCase addMovieComment) {
        this.fixture = fixture;
        this.addMovieComment = addMovieComment;
    }

    @When("user {string} comments {string} on movie {string}")
    public void userCommentsOnMovie(String username, String text, String imdbId) {
        addMovieComment.addComment(new AddCommentCommand(imdbId, username, text));
    }

    @Then("commented movie {string} has comment {string} by {string}")
    public void commentedMovieHasCommentBy(String imdbId, String text, String username) {
        fixture.assertMovieHasComment(imdbId, username, text);
    }

    @When("user {string} tries to comment blank text on movie {string}")
    public void userTriesToCommentBlankTextOnMovie(String username, String imdbId) {
        fixture.lastError(assertThrows(RuntimeException.class,
                () -> addMovieComment.addComment(new AddCommentCommand(imdbId, username, " "))));
    }

    @Then("the blank movie comment is rejected because text is required")
    public void theBlankMovieCommentIsRejectedBecauseTextIsRequired() {
        fixture.assertLastErrorIsIllegalArgumentException();
    }
}
