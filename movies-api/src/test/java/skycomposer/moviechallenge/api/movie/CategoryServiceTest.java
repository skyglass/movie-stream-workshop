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
        assertThatThrownBy(() -> categories.move(root.id(), new MoveCategoryRequest(root.id(), leaf.id(), false), "guide", true))
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
    void deletingACategoryCascadesToItsNativeChildren() {
        var root = categories.create(request("Genres", "🎭", null), "guide", true);
        var child = categories.create(request("Drama", "🎬", root.id()), "guide", true);
        categories.create(request("Courtroom", "⚖️", child.id()), "guide", true);

        categories.delete(child.id(), root.id(), "guide", true);

        assertThat(findById(categories.tree(null), root.id())).isNotNull().extracting(CategoryDto::children).isEqualTo(List.of());
    }

    @Test
    void copyingACategoryAddsASecondParentWithoutDuplicatingIt() {
        movies.saveMovie("tt-category-copy", "Copied Movie");
        var genres = categories.create(request("Genres", "🎭", null), "guide", true);
        var drama = categories.create(request("Drama", "🎬", genres.id()), "guide", true);
        categories.saveMovieCategories("tt-category-copy", new SaveMovieCategoriesRequest(List.of(drama.id()), List.of()));
        var guides = categories.create(request("Guides", "🗺️", null), "guide", true);

        categories.move(drama.id(), new MoveCategoryRequest(genres.id(), guides.id(), true), "guide", true);

        assertThat(closureExists(genres.id(), drama.id())).isTrue();
        assertThat(closureExists(guides.id(), drama.id())).isTrue();
        var underGuides = categories.tree(null).stream().filter(c -> c.id() == guides.id()).findFirst().orElseThrow();
        assertThat(underGuides.children()).extracting("id").containsExactly(drama.id());
        var underGenres = categories.tree(null).stream().filter(c -> c.id() == genres.id()).findFirst().orElseThrow();
        assertThat(underGenres.children()).extracting("id").containsExactly(drama.id());
    }

    @Test
    void deletingACategoryThroughAnUntrackedCopyLinkPreservesItsIndependentRootStatus() {
        var sharedRoot = categories.create(request("Shared Root", "📌", null), "guide", true);
        var otherRoot = categories.create(request("Other Root", "🗂️", null), "guide", true);

        // A plain Copy (not guide-tracked -- otherRoot isn't any guide's anchor) gives sharedRoot a second,
        // real parent edge alongside its own self-referencing root edge.
        categories.move(sharedRoot.id(), new MoveCategoryRequest(sharedRoot.id(), otherRoot.id(), true), "guide", true);
        assertThat(closureExists(otherRoot.id(), sharedRoot.id())).isTrue();
        assertThat(closureExists(sharedRoot.id(), sharedRoot.id())).isTrue();

        // Deleting sharedRoot *through the otherRoot edge specifically* must only remove that one edge --
        // sharedRoot's own root identity (the self-edge) is a completely separate, independent parent and must
        // survive, exactly like it would for any other multi-parent node.
        categories.delete(sharedRoot.id(), otherRoot.id(), "guide", true);

        assertThat(closureExists(sharedRoot.id(), sharedRoot.id())).isTrue();
        assertThat(categories.tree(null)).extracting("id").contains(sharedRoot.id());
        assertThat(closureExists(otherRoot.id(), sharedRoot.id())).isFalse();
    }

    @Test
    void deletingAGuideAnchorNeverDestroysARootItOnlyCopyReferences() {
        var referenceRoot = categories.create(request("Reference Root", "📌", null), "guide", true);
        var guideAnchor = categories.create(request("Guide Anchor", "🗺️", null), "guide", true);

        // Simulates a guide subscribing to an existing top-level category: referenceRoot is now reachable both as
        // its own independent root and as a descendant of guideAnchor.
        categories.move(referenceRoot.id(), new MoveCategoryRequest(referenceRoot.id(), guideAnchor.id(), true), "guide", true);
        assertThat(closureExists(guideAnchor.id(), referenceRoot.id())).isTrue();
        assertThat(closureExists(referenceRoot.id(), referenceRoot.id())).isTrue();

        // Deleting the guide's own anchor must destroy only the anchor -- referenceRoot must survive intact as
        // its own root, not get swept up by the cascade just because it was also reachable underneath.
        categories.delete(guideAnchor.id(), guideAnchor.id(), "guide", true);

        assertThat(categories.tree(null)).extracting("id").contains(referenceRoot.id());
        assertThat(closureExists(referenceRoot.id(), referenceRoot.id())).isTrue();
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

    private SaveCategoryRequest request(String name, String icon, Long parent) {
        return new SaveCategoryRequest(name, name + " description", icon, parent);
    }
}
