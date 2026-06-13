package com.ivanfranchin.moviesapi.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanfranchin.moviesapi.movie.application.AddMovieCommentUseCase;
import com.ivanfranchin.moviesapi.movie.application.AddMovieCommentUseCase.AddCommentCommand;
import com.ivanfranchin.moviesapi.movie.application.AddMovieToCatalogUseCase;
import com.ivanfranchin.moviesapi.movie.application.AddMovieToCatalogUseCase.AddMovieCommand;
import com.ivanfranchin.moviesapi.movie.application.AdministerMovieCatalogUseCase;
import com.ivanfranchin.moviesapi.movie.application.AdministerMovieCatalogUseCase.UpdateMovieCommand;
import com.ivanfranchin.moviesapi.movie.application.ViewMovieCatalogUseCase;
import com.ivanfranchin.moviesapi.movie.application.ViewMovieDetailsUseCase;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class MovieStreamStepDefinitions {

    private final MovieStreamCucumberFixture fixture;
    private final ViewMovieCatalogUseCase viewMovieCatalog;
    private final ViewMovieDetailsUseCase viewMovieDetails;
    private final AddMovieToCatalogUseCase addMovieToCatalog;
    private final AddMovieCommentUseCase addMovieComment;
    private final AdministerMovieCatalogUseCase administerMovieCatalog;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    private List<MovieDto> catalog;
    private MovieDto movieDetails;
    private RuntimeException commandError;
    private MvcResult lastResponse;
    private String currentUsername;
    private String currentRole;

    public MovieStreamStepDefinitions(MovieStreamCucumberFixture fixture,
                                      ViewMovieCatalogUseCase viewMovieCatalog,
                                      ViewMovieDetailsUseCase viewMovieDetails,
                                      AddMovieToCatalogUseCase addMovieToCatalog,
                                      AddMovieCommentUseCase addMovieComment,
                                      AdministerMovieCatalogUseCase administerMovieCatalog,
                                      MockMvc mockMvc,
                                      ObjectMapper objectMapper) {
        this.fixture = fixture;
        this.viewMovieCatalog = viewMovieCatalog;
        this.viewMovieDetails = viewMovieDetails;
        this.addMovieToCatalog = addMovieToCatalog;
        this.addMovieComment = addMovieComment;
        this.administerMovieCatalog = administerMovieCatalog;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Given("the movie catalog contains {string} and {string}")
    public void theMovieCatalogContains(String firstTitle, String secondTitle) {
        fixture.clearMovies();
        fixture.saveMovie("tt-catalog-1", firstTitle);
        fixture.saveMovie("tt-catalog-2", secondTitle);
    }

    @Given("the movie catalog is empty")
    public void theMovieCatalogIsEmpty() {
        fixture.clearMovies();
    }

    @Given("movie {string} exists with title {string}")
    public void movieExistsWithTitle(String imdbId, String title) {
        fixture.saveMovie(imdbId, title);
    }

    @Given("movie {string} has comment {string} by {string}")
    public void movieHasCommentBy(String imdbId, String text, String username) {
        fixture.addComment(imdbId, username, text);
    }

    @Given("regular user {string} is authenticated")
    public void regularUserIsAuthenticated(String username) {
        currentUsername = username;
        currentRole = "MOVIES_USER";
    }

    @Given("admin user {string} is authenticated")
    public void adminUserIsAuthenticated(String username) {
        currentUsername = username;
        currentRole = "MOVIES_ADMIN";
    }

    @When("the viewer requests the movie catalog")
    public void theViewerRequestsTheMovieCatalog() {
        catalog = viewMovieCatalog.viewCatalog();
    }

    @When("the viewer opens details for movie {string}")
    public void theViewerOpensDetailsForMovie(String imdbId) {
        movieDetails = viewMovieDetails.viewMovie(imdbId);
    }

    @When("contributor {string} adds movie {string} titled {string}")
    public void contributorAddsMovieTitled(String username, String imdbId, String title) {
        addMovieToCatalog.addMovie(new AddMovieCommand(imdbId, title, "N/A", "N/A", ""));
    }

    @When("an anonymous caller tries to add movie {string}")
    public void anonymousCallerTriesToAddMovie(String imdbId) throws Exception {
        lastResponse = mockMvc.perform(post("/api/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "imdbId", imdbId,
                                "title", "Anonymous Movie",
                                "director", "N/A",
                                "year", "N/A"))))
                .andReturn();
    }

    @When("user {string} comments {string} on movie {string}")
    public void userCommentsOnMovie(String username, String text, String imdbId) {
        addMovieComment.addComment(new AddCommentCommand(imdbId, username, text));
    }

    @When("user {string} tries to comment blank text on movie {string}")
    public void userTriesToCommentBlankTextOnMovie(String username, String imdbId) {
        commandError = assertThrows(RuntimeException.class,
                () -> addMovieComment.addComment(new AddCommentCommand(imdbId, username, " ")));
    }

    @When("the regular user requests own user profile")
    public void theRegularUserRequestsOwnUserProfile() throws Exception {
        lastResponse = mockMvc.perform(get("/api/userextras/me").with(jwtForCurrentUser()))
                .andReturn();
    }

    @When("the regular user tries to update own user profile")
    public void theRegularUserTriesToUpdateOwnUserProfile() throws Exception {
        lastResponse = mockMvc.perform(post("/api/userextras/me")
                        .with(jwtForCurrentUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("avatar", "new-avatar"))))
                .andReturn();
    }

    @When("the admin requests the registered users list")
    public void theAdminRequestsTheRegisteredUsersList() throws Exception {
        lastResponse = mockMvc.perform(get("/api/users").with(jwtForCurrentUser()))
                .andReturn();
    }

    @When("the regular user requests the registered users list")
    public void theRegularUserRequestsTheRegisteredUsersList() throws Exception {
        lastResponse = mockMvc.perform(get("/api/users").with(jwtForCurrentUser()))
                .andReturn();
    }

    @When("admin updates movie {string} title to {string}")
    public void adminUpdatesMovieTitleTo(String imdbId, String title) {
        administerMovieCatalog.updateMovie(new UpdateMovieCommand(imdbId, title, null, null, null));
    }

    @When("regular user {string} tries to delete movie {string}")
    public void regularUserTriesToDeleteMovie(String username, String imdbId) throws Exception {
        currentUsername = username;
        currentRole = "MOVIES_USER";
        lastResponse = mockMvc.perform(delete("/api/movies/{imdbId}", imdbId).with(jwtForCurrentUser()))
                .andReturn();
    }

    @Then("the catalog lists {string} before {string}")
    public void theCatalogListsBefore(String firstTitle, String secondTitle) {
        List<String> titles = catalog.stream().map(MovieDto::title).toList();
        assertTrue(titles.indexOf(firstTitle) < titles.indexOf(secondTitle),
                "Expected " + firstTitle + " before " + secondTitle + " but got " + titles);
    }

    @Then("the catalog contains {int} movies")
    public void theCatalogContainsMovies(int count) {
        assertEquals(count, catalog.size());
    }

    @Then("the movie details show title {string}")
    public void theMovieDetailsShowTitle(String title) {
        assertEquals(title, movieDetails.title());
    }

    @Then("the first movie comment is {string}")
    public void theFirstMovieCommentIs(String text) {
        assertEquals(text, movieDetails.comments().getFirst().text());
    }

    @Then("movie {string} exists in the catalog with title {string}")
    public void movieExistsInTheCatalogWithTitle(String imdbId, String title) {
        MovieDto movie = viewMovieDetails.viewMovie(imdbId);
        assertEquals(title, movie.title());
    }

    @Then("movie {string} has a comment {string} by {string}")
    public void movieHasACommentBy(String imdbId, String text, String username) {
        MovieDto movie = viewMovieDetails.viewMovie(imdbId);
        assertTrue(movie.comments().stream()
                .anyMatch(comment -> comment.username().equals(username) && comment.text().equals(text)));
    }

    @Then("the comment command is rejected because text is required")
    public void theCommentCommandIsRejectedBecauseTextIsRequired() {
        assertTrue(commandError instanceof IllegalArgumentException);
    }

    @Then("the API response status is {int}")
    public void theApiResponseStatusIs(int status) {
        assertEquals(status, lastResponse.getResponse().getStatus());
    }

    @Then("the API response status is 401 or 403")
    public void theApiResponseStatusIsUnauthorizedOrForbidden() {
        int status = lastResponse.getResponse().getStatus();
        assertTrue(status == 401 || status == 403, "Expected 401 or 403 but got " + status);
    }

    @Then("the profile username is {string}")
    public void theProfileUsernameIs(String username) throws Exception {
        JsonNode body = objectMapper.readTree(lastResponse.getResponse().getContentAsString());
        assertEquals(username, body.get("username").asText());
    }

    @Then("the registered users list contains {string}")
    public void theRegisteredUsersListContains(String username) throws Exception {
        JsonNode body = objectMapper.readTree(lastResponse.getResponse().getContentAsString());
        assertTrue(body.findValuesAsText("username").contains(username));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtForCurrentUser() {
        return jwt()
                .jwt(jwt -> jwt
                        .subject(currentUsername)
                        .claim("preferred_username", currentUsername)
                        .claim("email", currentUsername + "@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + currentRole));
    }
}
