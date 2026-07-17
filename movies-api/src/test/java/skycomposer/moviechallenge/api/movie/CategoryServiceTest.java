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
import skycomposer.moviechallenge.api.movie.dto.SaveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveMovieCategoriesRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import(CucumberFixtureConfiguration.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:category_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
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
        var root = categories.create(request("Genres", "🎭", null));
        var child = categories.create(request("Drama", "🎬", root.id()));
        var leaf = categories.create(request("Courtroom", "⚖️", child.id()));

        assertThat(closureExists(root.id(), leaf.id())).isTrue();
        assertThatThrownBy(() -> categories.update(root.id(), request("Genres", "🎭", leaf.id())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("cycle");

        var tree = categories.saveMovieCategories("tt-category",
                new SaveMovieCategoriesRequest(List.of(leaf.id()), List.of()));
        assertThat(tree.getFirst().children().getFirst().children().getFirst().checked()).isTrue();
        assertThatThrownBy(() -> categories.delete(leaf.id()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("without movies");
    }

    @Test
    void allowsAssigningMoviesToNonLeafCategoriesAndAddingChildrenLater() {
        movies.saveMovie("tt-category-2", "Category Movie 2");
        var root = categories.create(request("Genres", "🎭", null));
        var child = categories.create(request("Drama", "🎬", root.id()));

        var tree = categories.saveMovieCategories("tt-category-2",
                new SaveMovieCategoriesRequest(List.of(child.id()), List.of()));
        var childDto = tree.getFirst().children().getFirst();
        assertThat(childDto.checked()).isTrue();
        assertThat(childDto.leaf()).isTrue();

        var leaf = categories.create(request("Courtroom", "⚖️", child.id()));
        var reloaded = categories.tree(null).getFirst().children().getFirst();
        assertThat(reloaded.leaf()).isFalse();
        assertThat(reloaded.children()).extracting("id").containsExactly(leaf.id());
    }

    private boolean closureExists(long ancestor, long descendant) {
        return jdbc.sql("select exists(select 1 from category_parent_child_all where ancestor_id=:a and descendant_id=:d)")
                .param("a", ancestor).param("d", descendant).query(Boolean.class).single();
    }

    private SaveCategoryRequest request(String name, String icon, Long parent) {
        return new SaveCategoryRequest(name, name + " description", icon, parent);
    }
}
