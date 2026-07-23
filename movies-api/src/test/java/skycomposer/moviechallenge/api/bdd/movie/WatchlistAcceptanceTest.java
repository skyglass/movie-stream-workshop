package skycomposer.moviechallenge.api.bdd.movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import skycomposer.moviechallenge.api.bdd.fixture.RestApiFixture;
import skycomposer.moviechallenge.api.movie.WatchlistService;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.dto.WatchlistDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Covers the "curate-watchlist" use case (docs/specs/my-watchlists/watchlist-curation/curate-watchlist/uc.feature):
// creation (owner-scoped name uniqueness), subscribing to public categories without ever touching the public
// category tree, private sub-category CRUD scoped to the owner, movie assignment (flat top level vs. a private
// sub-category), the default view's 3-way union, and ownership enforcement on every read/write -- through the
// real REST boundary, exactly like MovieGuideAcceptanceTest. Reuses "category {string} exists" from
// MovieGuideAcceptanceTest and "movie {string} exists with title {string}" from MovieCatalogStepDefinitions
// (same Cucumber glue package) rather than redefining them.
public class WatchlistAcceptanceTest {
    private final WatchlistService watchlists;
    private final RestApiFixture restApi;
    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private final Map<String, WatchlistDto> watchlistsByName = new HashMap<>();
    private final Map<String, Long> privateCategoriesByName = new HashMap<>();

    public WatchlistAcceptanceTest(WatchlistService watchlists, RestApiFixture restApi, MockMvc mockMvc,
                                    JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.watchlists = watchlists;
        this.restApi = restApi;
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Given("{string} creates a Watchlist named {string}")
    public void createsWatchlist(String username, String name) throws Exception {
        var result = mockMvc.perform(post("/api/watchlists")
                .with(restApi.jwt(username, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","description":null,"icon":null,"subscribedCategoryIds":[]}
                        """.formatted(name))).andReturn();
        restApi.record(result);
        if (result.getResponse().getStatus() == 201) {
            watchlistsByName.put(name, objectMapper.readValue(result.getResponse().getContentAsString(), WatchlistDto.class));
        }
    }

    @Given("movie {string} is filed under category {string}")
    public void movieFiledUnderCategory(String imdbId, String categoryName) {
        long categoryId = categoryIdByName(categoryName);
        jdbc.update("insert into movie_category(movie_id,category_id) values (?,?)", imdbId, categoryId);
    }

    @Given("user {string} with role {string} creates private category {string} under the Watchlist {string}")
    public void createsPrivateCategory(String username, String role, String categoryName, String watchlistName) throws Exception {
        long parentId = watchlistsByName.get(watchlistName).categoryId();
        var result = mockMvc.perform(post("/api/private-categories")
                .with(restApi.jwt(username, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","description":null,"icon":null,"parentId":%d}
                        """.formatted(categoryName, parentId))).andReturn();
        restApi.record(result);
        if (result.getResponse().getStatus() == 201) {
            long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
            privateCategoriesByName.put(categoryName, id);
        }
    }

    @When("user {string} with role {string} requests the Watchlist {string} through the API")
    public void requestsWatchlist(String username, String role, String watchlistName) throws Exception {
        long id = watchlistsByName.get(watchlistName).id();
        restApi.record(mockMvc.perform(get("/api/watchlists/{id}", id).with(restApi.jwt(username, role))).andReturn());
    }

    // "Subscribing" is now just creating a new private OR-composition category, under the watchlist's own root,
    // whose sole component is the followed PUBLIC category (via publicComponentCategoryIds) -- the same generic
    // mechanism PrivateCategoryController.create() backs for any composition/subscription category. This never
    // touches the public category tree at all: publicComponentCategoryIds is a plain reference, not a tree edge.
    @When("user {string} with role {string} subscribes the Watchlist {string} to category {string}")
    public void subscribes(String username, String role, String watchlistName, String categoryName) throws Exception {
        long watchlistRootId = watchlistsByName.get(watchlistName).categoryId();
        long categoryId = categoryIdByName(categoryName);
        restApi.record(mockMvc.perform(post("/api/private-categories")
                .with(restApi.jwt(username, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","description":null,"icon":null,"parentId":%d,"componentCategoryIds":[],"publicComponentCategoryIds":[%d],"operator":"OR"}
                        """.formatted(categoryName, watchlistRootId, categoryId))).andReturn());
    }

    // "Unsubscribing" is now an ordinary delete of the subscription wrapper private category itself (see
    // subscribes above) -- there's no separate reconcile/diff endpoint anymore, so this looks the wrapper up first.
    @When("user {string} with role {string} unsubscribes the Watchlist {string} from category {string}")
    public void unsubscribes(String username, String role, String watchlistName, String categoryName) throws Exception {
        long watchlistRootId = watchlistsByName.get(watchlistName).categoryId();
        long wrapperId = subscriptionWrapperId(watchlistName, categoryName);
        restApi.record(mockMvc.perform(delete("/api/private-categories/{id}", wrapperId)
                .with(restApi.jwt(username, role))
                .param("parentId", String.valueOf(watchlistRootId))).andReturn());
    }

    @When("user {string} with role {string} adds movie {string} to the Watchlist {string}")
    public void addsMovie(String username, String role, String imdbId, String watchlistName) throws Exception {
        long watchlistId = watchlistsByName.get(watchlistName).id();
        restApi.record(mockMvc.perform(post("/api/watchlists/{id}/movies", watchlistId)
                .with(restApi.jwt(username, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imdbIds\":[\"%s\"]}".formatted(imdbId))).andReturn());
    }

    @When("user {string} with role {string} adds movie {string} to private category {string} of the Watchlist {string}")
    public void addsMovieToPrivateCategory(String username, String role, String imdbId, String categoryName, String watchlistName) throws Exception {
        long watchlistId = watchlistsByName.get(watchlistName).id();
        long categoryId = privateCategoriesByName.get(categoryName);
        restApi.record(mockMvc.perform(post("/api/watchlists/{id}/movies", watchlistId)
                .with(restApi.jwt(username, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imdbIds\":[\"%s\"],\"categoryId\":%d}".formatted(imdbId, categoryId))).andReturn());
    }

    @When("user {string} with role {string} removes movie {string} from the Watchlist {string}")
    public void removesMovie(String username, String role, String imdbId, String watchlistName) throws Exception {
        long watchlistId = watchlistsByName.get(watchlistName).id();
        restApi.record(mockMvc.perform(post("/api/watchlists/{id}/movies/{imdbId}/remove", watchlistId, imdbId)
                .with(restApi.jwt(username, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryIds\":[]}")).andReturn());
    }

    @When("user {string} with role {string} deletes the Watchlist {string}")
    public void deletesWatchlist(String username, String role, String watchlistName) throws Exception {
        long id = watchlistsByName.get(watchlistName).id();
        restApi.record(mockMvc.perform(delete("/api/watchlists/{id}", id).with(restApi.jwt(username, role))).andReturn());
    }

    @When("{string} requests their own Watchlists through the API")
    public void requestsMine(String username) throws Exception {
        restApi.record(mockMvc.perform(get("/api/watchlists/mine").with(restApi.jwt(username, "USER"))).andReturn());
    }

    @Then("the Watchlist API response status is {int}")
    public void status(int status) { restApi.assertStatus(status); }

    @Then("the Watchlist {string} is owned by {string}")
    public void isOwnedBy(String watchlistName, String owner) {
        assertEquals(owner, watchlistsByName.get(watchlistName).owner());
    }

    // Users -> username -> Watchlists -> watchlist_name: two hops up from the watchlist's own anchor category
    // should land on a private_category node named exactly the owner's username.
    @Then("the Watchlist {string} is anchored under {string} in the private tree")
    public void isAnchoredUnder(String watchlistName, String username) {
        long anchorId = watchlistsByName.get(watchlistName).categoryId();
        long watchlistsNodeId = privateParentOf(anchorId);
        long usernameNodeId = privateParentOf(watchlistsNodeId);
        String usernameNodeName = jdbc.queryForObject("select name from private_category where id=?", String.class, usernameNodeId);
        assertEquals(username, usernameNodeName);
    }

    @Then("the Watchlist {string} is subscribed to category {string}")
    public void isSubscribedTo(String watchlistName, String categoryName) {
        assertTrue(subscriptionWrapperIdOrNull(watchlistName, categoryName) != null);
    }

    @Then("the Watchlist {string} is not subscribed to category {string}")
    public void isNotSubscribedTo(String watchlistName, String categoryName) {
        assertFalse(subscriptionWrapperIdOrNull(watchlistName, categoryName) != null);
    }

    // A "subscription" is now just a private OR-composition category, created directly under the watchlist's own
    // root, whose sole component is the followed PUBLIC category -- there's no separate flat pointer table
    // anymore, so "is watchlist W subscribed to category C" means "does such a wrapper exist" (mirrors
    // WatchlistService.subscribedPublicCategoryIds's own join).
    private Long subscriptionWrapperIdOrNull(String watchlistName, String categoryName) {
        long watchlistRootId = watchlistsByName.get(watchlistName).categoryId();
        long categoryId = categoryIdByName(categoryName);
        List<Long> wrapperIds = jdbc.queryForList("""
                select cc.private_category_id from private_category_parent_child direct
                join private_composition_category cc on cc.private_category_id = direct.child_id
                join private_composition_category_component comp on comp.composition_category_id = cc.private_category_id
                where direct.parent_id = ? and cc.operator = 0 and comp.public_component_category_id = ?
                """, Long.class, watchlistRootId, categoryId);
        return wrapperIds.isEmpty() ? null : wrapperIds.get(0);
    }

    private long subscriptionWrapperId(String watchlistName, String categoryName) {
        Long wrapperId = subscriptionWrapperIdOrNull(watchlistName, categoryName);
        if (wrapperId == null) throw new IllegalStateException(
                "No subscription wrapper found for watchlist \"" + watchlistName + "\" and category \"" + categoryName + "\"");
        return wrapperId;
    }

    // Confirms subscribing never grafts a DAG edge into the public tree (unlike Movie Guide's copy-link) -- the
    // category should still have only its own self-referencing root edge.
    @Then("category {string} still has no other parent")
    public void categoryStillHasNoOtherParent(String categoryName) {
        long categoryId = categoryIdByName(categoryName);
        Integer parentCount = jdbc.queryForObject(
                "select count(*) from category_parent_child where child_id=? and parent_id<>?",
                Integer.class, categoryId, categoryId);
        assertEquals(0, parentCount);
    }

    @Then("the Watchlist {string} default movie list contains {string}")
    public void defaultListContains(String watchlistName, String imdbId) {
        assertTrue(defaultMovieIds(watchlistName).contains(imdbId));
    }

    @Then("the Watchlist {string} default movie list does not contain {string}")
    public void defaultListDoesNotContain(String watchlistName, String imdbId) {
        assertFalse(defaultMovieIds(watchlistName).contains(imdbId));
    }

    @Then("the Watchlist {string} scoped to private category {string} contains movie {string}")
    public void scopedListContains(String watchlistName, String categoryName, String imdbId) {
        WatchlistDto watchlist = watchlistsByName.get(watchlistName);
        long categoryId = privateCategoriesByName.get(categoryName);
        List<MovieDto> movies = watchlists.watchlistMovies(watchlist.id(), List.of(categoryId), 1, 50, null, null,
                watchlist.owner(), false).movies();
        assertTrue(movies.stream().anyMatch(movie -> movie.imdbId().equals(imdbId)));
    }

    @Then("the private category {string} no longer exists")
    public void privateCategoryNoLongerExists(String categoryName) {
        long id = privateCategoriesByName.get(categoryName);
        Boolean exists = jdbc.queryForObject("select exists(select 1 from private_category where id=?)", Boolean.class, id);
        assertFalse(Boolean.TRUE.equals(exists));
    }

    @Then("the {string} response contains the Watchlist {string} but not the Watchlist {string}")
    public void mineContainsButNot(String responseName, String includedWatchlist, String excludedWatchlist) throws Exception {
        String body = restApi.responseBody();
        assertTrue(body.contains("\"id\":" + watchlistsByName.get(includedWatchlist).id()));
        assertFalse(body.contains("\"id\":" + watchlistsByName.get(excludedWatchlist).id()));
    }

    private List<String> defaultMovieIds(String watchlistName) {
        WatchlistDto watchlist = watchlistsByName.get(watchlistName);
        return watchlists.watchlistMovies(watchlist.id(), List.of(), 1, 50, null, null, watchlist.owner(), false)
                .movies().stream().map(MovieDto::imdbId).toList();
    }

    private long categoryIdByName(String name) {
        return jdbc.queryForObject("select id from category where name=?", Long.class, name);
    }

    private long privateParentOf(long id) {
        return jdbc.queryForObject(
                "select parent_id from private_category_parent_child where child_id=? and parent_id<>? limit 1",
                Long.class, id, id);
    }
}
