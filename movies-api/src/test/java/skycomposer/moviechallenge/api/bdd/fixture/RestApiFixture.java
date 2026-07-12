package skycomposer.moviechallenge.api.bdd.fixture;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
/** Test state and JWT builders belonging to the mocked REST boundary, not to a database-backed domain. */
public class RestApiFixture {

    private MvcResult lastResponse;

    public RequestPostProcessor jwt(String username, String role) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token
                        .subject(username)
                        .claim("preferred_username", username)
                        .claim("email", username + "@skycomposer.net"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public void record(MvcResult response) {
        lastResponse = response;
    }

    public void assertStatus(int status) {
        assertEquals(status, lastResponse.getResponse().getStatus());
    }

    public void reset() {
        lastResponse = null;
    }
}
