package skycomposer.moviechallenge.api.bdd.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import skycomposer.moviechallenge.api.bdd.user.fixture.UserAccessFixture;
import skycomposer.moviechallenge.api.userextra.application.service.ChangeOwnAvatarUseCase;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class ChangeOwnAvatarAcceptanceTest {

    private final UserAccessFixture fixture;
    private final ChangeOwnAvatarUseCase changeOwnAvatar;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ChangeOwnAvatarAcceptanceTest(UserAccessFixture fixture,
                                      ChangeOwnAvatarUseCase changeOwnAvatar,
                                      MockMvc mockMvc,
                                      ObjectMapper objectMapper) {
        this.fixture = fixture;
        this.changeOwnAvatar = changeOwnAvatar;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @When("the regular user changes own avatar seed to {string}")
    public void theRegularUserChangesOwnAvatarSeedTo(String avatarSeed) {
        fixture.userExtra(changeOwnAvatar.changeAvatar(fixture.currentUsername(), avatarSeed));
    }

    @Then("own profile avatar seed is {string}")
    public void ownProfileAvatarSeedIs(String avatarSeed) {
        fixture.assertUserExtraAvatarIs(avatarSeed);
        fixture.assertUserExtraIdentityIsCurrentUser();
    }

    @When("the regular user tries to change own avatar seed to blank")
    public void theRegularUserTriesToChangeOwnAvatarSeedToBlank() {
        fixture.lastError(assertThrows(IllegalArgumentException.class,
                () -> changeOwnAvatar.changeAvatar(fixture.currentUsername(), " ")));
    }

    @Then("the avatar change is rejected because avatar seed is required")
    public void theAvatarChangeIsRejectedBecauseAvatarSeedIsRequired() {
        fixture.assertLastErrorIsIllegalArgumentException();
        fixture.assertStoredUserExtraAvatarIs(fixture.currentUsername(), fixture.currentUsername());
    }

    @When("an anonymous caller tries to change own avatar")
    public void anAnonymousCallerTriesToChangeOwnAvatar() throws Exception {
        fixture.lastResponse(mockMvc.perform(post("/api/userextras/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("avatar", "anonymous-avatar"))))
                .andReturn());
    }
}
