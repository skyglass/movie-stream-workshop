package skycomposer.moviechallenge.api.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import skycomposer.moviechallenge.api.movie.dto.CategoryDto;
import skycomposer.moviechallenge.api.movie.dto.RecommendMovieRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveMovieCategoriesRequest;
import skycomposer.moviechallenge.api.movie.model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.sql.Types;

@RequiredArgsConstructor
@Service
public class CategoryService {
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final MovieService movieService;
    private record Row(long id, String name, String description, String icon, long parentId,
                       boolean checked, boolean leaf, boolean empty) {}

    @Transactional(readOnly = true)
    public List<CategoryDto> tree(String movieId) {
        List<Row> rows = jdbc.sql("""
                select c.id, c.name, c.description, c.icon, direct.parent_id,
                    case when :movie is null then false else exists(
                        select 1 from movie_category mc where mc.movie_id=:movie and mc.category_id=c.id) end checked,
                    not exists(select 1 from category_parent_child child
                        where child.parent_id=c.id and child.child_id<>c.id) leaf,
                    not exists(select 1 from movie_category mc where mc.category_id=c.id)
                        and not exists(select 1 from category_parent_child child
                            where child.parent_id=c.id and child.child_id<>c.id) empty
                from category c
                join category_parent_child direct on direct.child_id=c.id
                order by lower(c.name), c.id
                """).param("movie", movieId, Types.VARCHAR).query((rs, n) -> new Row(
                rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getString("icon"),
                rs.getLong("parent_id"), rs.getBoolean("checked"), rs.getBoolean("leaf"), rs.getBoolean("empty"))).list();

        Map<Long, List<Row>> children = new HashMap<>();
        rows.stream().filter(row -> row.parentId() != row.id())
                .forEach(row -> children.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row));
        return rows.stream().filter(row -> row.parentId() == row.id()).map(row -> toDto(row, children)).toList();
    }

    @Transactional
    public CategoryDto create(SaveCategoryRequest request) {
        validateParent(request.parentId(), null);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", request.name().trim());
        values.put("description", text(request.description()));
        values.put("icon", text(request.icon()));
        Number key = new SimpleJdbcInsert(jdbcTemplate).withTableName("category")
                .usingGeneratedKeyColumns("id").executeAndReturnKey(values);
        long id = key.longValue();
        setParent(id, request.parentId());
        rebuildClosure();
        return findInTree(id);
    }

    @Transactional
    public CategoryDto update(long id, SaveCategoryRequest request) {
        requireCategory(id);
        validateParent(request.parentId(), id);
        jdbc.sql("update category set name=:name, description=:description, icon=:icon where id=:id")
                .param("name", request.name().trim()).param("description", text(request.description()))
                .param("icon", text(request.icon())).param("id", id).update();
        setParent(id, request.parentId());
        rebuildClosure();
        return findInTree(id);
    }

    @Transactional
    public void delete(long id) {
        requireCategory(id);
        boolean hasMovies = jdbc.sql("select count(*) from movie_category where category_id=:id")
                .param("id", id).query(Integer.class).single() > 0;
        boolean hasChildren = jdbc.sql("select count(*) from category_parent_child where parent_id=:id and child_id<>:id")
                .param("id", id).query(Integer.class).single() > 0;
        if (hasMovies || hasChildren) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only categories without movies or sub-categories can be deleted");
        }
        jdbc.sql("delete from category where id=:id").param("id", id).update();
        rebuildClosure();
    }

    @Transactional
    public List<CategoryDto> saveMovieCategories(String movieId, SaveMovieCategoriesRequest request) {
        requireMovie(movieId);
        List<Long> added = request.addedCategories().stream().distinct().toList();
        List<Long> removed = request.removedCategories().stream().distinct().toList();
        if (added.stream().anyMatch(removed::contains)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A category cannot be both added and removed");
        }
        added.forEach(this::requireCategory);
        removed.forEach(this::requireCategory);
        for (Long categoryId : removed) {
            jdbc.sql("delete from movie_category where movie_id=:movie and category_id=:category")
                    .param("movie", movieId).param("category", categoryId).update();
        }
        for (Long categoryId : added) {
            try {
                jdbc.sql("insert into movie_category(movie_id,category_id) values (:movie,:category)")
                        .param("movie", movieId).param("category", categoryId).update();
            } catch (DuplicateKeyException ignored) {
                // Idempotent submission.
            }
        }
        return tree(movieId);
    }

    @Transactional
    public void addMovieFromSearchToCategory(long categoryId, RecommendMovieRequest movieRequest) {
        requireCategory(categoryId);
        Movie movie = movieService.getOrCreateMovie(movieRequest);
        try {
            jdbc.sql("insert into movie_category(movie_id,category_id) values (:movie,:category)")
                    .param("movie", movie.getImdbId()).param("category", categoryId).update();
        } catch (DuplicateKeyException ignored) {
            // Idempotent submission.
        }
    }

    private CategoryDto findInTree(long id) {
        return flatten(tree(null)).stream().filter(category -> category.id() == id).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    private List<CategoryDto> flatten(List<CategoryDto> categories) {
        List<CategoryDto> result = new ArrayList<>();
        for (CategoryDto category : categories) {
            result.add(category);
            result.addAll(flatten(category.children()));
        }
        return result;
    }

    private CategoryDto toDto(Row row, Map<Long, List<Row>> children) {
        List<CategoryDto> nested = children.getOrDefault(row.id(), List.of()).stream()
                .map(child -> toDto(child, children)).toList();
        return new CategoryDto(row.id(), row.name(), row.description(), row.icon(),
                row.parentId() == row.id() ? null : row.parentId(), row.checked(), row.leaf(), row.empty(), nested);
    }

    private void validateParent(Long parentId, Long categoryId) {
        if (parentId == null) return;
        requireCategory(parentId);
        if (categoryId != null && parentId.equals(categoryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a null parent for a root category");
        }
        if (categoryId != null) {
            boolean cycle = jdbc.sql("select exists(select 1 from category_parent_child_all where ancestor_id=:category and descendant_id=:parent)")
                    .param("category", categoryId).param("parent", parentId).query(Boolean.class).single();
            if (cycle) throw new ResponseStatusException(HttpStatus.CONFLICT, "Category hierarchy cannot contain a cycle");
        }
    }

    private void setParent(long id, Long parentId) {
        jdbc.sql("delete from category_parent_child where child_id=:id").param("id", id).update();
        jdbc.sql("insert into category_parent_child(parent_id,child_id) values (:parent,:id)")
                .param("parent", parentId == null ? id : parentId).param("id", id).update();
    }

    private void rebuildClosure() {
        jdbc.sql("delete from category_parent_child_all").update();
        jdbc.sql("insert into category_parent_child_all(ancestor_id,descendant_id) select id,id from category").update();
        jdbc.sql("""
                insert into category_parent_child_all(ancestor_id,descendant_id)
                select parent_id,child_id from category_parent_child where parent_id<>child_id
                """).update();
        int inserted;
        do {
            inserted = jdbc.sql("""
                    insert into category_parent_child_all(ancestor_id,descendant_id)
                    select distinct ancestor.ancestor_id, descendant.descendant_id
                    from category_parent_child_all ancestor
                    join category_parent_child_all descendant on descendant.ancestor_id=ancestor.descendant_id
                    where ancestor.ancestor_id<>descendant.descendant_id
                      and not exists(select 1 from category_parent_child_all existing
                          where existing.ancestor_id=ancestor.ancestor_id
                            and existing.descendant_id=descendant.descendant_id)
                    """).update();
        } while (inserted > 0);
    }

    private void requireCategory(long id) {
        if (!jdbc.sql("select exists(select 1 from category where id=:id)").param("id", id).query(Boolean.class).single())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
    }

    private void requireMovie(String movieId) {
        if (!jdbc.sql("select exists(select 1 from movies where imdb_id=:id)").param("id", movieId).query(Boolean.class).single())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found");
    }

    private String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
