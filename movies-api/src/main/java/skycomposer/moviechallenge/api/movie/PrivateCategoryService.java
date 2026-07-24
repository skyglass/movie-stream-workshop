package skycomposer.moviechallenge.api.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import skycomposer.moviechallenge.api.movie.dto.CategoryComponentDto;
import skycomposer.moviechallenge.api.movie.dto.CategoryDto;
import skycomposer.moviechallenge.api.movie.dto.MoveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveCategoryRequest;
import skycomposer.moviechallenge.api.movie.model.Operator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Private counterpart to CategoryService, operating on private_category/private_category_parent_child/
// private_category_parent_child_all instead of the public category tables. Every read/write here is
// ownership-scoped: a private category is only ever reachable through the user_movie_watchlist that anchors it,
// and only its owner (or MOVIES_ADMIN) may see or touch it. A private composition/subscription category can
// combine both private (watchlist-owned) components and existing PUBLIC categories as components -- the latter
// lets a watchlist "follow" a shared public category, same as the old flat "Subscribe to Categories" pointer did.
@RequiredArgsConstructor
@Service
public class PrivateCategoryService {
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    private record Row(long id, String name, String description, String icon, long parentId, boolean leaf,
                       Integer operator) {}

    @Transactional(readOnly = true)
    public List<CategoryDto> tree() {
        List<Row> rows = jdbc.sql("""
                select c.id, c.name, c.description, c.icon, direct.parent_id,
                    not exists(select 1 from private_category_parent_child child
                        where child.parent_id=c.id and child.child_id<>c.id) leaf,
                    cc.operator operator
                from private_category c
                join private_category_parent_child direct on direct.child_id=c.id
                left join private_composition_category cc on cc.private_category_id=c.id
                order by lower(c.name), c.id
                """).query((rs, n) -> new Row(
                rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getString("icon"),
                rs.getLong("parent_id"), rs.getBoolean("leaf"), (Integer) rs.getObject("operator"))).list();

        Map<Long, Row> byId = new HashMap<>();
        rows.forEach(row -> byId.put(row.id(), row));
        Map<Long, List<CategoryComponentDto>> components = new HashMap<>();

        jdbc.sql("""
                select composition_category_id, component_category_id
                from private_composition_category_component
                where component_category_id is not null
                order by composition_category_id, component_category_id
                """).query((rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)}).list()
                .forEach(pair -> {
                    Row component = byId.get(pair[1]);
                    if (component != null) components.computeIfAbsent(pair[0], ignored -> new ArrayList<>())
                            .add(new CategoryComponentDto(component.id(), component.name(), component.icon(), false));
                });

        List<long[]> publicPairs = jdbc.sql("""
                select composition_category_id, public_component_category_id
                from private_composition_category_component
                where public_component_category_id is not null
                order by composition_category_id, public_component_category_id
                """).query((rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)}).list();
        if (!publicPairs.isEmpty()) {
            Set<Long> publicIds = new HashSet<>();
            publicPairs.forEach(pair -> publicIds.add(pair[1]));
            Map<Long, CategoryComponentDto> publicById = new HashMap<>();
            jdbc.sql("select id, name, icon from category where id in (:ids)").param("ids", publicIds)
                    .query((rs, n) -> new CategoryComponentDto(rs.getLong("id"), rs.getString("name"), rs.getString("icon"), true))
                    .list().forEach(dto -> publicById.put(dto.id(), dto));
            publicPairs.forEach(pair -> {
                CategoryComponentDto component = publicById.get(pair[1]);
                if (component != null) components.computeIfAbsent(pair[0], ignored -> new ArrayList<>()).add(component);
            });
        }

        Map<Long, List<Row>> children = new HashMap<>();
        rows.stream().filter(row -> row.parentId() != row.id())
                .forEach(row -> children.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row));
        return rows.stream().filter(row -> row.parentId() == row.id()).map(row -> toDto(row, children, components)).toList();
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
        List<Long> privateComponents = distinct(request.componentCategoryIds());
        List<Long> publicComponents = distinct(request.publicComponentCategoryIds());
        boolean hasComponents = !privateComponents.isEmpty() || !publicComponents.isEmpty();
        Operator operator = request.operator();
        if (hasComponents) {
            if (operator == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A composition/subscription category needs an operator");
            validateCompositionComponents(null, privateComponents, publicComponents, username, isAdmin);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", request.name().trim());
        values.put("description", text(request.description()));
        values.put("icon", text(request.icon()));
        Number key = new SimpleJdbcInsert(jdbcTemplate).withTableName("private_category")
                .usingGeneratedKeyColumns("id").executeAndReturnKey(values);
        long id = key.longValue();
        jdbc.sql("insert into private_category_parent_child(parent_id,child_id) values (:parent,:id)")
                .param("parent", request.parentId()).param("id", id).update();
        if (hasComponents) {
            jdbc.sql("insert into private_composition_category(private_category_id, operator) values (:id, :operator)")
                    .param("id", id).param("operator", operator.getCode()).update();
            insertComponents(id, privateComponents, publicComponents);
        }
        rebuildClosure();
        return findInTree(id);
    }

    @Transactional
    public CategoryDto update(long id, SaveCategoryRequest request, String username, boolean isAdmin) {
        requireCategory(id);
        requireManage(id, username, isAdmin);
        Operator existingOperator = compositionOperator(id);
        boolean sendsComponents = request.componentCategoryIds() != null || request.publicComponentCategoryIds() != null;
        List<Long> privateComponents = distinct(request.componentCategoryIds());
        List<Long> publicComponents = distinct(request.publicComponentCategoryIds());
        if (existingOperator == null && sendsComponents && (!privateComponents.isEmpty() || !publicComponents.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only a composition category can have components");
        }
        if (existingOperator != null && sendsComponents) {
            validateCompositionComponents(id, privateComponents, publicComponents, username, isAdmin);
            Operator newOperator = request.operator() != null ? request.operator() : existingOperator;
            jdbc.sql("update private_composition_category set operator=:operator where private_category_id=:id")
                    .param("operator", newOperator.getCode()).param("id", id).update();
            // Insert the desired components before removing the stale ones: cascade_delete_empty_private_
            // compositions_trigger (V55) deletes a composition/subscription category outright the moment it has
            // zero components, so a delete-all-then-reinsert here would trip that same cleanup on the category
            // still being edited.
            insertComponents(id, privateComponents, publicComponents);
            if (privateComponents.isEmpty()) {
                jdbc.sql("""
                        delete from private_composition_category_component
                        where composition_category_id=:id and component_category_id is not null
                        """).param("id", id).update();
            } else {
                jdbc.sql("""
                        delete from private_composition_category_component
                        where composition_category_id=:id and component_category_id is not null
                          and component_category_id not in (:components)
                        """).param("id", id).param("components", privateComponents).update();
            }
            if (publicComponents.isEmpty()) {
                jdbc.sql("""
                        delete from private_composition_category_component
                        where composition_category_id=:id and public_component_category_id is not null
                        """).param("id", id).update();
            } else {
                jdbc.sql("""
                        delete from private_composition_category_component
                        where composition_category_id=:id and public_component_category_id is not null
                          and public_component_category_id not in (:components)
                        """).param("id", id).param("components", publicComponents).update();
            }
        }
        jdbc.sql("update private_category set name=:name, description=:description, icon=:icon where id=:id")
                .param("name", request.name().trim()).param("description", text(request.description()))
                .param("icon", text(request.icon())).param("id", id).update();
        return findInTree(id);
    }

    private void insertComponents(long compositionId, List<Long> privateComponents, List<Long> publicComponents) {
        for (Long component : privateComponents) {
            jdbc.sql("""
                    insert into private_composition_category_component(composition_category_id, component_category_id)
                    values (:composition, :component) on conflict do nothing
                    """).param("composition", compositionId).param("component", component).update();
        }
        for (Long component : publicComponents) {
            jdbc.sql("""
                    insert into private_composition_category_component(composition_category_id, public_component_category_id)
                    values (:composition, :component) on conflict do nothing
                    """).param("composition", compositionId).param("component", component).update();
        }
    }

    private List<Long> distinct(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().distinct().toList();
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

    // Reused wherever a movie is filed into one private category (WatchlistService's own assignment/CSV-import
    // paths) -- a composable category is assigned through its PRIVATE components, recursively for a nested one,
    // never by a direct movie_private_category row for itself (the DB wouldn't accept that anyway). A PUBLIC
    // component contributes nothing to write -- its membership is entirely derived from the shared public catalog
    // (via private_category_movie_match's join to the public category_movie_match) and can't be changed from a
    // private, watchlist-scoped action; assigning to a composition whose components are ALL public is a no-op.
    void assignMovieToCategory(String movieId, long categoryId) {
        requireCategory(categoryId);
        if (isCompositionCategory(categoryId)) {
            for (Long componentId : privateComponentIds(categoryId)) {
                assignMovieToCategory(movieId, componentId);
            }
        } else {
            jdbc.sql("insert into movie_private_category(movie_id,private_category_id) values (:movie,:category) on conflict do nothing")
                    .param("movie", movieId).param("category", categoryId).update();
        }
    }

    private boolean isCompositionCategory(long categoryId) {
        return jdbc.sql("select exists(select 1 from private_composition_category where private_category_id=:id)")
                .param("id", categoryId).query(Boolean.class).single();
    }

    private List<Long> privateComponentIds(long compositionCategoryId) {
        return jdbc.sql("""
                select component_category_id from private_composition_category_component
                where composition_category_id=:composition and component_category_id is not null
                """).param("composition", compositionCategoryId).query(Long.class).list();
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

    private CategoryDto toDto(Row row, Map<Long, List<Row>> children, Map<Long, List<CategoryComponentDto>> components) {
        List<CategoryDto> nested = children.getOrDefault(row.id(), List.of()).stream()
                .map(child -> toDto(child, children, components)).toList();
        Operator operator = row.operator() == null ? null : Operator.fromCode(row.operator());
        return new CategoryDto(row.id(), row.name(), row.description(), row.icon(),
                row.parentId() == row.id() ? null : row.parentId(), false, row.leaf(), false, nested,
                operator, components.getOrDefault(row.id(), List.of()));
    }

    // compositionId is null on create, the editingId on update -- see CategoryService.validateCompositionComponents
    // for the worked example of why the cycle check only matters (and is only run) on the edit path.
    private void validateCompositionComponents(Long compositionId, List<Long> privateComponents, List<Long> publicComponents,
                                                String username, boolean isAdmin) {
        if (privateComponents.isEmpty() && publicComponents.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A composition category needs at least one component");
        }
        for (Long component : privateComponents) {
            requireCategory(component);
            // Private categories are per-watchlist -- a component must belong to a watchlist the caller actually
            // owns (or the caller is an admin), otherwise a client could pass another user's private category id.
            requireManage(component, username, isAdmin);
            if (compositionId != null && wouldCreateCycle(compositionId, component)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This component would create a circular dependency");
            }
        }
        for (Long component : publicComponents) {
            requirePublicCategory(component);
            // No cycle check needed: a public category can never depend on a private one, so a private
            // composition can never reach itself back through a public component.
        }
    }

    // Iterative (no recursive CTE, for portability), walking only the private component graph -- a public
    // component is always a safe, terminal node from this private-side check's perspective (see above).
    private boolean wouldCreateCycle(long compositionId, long candidateComponentId) {
        Set<Long> visited = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        pending.push(candidateComponentId);
        while (!pending.isEmpty()) {
            long current = pending.pop();
            if (current == compositionId) return true;
            if (!visited.add(current)) continue;
            jdbc.sql("""
                    select component_category_id from private_composition_category_component
                    where composition_category_id=:id and component_category_id is not null
                    """).param("id", current).query(Long.class).list().forEach(pending::push);
        }
        return false;
    }

    private Operator compositionOperator(long categoryId) {
        Integer code = jdbc.sql("select operator from private_composition_category where private_category_id=:id")
                .param("id", categoryId).query(Integer.class).optional().orElse(null);
        return code == null ? null : Operator.fromCode(code);
    }

    private void validateParent(Long parentId, Long categoryId) {
        if (parentId == null) return;
        requireCategory(parentId);
        boolean compositionParent = jdbc.sql("select exists(select 1 from private_composition_category where private_category_id=:id)")
                .param("id", parentId).query(Boolean.class).single();
        if (compositionParent) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A composition category cannot have children");
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

    private void requirePublicCategory(long id) {
        if (!jdbc.sql("select exists(select 1 from category where id=:id)").param("id", id).query(Boolean.class).single())
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
    // private tree has no multi-parent cases to preserve (a watchlist's private categories are never reachable
    // through any other parent edge).
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
