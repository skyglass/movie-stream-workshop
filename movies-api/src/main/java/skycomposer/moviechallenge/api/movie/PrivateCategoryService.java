package skycomposer.moviechallenge.api.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import skycomposer.moviechallenge.api.movie.dto.CategoryDto;
import skycomposer.moviechallenge.api.movie.dto.MoveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveCategoryRequest;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Private counterpart to CategoryService, operating on private_category/private_category_parent_child/
// private_category_parent_child_all instead of the public category tables. There is no copy-link/reference
// concept here (unlike Guide's movie_guide_default_category) -- "subscribing" a watchlist to a public category is
// a plain pointer row in movie_watchlist_default_category, handled entirely by WatchlistService, never touching
// this tree. Every read/write here is ownership-scoped: a private category is only ever reachable through the
// user_movie_watchlist that anchors it, and only its owner (or MOVIES_ADMIN) may see or touch it.
@RequiredArgsConstructor
@Service
public class PrivateCategoryService {
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    private record Row(long id, String name, String description, String icon, long parentId, boolean leaf) {}

    @Transactional(readOnly = true)
    public List<CategoryDto> tree() {
        List<Row> rows = jdbc.sql("""
                select c.id, c.name, c.description, c.icon, direct.parent_id,
                    not exists(select 1 from private_category_parent_child child
                        where child.parent_id=c.id and child.child_id<>c.id) leaf
                from private_category c
                join private_category_parent_child direct on direct.child_id=c.id
                order by lower(c.name), c.id
                """).query((rs, n) -> new Row(
                rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getString("icon"),
                rs.getLong("parent_id"), rs.getBoolean("leaf"))).list();

        Map<Long, List<Row>> children = new HashMap<>();
        rows.stream().filter(row -> row.parentId() != row.id())
                .forEach(row -> children.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row));
        return rows.stream().filter(row -> row.parentId() == row.id()).map(row -> toDto(row, children)).toList();
    }

    // Direct children of a watchlist's own anchor category -- backs the 'watchlist' picker mode's private-subtree
    // half, same role as CategoryService.subtree() plays for a Movie Guide.
    @Transactional(readOnly = true)
    public List<CategoryDto> subtree(long rootCategoryId, String username, boolean isAdmin) {
        requireManage(rootCategoryId, username, isAdmin);
        CategoryDto root = flatten(tree()).stream().filter(category -> category.id() == rootCategoryId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        return root.children();
    }

    @Transactional
    public CategoryDto create(SaveCategoryRequest request, String username, boolean isAdmin) {
        if (request.parentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A private category must have a parent");
        }
        requireManage(request.parentId(), username, isAdmin);
        validateParent(request.parentId(), null);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", request.name().trim());
        values.put("description", text(request.description()));
        values.put("icon", text(request.icon()));
        Number key = new SimpleJdbcInsert(jdbcTemplate).withTableName("private_category")
                .usingGeneratedKeyColumns("id").executeAndReturnKey(values);
        long id = key.longValue();
        jdbc.sql("insert into private_category_parent_child(parent_id,child_id) values (:parent,:id)")
                .param("parent", request.parentId()).param("id", id).update();
        rebuildClosure();
        return findInTree(id);
    }

    @Transactional
    public CategoryDto update(long id, SaveCategoryRequest request, String username, boolean isAdmin) {
        requireCategory(id);
        requireManage(id, username, isAdmin);
        jdbc.sql("update private_category set name=:name, description=:description, icon=:icon where id=:id")
                .param("name", request.name().trim()).param("description", text(request.description()))
                .param("icon", text(request.icon())).param("id", id).update();
        return findInTree(id);
    }

    @Transactional
    public CategoryDto move(long id, MoveCategoryRequest request, String username, boolean isAdmin) {
        requireCategory(id);
        requireManage(id, username, isAdmin);
        if (request.targetParentId() != null) requireManage(request.targetParentId(), username, isAdmin);
        validateParent(request.targetParentId(), id);
        long target = request.targetParentId() == null ? id : request.targetParentId();
        jdbc.sql("delete from private_category_parent_child where parent_id=:parent and child_id=:id")
                .param("parent", request.sourceParentId()).param("id", id).update();
        jdbc.sql("insert into private_category_parent_child(parent_id,child_id) values (:parent,:id) on conflict do nothing")
                .param("parent", target).param("id", id).update();
        rebuildClosure();
        return findInTree(id);
    }

    // Used both for deleting a sub-category (from the 'watchlist' picker dialog) and, via WatchlistService, for
    // deleting a whole watchlist (deleting its anchor row cascades to user_movie_watchlist and everything under
    // it) -- exactly the same single primitive CategoryService.delete() plays for Guide.
    @Transactional
    public void delete(long id, long parentId, String username, boolean isAdmin) {
        requireCategory(id);
        requireManage(id, username, isAdmin);
        deleteSubtree(id, parentId);
        rebuildClosure();
    }

    Long parentOf(long id) {
        return jdbc.sql("select parent_id from private_category_parent_child where child_id=:id and parent_id<>:id")
                .param("id", id).query(Long.class).optional().orElse(null);
    }

    private CategoryDto findInTree(long id) {
        return flatten(tree()).stream().filter(category -> category.id() == id).findFirst()
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
                row.parentId() == row.id() ? null : row.parentId(), false, row.leaf(), false, null, false, nested);
    }

    private void validateParent(Long parentId, Long categoryId) {
        if (parentId == null) return;
        requireCategory(parentId);
        if (categoryId != null && parentId.equals(categoryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A category cannot be its own parent");
        }
        if (categoryId != null) {
            boolean cycle = jdbc.sql("select exists(select 1 from private_category_parent_child_all where ancestor_id=:category and descendant_id=:parent)")
                    .param("category", categoryId).param("parent", parentId).query(Boolean.class).single();
            if (cycle) throw new ResponseStatusException(HttpStatus.CONFLICT, "Category hierarchy cannot contain a cycle");
        }
    }

    private void rebuildClosure() {
        jdbc.sql("delete from private_category_parent_child_all").update();
        jdbc.sql("insert into private_category_parent_child_all(ancestor_id,descendant_id) select id,id from private_category").update();
        jdbc.sql("""
                insert into private_category_parent_child_all(ancestor_id,descendant_id)
                select parent_id,child_id from private_category_parent_child where parent_id<>child_id
                """).update();
        int inserted;
        do {
            inserted = jdbc.sql("""
                    insert into private_category_parent_child_all(ancestor_id,descendant_id)
                    select distinct ancestor.ancestor_id, descendant.descendant_id
                    from private_category_parent_child_all ancestor
                    join private_category_parent_child_all descendant on descendant.ancestor_id=ancestor.descendant_id
                    where ancestor.ancestor_id<>descendant.descendant_id
                      and not exists(select 1 from private_category_parent_child_all existing
                          where existing.ancestor_id=ancestor.ancestor_id
                            and existing.descendant_id=descendant.descendant_id)
                    """).update();
        } while (inserted > 0);
    }

    private void requireCategory(long id) {
        if (!jdbc.sql("select exists(select 1 from private_category where id=:id)").param("id", id).query(Boolean.class).single())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
    }

    private String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    // A plain user may only manage private categories inside a watchlist they own (resolved via ancestry through
    // private_category_parent_child_all) -- the same shape as CategoryService.requireManage(), just rooted in
    // user_movie_watchlist instead of movie_guide.
    void requireManage(long categoryId, String username, boolean isAdmin) {
        if (isAdmin) return;
        boolean owns = jdbc.sql("""
                select exists(
                    select 1 from user_movie_watchlist w
                    join private_category_parent_child_all a on a.ancestor_id=w.category_id and a.descendant_id=:category
                    where w.owner=:username)
                """).param("category", categoryId).param("username", username).query(Boolean.class).single();
        if (!owns) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted to manage this category");
    }

    // Cascade-deletes id and its native descendants -- simpler than CategoryService.deleteSubtree() since the
    // private tree has no multi-parent/reference-link cases to preserve (a watchlist's private categories are
    // never reachable through any other parent edge).
    private void deleteSubtree(long id, long parentId) {
        jdbc.sql("delete from private_category_parent_child where parent_id=:parent and child_id=:id")
                .param("parent", parentId).param("id", id).update();
        boolean stillReachable = jdbc.sql("select exists(select 1 from private_category_parent_child where child_id=:id)")
                .param("id", id).query(Boolean.class).single();
        if (stillReachable) return;
        List<Long> subtree = jdbc.sql("select descendant_id from private_category_parent_child_all where ancestor_id=:id")
                .param("id", id).query(Long.class).list();
        Set<Long> destroyable = new HashSet<>(subtree);
        jdbc.sql("delete from movie_private_category where private_category_id in (:ids)").param("ids", destroyable).update();
        jdbc.sql("delete from private_category where id in (:ids)").param("ids", destroyable).update();
    }
}
