package com.ivanfranchin.moviesapi.bdd.user;

import com.ivanfranchin.moviesapi.bdd.user.fixture.UserAccessFixture;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ViewOwnUserProfileUseCaseTest {

    private final UserAccessFixture fixture;
    private final MockMvc mockMvc;

    public ViewOwnUserProfileUseCaseTest(UserAccessFixture fixture, MockMvc mockMvc) {
        this.fixture = fixture;
        this.mockMvc = mockMvc;
    }

    @When("the regular user requests own user profile")
    public void theRegularUserRequestsOwnUserProfile() throws Exception {
        fixture.lastResponse(mockMvc.perform(get("/api/userextras/me")
                        .with(fixture.jwtForCurrentUser()))
                .andReturn());
    }

    @Then("own profile username is {string}")
    public void ownProfileUsernameIs(String username) throws Exception {
        fixture.assertLastResponseJsonFieldIs("username", username);
    }

    @Then("own profile email is {string}")
    public void ownProfileEmailIs(String email) throws Exception {
        fixture.assertLastResponseJsonFieldIs("email", email);
    }

    @Then("the local users table has no first-name or last-name columns")
    public void theLocalUsersTableHasNoFirstNameOrLastNameColumns() {
        fixture.assertUsersTableHasNoNameColumns();
    }
}
