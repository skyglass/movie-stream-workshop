package skycomposer.moviechallenge.api.bdd.movie;

import skycomposer.moviechallenge.api.bdd.fixture.RestApiFixture;
import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.application.service.AddMovieCommentUseCase;
import skycomposer.moviechallenge.api.movie.application.service.AddMovieCommentUseCase.AddCommentCommand;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class AddMovieCommentAcceptanceTest {

    private final MovieCatalogFixture fixture;
    private final AddMovieCommentUseCase addMovieComment;
    private final RestApiFixture restApi;
    private final MockMvc mockMvc;

    public AddMovieCommentAcceptanceTest(MovieCatalogFixture fixture, AddMovieCommentUseCase addMovieComment,
                                         RestApiFixture restApi, MockMvc mockMvc) {
        this.fixture = fixture;
        this.addMovieComment = addMovieComment;
        this.restApi = restApi;
        this.mockMvc = mockMvc;
    }

    @When("admin user {string} comments {string} on movie {string} through the movie API")
    public void adminCommentsThroughApi(String username, String text, String imdbId) throws Exception {
        commentThroughApi(username, "MOVIES_ADMIN", text, imdbId);
    }

    @When("regular user {string} tries to comment {string} on movie {string} through the movie API")
    public void regularUserCommentsThroughApi(String username, String text, String imdbId) throws Exception {
        commentThroughApi(username, "MOVIES_USER", text, imdbId);
    }

    private void commentThroughApi(String username, String role, String text, String imdbId) throws Exception {
        restApi.record(mockMvc.perform(post("/api/movies/{imdbId}/comments", imdbId)
                        .with(restApi.jwt(username, role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\"}"))
                .andReturn());
    }

    @Then("the comment API response status is {int}")
    public void commentApiResponseStatusIs(int status) {
        restApi.assertStatus(status);
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
