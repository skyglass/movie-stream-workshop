package com.ivanfranchin.moviesapi.bdd.user.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanfranchin.moviesapi.userextra.UserExtraRepository;
import com.ivanfranchin.moviesapi.userextra.model.UserExtra;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public class UserAccessFixture {

    private final UserExtraRepository userExtraRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private UserExtra userExtra;
    private RuntimeException lastError;
    private MvcResult lastResponse;
    private String currentUsername;
    private String currentRole;

    public UserAccessFixture(UserExtraRepository userExtraRepository, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.userExtraRepository = userExtraRepository;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void resetPersistentScenarioState() {
        resetScenarioState();
        userExtraRepository.deleteAll();
        userExtraRepository.save(new UserExtra("admin", "admin@skycomposer.net"));
        userExtraRepository.save(new UserExtra("user", "user@skycomposer.net"));
        userExtraRepository.flush();
    }

    public void resetScenarioState() {
        userExtra = null;
        lastError = null;
        lastResponse = null;
        currentUsername = null;
        currentRole = null;
    }

    public void assertLastResponseStatus(int status) {
        assertEquals(status, lastResponse.getResponse().getStatus());
    }

    public void assertLastResponseJsonFieldIs(String fieldName, String value) throws Exception {
        JsonNode body = objectMapper.readTree(lastResponse.getResponse().getContentAsString());
        assertEquals(value, body.get(fieldName).asText());
    }

    public void assertUserExtraAvatarIs(String avatarSeed) {
        assertEquals(avatarSeed, userExtra.getAvatar());
        assertEquals(avatarSeed, getUserExtra(userExtra.getUsername()).getAvatar());
    }

    public void assertUserExtraIdentityIsCurrentUser() {
        assertEquals(currentUsername, userExtra.getUsername());
        assertEquals(currentUserEmail(), userExtra.getEmail());
    }

    public void assertLastErrorIsIllegalArgumentException() {
        assertTrue(lastError instanceof IllegalArgumentException);
    }

    public void assertStoredUserExtraAvatarIs(String username, String avatarSeed) {
        assertEquals(avatarSeed, getUserExtra(username).getAvatar());
    }

    public void assertLastResponseContainsUsername(String username) throws Exception {
        JsonNode body = objectMapper.readTree(lastResponse.getResponse().getContentAsString());
        assertTrue(body.findValuesAsText("username").contains(username));
    }

    public void assertUsersTableHasNoNameColumns() {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name = 'users'
                  and column_name in ('first_name', 'last_name')
                """, Integer.class);
        assertEquals(0, count);
    }

    public UserExtra getUserExtra(String username) {
        return userExtraRepository.findById(username).orElseThrow();
    }

    public void authenticateRegularUser(String username) {
        currentUsername = username;
        currentRole = "MOVIES_USER";
    }

    public void authenticateAdminUser(String username) {
        currentUsername = username;
        currentRole = "MOVIES_ADMIN";
    }

    public String currentUsername() {
        return currentUsername;
    }

    public String currentUserEmail() {
        return currentUsername + "@skycomposer.net";
    }

    public RequestPostProcessor jwtForCurrentUser() {
        return jwt()
                .jwt(jwt -> jwt
                        .subject(currentUsername)
                        .claim("preferred_username", currentUsername)
                        .claim("email", currentUserEmail()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + currentRole));
    }

    public void userExtra(UserExtra userExtra) {
        this.userExtra = userExtra;
    }

    public void lastError(RuntimeException lastError) {
        this.lastError = lastError;
    }

    public void lastResponse(MvcResult lastResponse) {
        this.lastResponse = lastResponse;
    }
}
