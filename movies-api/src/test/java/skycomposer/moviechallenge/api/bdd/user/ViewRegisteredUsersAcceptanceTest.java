package skycomposer.moviechallenge.api.bdd.user;

import skycomposer.moviechallenge.api.bdd.user.fixture.UserAccessFixture;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ViewRegisteredUsersAcceptanceTest {

    private final UserAccessFixture fixture;
    private final MockMvc mockMvc;

    public ViewRegisteredUsersAcceptanceTest(UserAccessFixture fixture, MockMvc mockMvc) {
        this.fixture = fixture;
        this.mockMvc = mockMvc;
    }

    @When("the admin requests the registered users list")
    public void theAdminRequestsTheRegisteredUsersList() throws Exception {
        fixture.lastResponse(mockMvc.perform(get("/api/users")
                        .with(fixture.jwtForCurrentUser()))
                .andReturn());
    }

    @Then("the registered users list contains {string}")
    public void theRegisteredUsersListContains(String username) throws Exception {
        fixture.assertLastResponseContainsUsername(username);
    }

    @When("the regular user requests the registered users list")
    public void theRegularUserRequestsTheRegisteredUsersList() throws Exception {
        fixture.lastResponse(mockMvc.perform(get("/api/users")
                        .with(fixture.jwtForCurrentUser()))
                .andReturn());
    }
}
