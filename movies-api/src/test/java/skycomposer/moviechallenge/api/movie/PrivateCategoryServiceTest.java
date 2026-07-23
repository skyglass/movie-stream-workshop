package skycomposer.moviechallenge.api.movie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import skycomposer.moviechallenge.api.bdd.CucumberFixtureConfiguration;
import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.movie.dto.AssignWatchlistMoviesRequest;
import skycomposer.moviechallenge.api.movie.dto.CategoryDto;
import skycomposer.moviechallenge.api.movie.dto.CreateWatchlistRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveMovieCategoriesRequest;
import skycomposer.moviechallenge.api.movie.dto.WatchlistDto;
import skycomposer.moviechallenge.api.movie.model.Operator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Mirrors CategoryServiceTest's compositionCategoryMatchesEveryComponentAndRemainsALeaf() coverage for the
// private/watchlist side (PrivateCategoryService + WatchlistService.assignMovies), the counterpart added once
// private categories gained composition support (V50__private_composition_categories.sql).
@Transactional
@Import(CucumberFixtureConfiguration.class)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/movies"
})
class PrivateCategoryServiceTest {
    @Autowired PrivateCategoryService privateCategories;
    @Autowired CategoryService categories;
    @Autowired WatchlistService watchlists;
    @Autowired JdbcClient jdbc;
    @Autowired MovieCatalogFixture movies;

    @Test
    void compositionCategoryMatchesEveryComponentAndRemainsALeaf() {
        movies.saveMovie("tt-private-composition-match", "Matches private composition");
        movies.saveMovie("tt-private-composition-missing", "Missing private component");

        WatchlistDto watchlist = watchlists.createWatchlist(
                new CreateWatchlistRequest("Styles Watchlist", null, null, List.of()), "alice");
        var realism = privateCategories.create(request("Realism", "🎭", watchlist.categoryId()), "alice", false);
        var humanism = privateCategories.create(request("Humanism", "🤝", watchlist.categoryId()), "alice", false);
        var composition = privateCategories.create(
                composeRequest("Humanistic Realism", watchlist.categoryId(), List.of(realism.id(), humanism.id()), List.of(), Operator.AND),
                "alice", false);

        watchlists.assignMovies(watchlist.id(),
                new AssignWatchlistMoviesRequest(List.of("tt-private-composition-match"), realism.id()), "alice", false);
        watchlists.assignMovies(watchlist.id(),
                new AssignWatchlistMoviesRequest(List.of("tt-private-composition-missing"), realism.id()), "alice", false);
        assertThat(matches(composition.id(), "tt-private-composition-match")).isFalse();
        assertThat(matches(composition.id(), "tt-private-composition-missing")).isFalse();

        // Assigning straight to the composition fans out to every component automatically.
        watchlists.assignMovies(watchlist.id(),
                new AssignWatchlistMoviesRequest(List.of("tt-private-composition-match"), composition.id()), "alice", false);
        assertThat(matches(composition.id(), "tt-private-composition-match")).isTrue();
        assertThat(matches(composition.id(), "tt-private-composition-missing")).isFalse();

        var compositionDto = findById(privateCategories.tree(), composition.id());
        assertThat(compositionDto).isNotNull();
        assertThat(compositionDto.operator()).isEqualTo(Operator.AND);
        assertThat(compositionDto.leaf()).isTrue();
        assertThat(compositionDto.components()).extracting("id").containsExactlyInAnyOrder(realism.id(), humanism.id());

        assertThatThrownBy(() -> privateCategories.create(request("Not allowed", "📁", composition.id()), "alice", false))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("cannot have children");
    }

    @Test
    void rejectsAnotherUsersPrivateCategoryAsAComponent() {
        WatchlistDto aliceWatchlist = watchlists.createWatchlist(
                new CreateWatchlistRequest("Alice Watchlist", null, null, List.of()), "alice");
        WatchlistDto bobWatchlist = watchlists.createWatchlist(
                new CreateWatchlistRequest("Bob Watchlist", null, null, List.of()), "bob");
        var aliceCategory = privateCategories.create(request("Alice Genre", "🎬", aliceWatchlist.categoryId()), "alice", false);
        var bobCategory = privateCategories.create(request("Bob Genre", "🎬", bobWatchlist.categoryId()), "bob", false);

        assertThatThrownBy(() -> privateCategories.create(
                composeRequest("Cross-owner composition", aliceWatchlist.categoryId(),
                        List.of(aliceCategory.id(), bobCategory.id()), List.of(), Operator.AND), "alice", false))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Not permitted to manage");
    }

    @Test
    void directMovieAssignmentToACompositionIsRejectedAtTheDatabaseLevel() {
        movies.saveMovie("tt-private-composition-direct", "Direct assignment attempt");
        WatchlistDto watchlist = watchlists.createWatchlist(
                new CreateWatchlistRequest("Direct Assignment Watchlist", null, null, List.of()), "alice");
        var component = privateCategories.create(request("Component", "🎬", watchlist.categoryId()), "alice", false);
        var composition = privateCategories.create(
                composeRequest("Composition", watchlist.categoryId(), List.of(component.id()), List.of(), Operator.AND),
                "alice", false);

        // Bypasses the service (which always fans a composition assignment out to its components) to confirm the
        // database trigger itself also rejects a raw direct assignment, as a backstop invariant.
        assertThatThrownBy(() -> jdbc.sql(
                "insert into movie_private_category(movie_id,private_category_id) values (:movie,:category)")
                .param("movie", "tt-private-composition-direct").param("category", composition.id()).update())
                .hasMessageContaining("movies cannot be assigned directly to a composition category");
    }

    @Test
    void subscriptionCategoryMatchesAnyComponentAndRemainsALeaf() {
        movies.saveMovie("tt-private-subscription-match", "Matches private subscription");
        movies.saveMovie("tt-private-subscription-missing", "Missing every private component");
        WatchlistDto watchlist = watchlists.createWatchlist(
                new CreateWatchlistRequest("Buzzy Watchlist", null, null, List.of()), "alice");
        var newReleases = privateCategories.create(request("New Releases", "🆕", watchlist.categoryId()), "alice", false);
        var awardsSeason = privateCategories.create(request("Awards Season", "🏆", watchlist.categoryId()), "alice", false);
        var subscription = privateCategories.create(
                composeRequest("Buzzy", watchlist.categoryId(), List.of(newReleases.id(), awardsSeason.id()), List.of(), Operator.OR),
                "alice", false);

        watchlists.assignMovies(watchlist.id(),
                new AssignWatchlistMoviesRequest(List.of("tt-private-subscription-match"), newReleases.id()), "alice", false);

        assertThat(matches(subscription.id(), "tt-private-subscription-match")).isTrue();
        assertThat(matches(subscription.id(), "tt-private-subscription-missing")).isFalse();
        var subscriptionDto = findById(privateCategories.tree(), subscription.id());
        assertThat(subscriptionDto).isNotNull();
        assertThat(subscriptionDto.operator()).isEqualTo(Operator.OR);
        assertThat(subscriptionDto.leaf()).isTrue();
    }

    @Test
    void compositionCanIncludeAPublicCategoryAsAComponentAndFollowsItsPublicAssignments() {
        movies.saveMovie("tt-public-component-match", "Matches via public component");
        var publicGenre = categories.create(
                new SaveCategoryRequest("Public Genre", "Public Genre description", "🎬", null), "guide", true);
        WatchlistDto watchlist = watchlists.createWatchlist(
                new CreateWatchlistRequest("Public Follow Watchlist", null, null, List.of()), "alice");
        var subscription = privateCategories.create(
                composeRequest("Follows Public Genre", watchlist.categoryId(), List.of(), List.of(publicGenre.id()), Operator.OR),
                "alice", false);

        var subscriptionDto = findById(privateCategories.tree(), subscription.id());
        assertThat(subscriptionDto).isNotNull();
        assertThat(subscriptionDto.components()).hasSize(1);
        assertThat(subscriptionDto.components().get(0).id()).isEqualTo(publicGenre.id());
        assertThat(subscriptionDto.components().get(0).isPublic()).isTrue();

        // Membership of a public component is entirely derived from the shared public catalog -- assigning the
        // movie there (never touching the watchlist at all) is enough for the private match to pick it up.
        categories.saveMovieCategories("tt-public-component-match",
                new SaveMovieCategoriesRequest(List.of(publicGenre.id()), List.of()));
        assertThat(matches(subscription.id(), "tt-public-component-match")).isTrue();
    }

    @Test
    void editingAComponentToCreateACycleIsRejected() {
        WatchlistDto watchlist = watchlists.createWatchlist(
                new CreateWatchlistRequest("Cycle Watchlist", null, null, List.of()), "alice");
        var base = privateCategories.create(request("Base", "📁", watchlist.categoryId()), "alice", false);
        var first = privateCategories.create(
                composeRequest("First", watchlist.categoryId(), List.of(base.id()), List.of(), Operator.AND), "alice", false);
        var second = privateCategories.create(
                composeRequest("Second", watchlist.categoryId(), List.of(first.id()), List.of(), Operator.AND), "alice", false);

        // Editing "first" to depend on "second" would close a cycle: first -> second -> first.
        assertThatThrownBy(() -> privateCategories.update(first.id(),
                composeRequest("First", watchlist.categoryId(), List.of(second.id()), List.of(), Operator.AND), "alice", false))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("circular dependency");
    }

    @Test
    void deletingASoleComponentCascadesToDeleteTheNowEmptyComposition() {
        WatchlistDto watchlist = watchlists.createWatchlist(
                new CreateWatchlistRequest("Cascade Watchlist", null, null, List.of()), "alice");
        var onlyComponent = privateCategories.create(request("Only Component", "📁", watchlist.categoryId()), "alice", false);
        var composition = privateCategories.create(
                composeRequest("Solo", watchlist.categoryId(), List.of(onlyComponent.id()), List.of(), Operator.AND), "alice", false);

        privateCategories.delete(onlyComponent.id(), watchlist.categoryId(), "alice", false);

        assertThat(findById(privateCategories.tree(), composition.id())).isNull();
    }

    private CategoryDto findById(List<CategoryDto> tree, long id) {
        for (CategoryDto category : tree) {
            if (category.id() == id) return category;
            CategoryDto found = findById(category.children(), id);
            if (found != null) return found;
        }
        return null;
    }

    private boolean matches(long categoryId, String movieId) {
        return jdbc.sql("select exists(select 1 from private_category_movie_match where private_category_id=:category and movie_id=:movie)")
                .param("category", categoryId).param("movie", movieId).query(Boolean.class).single();
    }

    private SaveCategoryRequest request(String name, String icon, Long parent) {
        return new SaveCategoryRequest(name, name + " description", icon, parent);
    }

    private SaveCategoryRequest composeRequest(String name, Long parent, List<Long> componentIds, List<Long> publicComponentIds, Operator operator) {
        String icon = operator == Operator.OR ? "🔔" : "🧩";
        return new SaveCategoryRequest(name, name + " description", icon, parent, componentIds, publicComponentIds, operator);
    }
}
