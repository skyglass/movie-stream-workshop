package skycomposer.moviechallenge.api.bdd.movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import skycomposer.moviechallenge.api.bdd.fixture.RestApiFixture;
import skycomposer.moviechallenge.api.movie.CategoryService;
import skycomposer.moviechallenge.api.movie.MovieGuideService;
import skycomposer.moviechallenge.api.movie.dto.CategoryDto;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.dto.MovieGuideDto;
import skycomposer.moviechallenge.api.movie.dto.SaveCategoryRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Covers the "curate-movie-guide" use case (docs/specs/movie-guides/guide-curation/curate-movie-guide/uc.feature):
// creation, subscribing to categories, adding movies (direct and CSV), listing "mine", and deletion safety --
// through the real REST boundary, exactly like the other *AcceptanceTest classes in this package.
public class MovieGuideAcceptanceTest {
    private final MovieGuideService movieGuides;
    private final CategoryService categories;
    private final RestApiFixture restApi;
    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private final Map<String, MovieGuideDto> guidesByName = new HashMap<>();
    private final Map<String, Long> categoriesByName = new HashMap<>();

    public MovieGuideAcceptanceTest(MovieGuideService movieGuides, CategoryService categories, RestApiFixture restApi,
                                    MockMvc mockMvc, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.movieGuides = movieGuides;
        this.categories = categories;
        this.restApi = restApi;
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Given("{string} creates a Movie Guide named {string}")
    public void createsGuide(String username, String name) throws Exception {
        create(username, "Guide", name);
    }

    @Given("{string} creates a Movie Personality named {string}")
    public void createsPersonality(String username, String name) throws Exception {
        create(username, "Personality", name);
    }

    private void create(String username, String type, String name) throws Exception {
        var result = mockMvc.perform(post("/api/movie-guides/wizard")
                .with(restApi.jwt(username, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"%s","name":"%s","description":null,"icon":null,"subscribedCategoryIds":[]}
                        """.formatted(type, name))).andReturn();
        restApi.record(result);
        if (result.getResponse().getStatus() == 201) {
            guidesByName.put(name, objectMapper.readValue(result.getResponse().getContentAsString(), MovieGuideDto.class));
        }
    }

    @Given("category {string} exists")
    public void categoryExists(String name) {
        CategoryDto category = categories.create(new SaveCategoryRequest(name, null, null, null), "admin", true);
        categoriesByName.put(name, category.id());
    }

    @Given("category {string} exists under category {string}")
    public void categoryExistsUnder(String name, String parentName) {
        long parentId = categoriesByName.get(parentName);
        CategoryDto category = categories.create(new SaveCategoryRequest(name, null, null, parentId), "admin", true);
        categoriesByName.put(name, category.id());
    }

    @Given("category {string} exists under the Movie Guide {string}")
    public void categoryExistsUnderGuide(String name, String guideName) {
        long parentId = guidesByName.get(guideName).categoryId();
        CategoryDto category = categories.create(new SaveCategoryRequest(name, null, null, parentId), "admin", true);
        categoriesByName.put(name, category.id());
    }

    // "Subscribing" is now just creating a new OR-composition category, under the guide's own root, with the
    // followed category as its sole component -- the same generic mechanism CategoryController.create() backs for
    // any composition/subscription category. Mirroring the followed category's own name (rather than requiring a
    // separate name from the caller here) matches what the UI's "Subscribe to Categories" shortcut does.
    @When("user {string} with role {string} subscribes the Movie Guide {string} to category {string}")
    public void subscribes(String username, String role, String guideName, String categoryName) throws Exception {
        long guideRootId = guidesByName.get(guideName).categoryId();
        long categoryId = categoriesByName.get(categoryName);
        restApi.record(mockMvc.perform(post("/api/categories")
                .with(restApi.jwt(username, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","description":null,"icon":null,"parentId":%d,"componentCategoryIds":[%d],"publicComponentCategoryIds":[],"operator":"OR"}
                        """.formatted(categoryName, guideRootId, categoryId))).andReturn());
    }

    // "Unsubscribing" is now an ordinary delete of the subscription wrapper category itself (see subscribes
    // above) -- there's no separate reconcile/diff endpoint anymore, so this looks the wrapper up first.
    @When("user {string} with role {string} unsubscribes the Movie Guide {string} from category {string}")
    public void unsubscribes(String username, String role, String guideName, String categoryName) throws Exception {
        long guideRootId = guidesByName.get(guideName).categoryId();
        long wrapperId = subscriptionWrapperId(guideName, categoryName);
        restApi.record(mockMvc.perform(delete("/api/categories/{id}", wrapperId)
                .with(restApi.jwt(username, role))
                .param("parentId", String.valueOf(guideRootId))).andReturn());
    }

    @When("an anonymous viewer requests the Movie Guide {string} through the API")
    public void requestsAnonymously(String guideName) throws Exception {
        long categoryId = guidesByName.get(guideName).categoryId();
        restApi.record(mockMvc.perform(get("/api/movie-guides/by-category/{categoryId}", categoryId)).andReturn());
    }

    @When("user {string} with role {string} adds movie {string} to the Movie Guide {string}")
    public void addsMovie(String username, String role, String imdbId, String guideName) throws Exception {
        long guideId = guidesByName.get(guideName).id();
        restApi.record(mockMvc.perform(post("/api/movie-guides/{id}/wizard-movies", guideId)
                .with(restApi.jwt(username, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imdbIds\":[\"%s\"]}".formatted(imdbId))).andReturn());
    }

    // categoryName may name either a plain native sub-category of the guide (categoriesByName already holds its
    // real, guide-scoped id) or a category the guide has subscribed to (categoriesByName holds the ORIGINAL
    // category's id, which -- unlike the old DAG-copy-link days -- is no longer itself a child of the guide's
    // root; only the subscription wrapper is). Resolving through subscriptionWrapperId first (falling back to the
    // plain id when there's no wrapper) lets every scenario share this one step regardless of which case applies.
    @When("user {string} with role {string} adds movie {string} to category {string} of the Movie Guide {string}")
    public void addsMovieToCategory(String username, String role, String imdbId, String categoryName, String guideName) throws Exception {
        long guideId = guidesByName.get(guideName).id();
        Long wrapperId = subscriptionWrapperIdOrNull(guideName, categoryName);
        long categoryId = wrapperId != null ? wrapperId : categoriesByName.get(categoryName);
        restApi.record(mockMvc.perform(post("/api/movie-guides/{id}/wizard-movies", guideId)
                .with(restApi.jwt(username, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imdbIds\":[\"%s\"],\"categoryId\":%d}".formatted(imdbId, categoryId))).andReturn());
    }

    @When("{string} imports CSV row {string} into the Movie Guide {string}")
    public void importsCsvRow(String username, String imdbId, String guideName) throws Exception {
        importCsvRow(username, imdbId, List.of(), guideName);
    }

    @When("{string} imports CSV row {string} with suggested category {string} into the Movie Guide {string}")
    public void importsCsvRowWithCategory(String username, String imdbId, String categoryPath, String guideName) throws Exception {
        importCsvRow(username, imdbId, List.of(categoryPath), guideName);
    }

    private void importCsvRow(String username, String imdbId, List<String> categoryPaths, String guideName) throws Exception {
        long guideId = guidesByName.get(guideName).id();
        String paths = categoryPaths.stream().map(path -> "\"" + path + "\"").reduce((a, b) -> a + "," + b).orElse("");
        restApi.record(mockMvc.perform(post("/api/movie-guides/{id}/import-csv", guideId)
                .with(restApi.jwt(username, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"movies\":[{\"imdbId\":\"%s\",\"categoryPaths\":[%s]}]}".formatted(imdbId, paths))).andReturn());
    }

    // categoryName may name either a plain category or a Movie Guide (its own anchor category is used as the
    // scope) -- matches how the "Delete Movies" dialog scopes to either a picked sub-category or the guide root.
    @When("user {string} with role {string} removes movie {string} from the Movie Guide {string} scoped to category {string}")
    public void removesMovieScoped(String username, String role, String imdbId, String guideName, String categoryName) throws Exception {
        long guideId = guidesByName.get(guideName).id();
        long categoryId = guidesByName.containsKey(categoryName)
                ? guidesByName.get(categoryName).categoryId() : categoriesByName.get(categoryName);
        restApi.record(mockMvc.perform(post("/api/movie-guides/{id}/movies/{imdbId}/remove", guideId, imdbId)
                .with(restApi.jwt(username, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryIds\":[%d]}".formatted(categoryId))).andReturn());
    }

    @When("user {string} with role {string} deletes the Movie Guide {string}")
    public void deletesGuide(String username, String role, String guideName) throws Exception {
        long categoryId = guidesByName.get(guideName).categoryId();
        Long parentId = jdbc.queryForObject(
                "select parent_id from category_parent_child where child_id=? and parent_id<>? limit 1",
                Long.class, categoryId, categoryId);
        restApi.record(mockMvc.perform(delete("/api/categories/{id}", categoryId)
                .with(restApi.jwt(username, role))
                .param("parentId", String.valueOf(parentId))).andReturn());
    }

    @When("{string} requests their own Movie Guides through the API")
    public void requestsMine(String username) throws Exception {
        restApi.record(mockMvc.perform(get("/api/movie-guides/mine")
                .with(restApi.jwt(username, "USER"))).andReturn());
    }

    @Then("the Movie Guide API response status is {int}")
    public void status(int status) { restApi.assertStatus(status); }

    @Then("the Movie Guide {string} is owned by {string}")
    public void isOwnedBy(String guideName, String owner) {
        assertEquals(owner, guidesByName.get(guideName).owner());
    }

    @Then("the Movie Guide {string} has type {string} under root category {string}")
    public void hasTypeUnderRoot(String guideName, String type, String rootName) {
        MovieGuideDto guide = guidesByName.get(guideName);
        assertEquals(type, guide.type());
        Long parentId = jdbc.queryForObject(
                "select parent_id from category_parent_child where child_id=? and parent_id<>? limit 1",
                Long.class, guide.categoryId(), guide.categoryId());
        String parentName = jdbc.queryForObject("select name from category where id=?", String.class, parentId);
        assertEquals(rootName, parentName);
    }

    @Then("the Movie Guide response body identifies the owner as {string}")
    public void responseIdentifiesOwner(String owner) throws Exception {
        assertTrue(restApi.responseBody().contains("\"owner\":\"" + owner + "\""));
    }

    @Then("the Movie Guide {string} is subscribed to category {string}")
    public void isSubscribedTo(String guideName, String categoryName) {
        assertTrue(subscriptionWrapperIdOrNull(guideName, categoryName) != null);
    }

    @Then("the Movie Guide {string} is not subscribed to category {string}")
    public void isNotSubscribedTo(String guideName, String categoryName) {
        assertFalse(subscriptionWrapperIdOrNull(guideName, categoryName) != null);
    }

    // A "subscription" is now just an OR-composition category, created directly under the guide's own root, whose
    // sole component is the followed category -- there's no separate flat pointer table anymore, so "is guide G
    // subscribed to category C" means "does such a wrapper exist", found by walking the guide root's direct
    // composable children for one whose component is C (see MovieGuideService.subscribedCategoryIds, the same
    // join this mirrors).
    private Long subscriptionWrapperIdOrNull(String guideName, String categoryName) {
        long guideRootId = guidesByName.get(guideName).categoryId();
        Long categoryId = categoriesByName.get(categoryName);
        if (categoryId == null) return null;
        List<Long> wrapperIds = jdbc.queryForList("""
                select cc.category_id from category_parent_child direct
                join composition_category cc on cc.category_id = direct.child_id
                join composition_category_component comp on comp.composition_category_id = cc.category_id
                where direct.parent_id = ? and cc.operator = 0 and comp.component_category_id = ?
                """, Long.class, guideRootId, categoryId);
        return wrapperIds.isEmpty() ? null : wrapperIds.get(0);
    }

    private long subscriptionWrapperId(String guideName, String categoryName) {
        Long wrapperId = subscriptionWrapperIdOrNull(guideName, categoryName);
        if (wrapperId == null) throw new IllegalStateException(
                "No subscription wrapper found for guide \"" + guideName + "\" and category \"" + categoryName + "\"");
        return wrapperId;
    }

    @Then("category {string} still has parent {string}")
    public void stillHasParent(String categoryName, String parentName) {
        long categoryId = categoriesByName.get(categoryName);
        long parentId = categoriesByName.get(parentName);
        Boolean exists = jdbc.queryForObject(
                "select exists(select 1 from category_parent_child where child_id=? and parent_id=?)",
                Boolean.class, categoryId, parentId);
        assertTrue(Boolean.TRUE.equals(exists));
    }

    @Then("category {string} has parents {string} and {string}")
    public void hasParents(String categoryName, String parentName1, String parentName2) {
        long categoryId = categoriesByName.get(categoryName);
        List<Long> parentIds = jdbc.queryForList(
                "select parent_id from category_parent_child where child_id=? and parent_id<>?",
                Long.class, categoryId, categoryId);
        long expectedParent1 = categoriesByName.get(parentName1);
        long expectedParent2 = guidesByName.containsKey(parentName2)
                ? guidesByName.get(parentName2).categoryId() : categoriesByName.get(parentName2);
        assertTrue(parentIds.contains(expectedParent1), "expected parent " + parentName1 + " (" + expectedParent1 + ") in " + parentIds);
        assertTrue(parentIds.contains(expectedParent2), "expected parent " + parentName2 + " (" + expectedParent2 + ") in " + parentIds);
    }

    @Then("the Movie Guide {string} movie list contains {string}")
    public void movieListContains(String guideName, String imdbId) {
        long guideId = guidesByName.get(guideName).id();
        List<MovieDto> movies = movieGuides.guideMovies(guideId, 1, 50, null, null).movies();
        assertTrue(movies.stream().anyMatch(movie -> movie.imdbId().equals(imdbId)));
    }

    @Then("the Movie Guide {string} movie list does not contain {string}")
    public void movieListDoesNotContain(String guideName, String imdbId) {
        long guideId = guidesByName.get(guideName).id();
        List<MovieDto> movies = movieGuides.guideMovies(guideId, 1, 50, null, null).movies();
        assertFalse(movies.stream().anyMatch(movie -> movie.imdbId().equals(imdbId)));
    }

    @Then("the Movie Guide CSV import reports no failed rows")
    public void noFailedRows() throws Exception {
        assertTrue(restApi.responseBody().contains("\"failedMovies\":[]"));
    }

    @Then("the Movie Guide {string} has a sub-category path {string} containing movie {string}")
    public void hasSubCategoryPathContainingMovie(String guideName, String dotPath, String imdbId) {
        long parentId = guidesByName.get(guideName).categoryId();
        for (String segment : dotPath.split("\\.")) {
            parentId = jdbc.queryForObject("""
                    select c.id from category c join category_parent_child pc on pc.child_id=c.id
                    where pc.parent_id=? and lower(c.name)=lower(?)
                    """, Long.class, parentId, segment);
        }
        Integer count = jdbc.queryForObject("select count(*) from movie_category where movie_id=? and category_id=?",
                Integer.class, imdbId, parentId);
        assertEquals(1, count);
    }

    @Then("category {string} still contains movie {string}")
    public void categoryStillContainsMovie(String categoryName, String imdbId) {
        long categoryId = categoriesByName.get(categoryName);
        Integer count = jdbc.queryForObject("select count(*) from movie_category where movie_id=? and category_id=?",
                Integer.class, imdbId, categoryId);
        assertEquals(1, count);
    }

    @Then("category {string} still exists")
    public void categoryStillExists(String name) {
        Long id = categoriesByName.get(name);
        Boolean exists = jdbc.queryForObject("select exists(select 1 from category where id=?)", Boolean.class, id);
        assertTrue(Boolean.TRUE.equals(exists));
    }

    @Then("the {string} response contains the category for {string} but not for {string}")
    public void mineContainsButNot(String responseName, String includedGuide, String excludedGuide) throws Exception {
        String body = restApi.responseBody();
        assertTrue(body.contains(String.valueOf(guidesByName.get(includedGuide).categoryId())));
        assertFalse(body.contains(String.valueOf(guidesByName.get(excludedGuide).categoryId())));
    }
}
