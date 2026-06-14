package com.ivanfranchin.moviesapi.bdd.user.fixture;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

public class UserAccessStepDefinitions {

    private final UserAccessFixture fixture;

    public UserAccessStepDefinitions(UserAccessFixture fixture) {
        this.fixture = fixture;
    }

    @Given("regular user {string} is authenticated")
    public void regularUserIsAuthenticated(String username) {
        fixture.authenticateRegularUser(username);
    }

    @Given("admin user {string} is authenticated")
    public void adminUserIsAuthenticated(String username) {
        fixture.authenticateAdminUser(username);
    }

    @Then("the user access API response status is {int}")
    public void theUserAccessApiResponseStatusIs(int status) {
        fixture.assertLastResponseStatus(status);
    }
}
