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
import skycomposer.moviechallenge.api.movie.dto.CategoryDto;
import skycomposer.moviechallenge.api.movie.dto.MoveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveMovieCategoriesRequest;
import skycomposer.moviechallenge.api.movie.model.Operator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Datasource comes from PostgresTestcontainerConfiguration (a real PostgreSQL container, picked up automatically
// via component scanning) -- CategoryService's move()/delete() rely on genuine Postgres transaction-abort
// semantics that H2 doesn't replicate.
@Transactional
@Import(CucumberFixtureConfiguration.class)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/movies"
})
class CategoryServiceTest {
    @Autowired CategoryService categories;
    @Autowired JdbcClient jdbc;
    @Autowired MovieCatalogFixture movies;

    @Test
    void maintainsClosureAndRejectsCycles() {
        movies.saveMovie("tt-category", "Category Movie");
        var root = categories.create(request("Genres", "🎭", null), "guide", true);
        var child = categories.create(request("Drama", "🎬", root.id()), "guide", true);
        var leaf = categories.create(request("Courtroom", "⚖️", child.id()), "guide", true);

        assertThat(closureExists(root.id(), leaf.id())).isTrue();
        assertThatThrownBy(() -> categories.move(root.id(), new MoveCategoryRequest(root.id(), leaf.id()), "guide", true))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("cycle");

        // Looked up by id, not list position -- movies.saveMovie() below triggers automatic Director/Writer/Genre
        // category creation (V35's sync_movie_categories trigger), so the top-level category list isn't just
        // whatever this test itself created.
        var tree = categories.saveMovieCategories("tt-category",
                new SaveMovieCategoriesRequest(List.of(leaf.id()), List.of()));
        assertThat(findById(tree, leaf.id())).isNotNull().extracting(CategoryDto::checked).isEqualTo(true);

        // Deleting a leaf is now allowed even though it still has a movie assigned to it.
        categories.delete(leaf.id(), child.id(), "guide", true);
        assertThat(closureExists(root.id(), leaf.id())).isFalse();
    }

    @Test
    void allowsAssigningMoviesToNonLeafCategoriesAndAddingChildrenLater() {
        movies.saveMovie("tt-category-2", "Category Movie 2");
        var root = categories.create(request("Genres", "🎭", null), "guide", true);
        var child = categories.create(request("Drama", "🎬", root.id()), "guide", true);

        var tree = categories.saveMovieCategories("tt-category-2",
                new SaveMovieCategoriesRequest(List.of(child.id()), List.of()));
        var childDto = findById(tree, child.id());
        assertThat(childDto).isNotNull();
        assertThat(childDto.checked()).isTrue();
        assertThat(childDto.leaf()).isTrue();

        var leaf = categories.create(request("Courtroom", "⚖️", child.id()), "guide", true);
        var reloaded = findById(categories.tree(null), child.id());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.leaf()).isFalse();
        assertThat(reloaded.children()).extracting("id").containsExactly(leaf.id());
    }

    @Test
    void compositionCategoryMatchesEveryComponentAndRemainsALeaf() {
        movies.saveMovie("tt-composition-match", "Matches composition");
        movies.saveMovie("tt-composition-missing", "Missing component");
        var root = categories.create(request("Styles", "🎞️", null), "guide", true);
        var realism = categories.create(request("Realism", "🎭", root.id()), "guide", true);
        var humanism = categories.create(request("Humanism", "🤝", root.id()), "guide", true);
        var socialRealism = categories.create(request("Social Realism", "🎬", realism.id()), "guide", true);
        var composition = categories.create(composeRequest("Humanistic Realism", root.id(),
                List.of(realism.id(), humanism.id()), Operator.AND), "guide", true);

        // A component retains normal descendant semantics: Social Realism satisfies Realism.
        categories.saveMovieCategories("tt-composition-match",
                new SaveMovieCategoriesRequest(List.of(socialRealism.id(), humanism.id()), List.of()));
        categories.saveMovieCategories("tt-composition-missing",
                new SaveMovieCategoriesRequest(List.of(socialRealism.id()), List.of()));

        assertThat(matches(composition.id(), "tt-composition-match")).isTrue();
        assertThat(matches(composition.id(), "tt-composition-missing")).isFalse();
        var compositionDto = findById(categories.tree(null), composition.id());
        assertThat(compositionDto.operator()).isEqualTo(Operator.AND);
        assertThat(compositionDto.leaf()).isTrue();
        assertThat(compositionDto.components()).extracting("id").containsExactlyInAnyOrder(realism.id(), humanism.id());
        assertThatThrownBy(() -> categories.create(request("Not allowed", "📁", composition.id()), "guide", true))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("cannot have children");
        // Checking a composition creates its missing component links. Removing any required component makes the
        // dynamic AND match disappear immediately.
        categories.saveMovieCategories("tt-composition-missing",
                new SaveMovieCategoriesRequest(List.of(composition.id()), List.of()));
        assertThat(matches(composition.id(), "tt-composition-missing")).isTrue();
        categories.saveMovieCategories("tt-composition-missing",
                new SaveMovieCategoriesRequest(List.of(), List.of(humanism.id())));
        assertThat(matches(composition.id(), "tt-composition-missing")).isFalse();
    }

    // Regression: a movie filed only under a plain leaf must never make that leaf's ANCESTORS (e.g. a "Genres"
    // root) show up as matched/checked -- that was the "Edit Categories" bug where root categories appeared
    // checked (and unchecking them was a no-op) purely because some descendant had a movie. A plain category's
    // own match must reflect direct assignment only; descendant semantics apply only when a category is used AS
    // a composition/subscription component (covered by compositionCategoryMatchesEveryComponentAndRemainsALeaf).
    @Test
    void plainCategoryMatchNeverLeaksToItsAncestors() {
        movies.saveMovie("tt-ancestor-leak", "Ancestor leak regression");
        var root = categories.create(request("Genres", "🎭", null), "guide", true);
        var child = categories.create(request("Drama", "🎬", root.id()), "guide", true);
        var leaf = categories.create(request("Courtroom", "⚖️", child.id()), "guide", true);

        categories.saveMovieCategories("tt-ancestor-leak",
                new SaveMovieCategoriesRequest(List.of(leaf.id()), List.of()));

        assertThat(matches(leaf.id(), "tt-ancestor-leak")).isTrue();
        assertThat(matches(child.id(), "tt-ancestor-leak")).isFalse();
        assertThat(matches(root.id(), "tt-ancestor-leak")).isFalse();
        var tree = categories.tree("tt-ancestor-leak");
        assertThat(findById(tree, leaf.id())).extracting(CategoryDto::checked).isEqualTo(true);
        assertThat(findById(tree, child.id())).extracting(CategoryDto::checked).isEqualTo(false);
        assertThat(findById(tree, root.id())).extracting(CategoryDto::checked).isEqualTo(false);
    }

    @Test
    void subscriptionCategoryMatchesAnyComponentAndRemainsALeaf() {
        movies.saveMovie("tt-subscription-match", "Matches subscription");
        movies.saveMovie("tt-subscription-missing", "Missing every component");
        var root = categories.create(request("Collections", "🗂️", null), "guide", true);
        var newReleases = categories.create(request("New Releases", "🆕", root.id()), "guide", true);
        var awardsSeason = categories.create(request("Awards Season", "🏆", root.id()), "guide", true);
        var subscription = categories.create(composeRequest("Buzzy", root.id(),
                List.of(newReleases.id(), awardsSeason.id()), Operator.OR), "guide", true);

        categories.saveMovieCategories("tt-subscription-match",
                new SaveMovieCategoriesRequest(List.of(newReleases.id()), List.of()));

        // A single matching component is enough for OR, unlike the AND case above.
        assertThat(matches(subscription.id(), "tt-subscription-match")).isTrue();
        assertThat(matches(subscription.id(), "tt-subscription-missing")).isFalse();
        var subscriptionDto = findById(categories.tree(null), subscription.id());
        assertThat(subscriptionDto.operator()).isEqualTo(Operator.OR);
        assertThat(subscriptionDto.leaf()).isTrue();
        assertThat(subscriptionDto.components()).extracting("id").containsExactlyInAnyOrder(newReleases.id(), awardsSeason.id());
    }

    @Test
    void nestedCompositionResolvesThroughAnotherComposableComponent() {
        movies.saveMovie("tt-nested-match", "Matches nested AND-of-OR");
        var root = categories.create(request("Vibes", "✨", null), "guide", true);
        var cozy = categories.create(request("Cozy", "🛋️", root.id()), "guide", true);
        var wintry = categories.create(request("Wintry", "❄️", root.id()), "guide", true);
        var seasonal = categories.create(composeRequest("Holiday Vibes", root.id(), List.of(cozy.id(), wintry.id()), Operator.OR), "guide", true);
        var uplifting = categories.create(request("Uplifting", "😊", root.id()), "guide", true);
        var nested = categories.create(composeRequest("Feel-Good Holiday", root.id(), List.of(seasonal.id(), uplifting.id()), Operator.AND), "guide", true);

        // Satisfies "seasonal" (OR) only via cozy, and separately satisfies "uplifting" -- the AND-of-OR match
        // must resolve through seasonal's own (already fixed-point-resolved) match status, not just plain leaves.
        categories.saveMovieCategories("tt-nested-match",
                new SaveMovieCategoriesRequest(List.of(cozy.id(), uplifting.id()), List.of()));

        assertThat(matches(seasonal.id(), "tt-nested-match")).isTrue();
        assertThat(matches(nested.id(), "tt-nested-match")).isTrue();
    }

    @Test
    void editingAComponentToCreateACycleIsRejected() {
        var root = categories.create(request("Concepts", "🧠", null), "guide", true);
        var base = categories.create(request("Base", "📁", root.id()), "guide", true);
        var first = categories.create(composeRequest("First", root.id(), List.of(base.id()), Operator.AND), "guide", true);
        var second = categories.create(composeRequest("Second", root.id(), List.of(first.id()), Operator.AND), "guide", true);

        // Editing "first" to depend on "second" would close a cycle: first -> second -> first.
        assertThatThrownBy(() -> categories.update(first.id(),
                composeRequest("First", root.id(), List.of(second.id()), Operator.AND), "guide", true))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("circular dependency");
    }

    @Test
    void deletingASoleComponentCascadesToDeleteTheNowEmptyComposition() {
        var root = categories.create(request("Concepts", "🧠", null), "guide", true);
        var onlyComponent = categories.create(request("Only Component", "📁", root.id()), "guide", true);
        var composition = categories.create(composeRequest("Solo", root.id(), List.of(onlyComponent.id()), Operator.AND), "guide", true);

        categories.delete(onlyComponent.id(), root.id(), "guide", true);

        assertThat(findById(categories.tree(null), composition.id())).isNull();
    }

    @Test
    void deletingASoleComponentCascadesTransitivelyThroughNestedCompositions() {
        var root = categories.create(request("Concepts", "🧠", null), "guide", true);
        var leaf = categories.create(request("Leaf", "📁", root.id()), "guide", true);
        var inner = categories.create(composeRequest("Inner", root.id(), List.of(leaf.id()), Operator.AND), "guide", true);
        var outer = categories.create(composeRequest("Outer", root.id(), List.of(inner.id()), Operator.AND), "guide", true);

        // Deleting the only leaf empties "inner", whose own deletion (via the cascade trigger) then empties
        // "outer" too -- a chain of dependents resolving to a fixed point with no application-level recursion.
        categories.delete(leaf.id(), root.id(), "guide", true);

        var tree = categories.tree(null);
        assertThat(findById(tree, inner.id())).isNull();
        assertThat(findById(tree, outer.id())).isNull();
    }

    @Test
    void deletingACategoryCascadesToItsNativeChildren() {
        var root = categories.create(request("Genres", "🎭", null), "guide", true);
        var child = categories.create(request("Drama", "🎬", root.id()), "guide", true);
        categories.create(request("Courtroom", "⚖️", child.id()), "guide", true);

        categories.delete(child.id(), root.id(), "guide", true);

        assertThat(findById(categories.tree(null), root.id())).isNotNull().extracting(CategoryDto::children).isEqualTo(List.of());
    }

    private CategoryDto findById(List<CategoryDto> tree, long id) {
        for (CategoryDto category : tree) {
            if (category.id() == id) return category;
            CategoryDto found = findById(category.children(), id);
            if (found != null) return found;
        }
        return null;
    }

    private boolean closureExists(long ancestor, long descendant) {
        return jdbc.sql("select exists(select 1 from category_parent_child_all where ancestor_id=:a and descendant_id=:d)")
                .param("a", ancestor).param("d", descendant).query(Boolean.class).single();
    }

    private boolean matches(long categoryId, String movieId) {
        return jdbc.sql("select exists(select 1 from category_movie_match where category_id=:category and movie_id=:movie)")
                .param("category", categoryId).param("movie", movieId).query(Boolean.class).single();
    }

    private SaveCategoryRequest request(String name, String icon, Long parent) {
        return new SaveCategoryRequest(name, name + " description", icon, parent);
    }

    private SaveCategoryRequest composeRequest(String name, Long parent, List<Long> componentIds, Operator operator) {
        String icon = operator == Operator.OR ? "🔔" : "🧩";
        return new SaveCategoryRequest(name, name + " description", icon, parent, componentIds, List.of(), operator);
    }
}
