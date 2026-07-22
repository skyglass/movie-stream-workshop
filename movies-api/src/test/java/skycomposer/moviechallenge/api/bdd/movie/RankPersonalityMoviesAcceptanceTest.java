package skycomposer.moviechallenge.api.bdd.movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import skycomposer.moviechallenge.api.bdd.fixture.RestApiFixture;
import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.dto.MovieGuideDto;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// Covers the "rank-personality-movies" use case (docs/specs/movie-guides/guide-curation/rank-personality-movies/uc.feature).
// Deliberately doesn't reuse MovieGuideAcceptanceTest's "creates a Movie Guide/Personality"/"adds movie" steps --
// those key off that class's own private guidesByName map, which isn't shared across glue classes -- so this
// class owns its own guide creation and movie-assignment steps (via direct JDBC for the latter, since movie
// assignment mechanics are already covered by MovieGuideAcceptanceTest and aren't what's under test here).
public class RankPersonalityMoviesAcceptanceTest {
    private final RestApiFixture restApi;
    private final MovieCatalogFixture fixture;
    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private final Map<String, MovieGuideDto> guidesByName = new HashMap<>();
    private MoviePageDto lastPersonalityMovies;

    public RankPersonalityMoviesAcceptanceTest(RestApiFixture restApi, MovieCatalogFixture fixture, MockMvc mockMvc,
                                               JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.restApi = restApi;
        this.fixture = fixture;
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Given("a Movie Personality {string} exists, owned by {string}")
    public void aMoviePersonalityExists(String name, String owner) throws Exception {
        createGuide(name, owner, "Personality");
    }

    @Given("a Movie Guide {string} exists, owned by {string}")
    public void aMovieGuideExists(String name, String owner) throws Exception {
        createGuide(name, owner, "Guide");
    }

    private void createGuide(String name, String owner, String type) throws Exception {
        var result = mockMvc.perform(post("/api/movie-guides/wizard")
                .with(restApi.jwt(owner, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"%s","name":"%s","description":null,"icon":null,"subscribedCategoryIds":[]}
                        """.formatted(type, name))).andReturn();
        restApi.record(result);
        guidesByName.put(name, objectMapper.readValue(result.getResponse().getContentAsString(), MovieGuideDto.class));
    }

    @Given("movies {string} are filed under the Movie Guide {string}")
    public void moviesAreFiledUnder(String commaSeparatedImdbIds, String guideName) {
        long categoryId = guidesByName.get(guideName).categoryId();
        for (String imdbId : splitIds(commaSeparatedImdbIds)) {
            jdbc.update("insert into movie_category(movie_id, category_id) values (?, ?) on conflict do nothing",
                    imdbId, categoryId);
        }
    }

    @When("user {string} with role {string} ranks the Movie Personality {string} with movies {string}")
    public void ranksPersonality(String username, String role, String guideName, String commaSeparatedImdbIds) throws Exception {
        long guideId = guidesByName.get(guideName).id();
        List<String> imdbIds = splitIds(commaSeparatedImdbIds);
        String body = objectMapper.writeValueAsString(Map.of("orderedImdbIds", imdbIds));
        var result = mockMvc.perform(post("/api/movie-guides/{id}/ranking", guideId)
                .with(restApi.jwt(username, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        restApi.record(result);
        if (result.getResponse().getStatus() < 400) {
            guidesByName.put(guideName, objectMapper.readValue(result.getResponse().getContentAsString(), MovieGuideDto.class));
        }
    }

    @Then("the Movie Personality {string} movie list is ordered {string}")
    public void movieListIsOrdered(String guideName, String commaSeparatedImdbIds) {
        long guideId = guidesByName.get(guideName).id();
        List<String> actual = jdbc.queryForList(
                "select movie_id from personality_movie_rank where personality_id=? order by rank asc",
                String.class, guideId);
        assertEquals(splitIds(commaSeparatedImdbIds), actual);
    }

    // Goes through the real "Edit" entry point (CategoryController.update, the only rename path that exists --
    // EditGuideDialogComponent calls updateCategory, never a movie-guide-specific endpoint), scoped to the
    // guide's own anchor category id.
    @When("user {string} with role {string} renames the Movie Guide {string} to {string}")
    public void renamesGuide(String username, String role, String guideName, String newName) throws Exception {
        MovieGuideDto guide = guidesByName.get(guideName);
        String body = objectMapper.writeValueAsString(Map.of("name", newName, "description", "", "icon", ""));
        var result = mockMvc.perform(put("/api/categories/{id}", guide.categoryId())
                .with(restApi.jwt(username, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        restApi.record(result);
        if (result.getResponse().getStatus() < 400) {
            var refreshed = mockMvc.perform(get("/api/movie-guides/by-category/{categoryId}", guide.categoryId())).andReturn();
            MovieGuideDto updated = objectMapper.readValue(refreshed.getResponse().getContentAsString(), MovieGuideDto.class);
            guidesByName.put(guideName, updated);
            guidesByName.put(newName, updated);
        }
    }

    @Then("the Movie Personality {string} has ranking username {string}")
    public void hasRankingUsername(String guideName, String username) {
        assertEquals(username, guidesByName.get(guideName).rankingUsername());
    }

    @When("an anonymous viewer requests the Movie Personality {string} movie results")
    public void requestsPersonalityMovieResults(String guideName) throws Exception {
        long guideId = guidesByName.get(guideName).id();
        var result = mockMvc.perform(get("/api/movie-guides/{id}/personality-movies", guideId).param("pageSize", "50")).andReturn();
        restApi.record(result);
        lastPersonalityMovies = objectMapper.readValue(result.getResponse().getContentAsString(), MoviePageDto.class);
    }

    // Doesn't assert the exact Bradley-Terry-fitted decimal rating (a derived output, not something worth
    // hand-predicting in a test) -- just that the enrichment wiring genuinely reaches the real endpoint: the
    // rank position matches the submitted order, and a real positive rating is present (not the null/"-" a plain
    // single-arg toMovieDto call would silently produce, which is exactly the bug this scenario guards against).
    @Then("movie {string} has Personality's Rating rank {int}")
    public void hasPersonalityRatingRank(String imdbId, int expectedRank) {
        MovieDto movie = lastPersonalityMovies.movies().stream().filter(m -> m.imdbId().equals(imdbId)).findFirst()
                .orElseThrow(() -> new AssertionError("Movie " + imdbId + " not found in personality movie results"));
        assertEquals(expectedRank, movie.rankPosition());
        assertTrue(movie.rating() != null && movie.rating().compareTo(BigDecimal.ZERO) > 0,
                "Expected a positive Personality's Rating for " + imdbId + " but got " + movie.rating());
    }

    // Reuses ShareMyFavoriteMoviesAcceptanceTest's "anonymous viewer requests shared favorite movies for encoded
    // username" step (same MovieCatalogFixture instance, same scenario) to populate the shared movie list.
    @Then("the movie list is ordered {string}")
    public void theMovieListIsOrdered(String commaSeparatedImdbIds) {
        fixture.assertMovieListOrderedExactly(commaSeparatedImdbIds);
    }

    @Then("the movie list does not contain {string}")
    public void theMovieListDoesNotContain(String imdbId) {
        fixture.assertMovieListDoesNotContainImdbId(imdbId);
    }

    private List<String> splitIds(String commaSeparatedImdbIds) {
        return Arrays.stream(commaSeparatedImdbIds.split(",")).map(String::trim).filter(id -> !id.isBlank()).toList();
    }
}
