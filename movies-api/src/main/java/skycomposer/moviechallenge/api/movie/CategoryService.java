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
import skycomposer.moviechallenge.api.movie.dto.CategoryComponentDto;
import skycomposer.moviechallenge.api.movie.dto.MoveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.RecommendMovieRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveMovieCategoriesRequest;
import skycomposer.moviechallenge.api.movie.model.Movie;
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
import java.sql.Types;

@RequiredArgsConstructor
@Service
public class CategoryService {
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final MovieService movieService;
    private record Row(long id, String name, String description, String icon, long parentId,
                       boolean checked, boolean leaf, boolean empty, Integer operator) {}

    @Transactional(readOnly = true)
    public List<CategoryDto> tree(String movieId) {
        List<Row> rows = jdbc.sql("""
                select c.id, c.name, c.description, c.icon, direct.parent_id,
                    case when :movie is null then false else exists(
                        select 1 from category_movie_match match where match.movie_id=:movie and match.category_id=c.id) end checked,
                    not exists(select 1 from category_parent_child child
                        where child.parent_id=c.id and child.child_id<>c.id) leaf,
                    not exists(select 1 from movie_category mc where mc.category_id=c.id)
                        and not exists(select 1 from category_parent_child child
                            where child.parent_id=c.id and child.child_id<>c.id) empty,
                    cc.operator operator
                from category c
                join category_parent_child direct on direct.child_id=c.id
                left join composition_category cc on cc.category_id=c.id
                order by lower(c.name), c.id
                """).param("movie", movieId, Types.VARCHAR).query((rs, n) -> new Row(
                rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getString("icon"),
                rs.getLong("parent_id"), rs.getBoolean("checked"), rs.getBoolean("leaf"), rs.getBoolean("empty"),
                (Integer) rs.getObject("operator"))).list();

        Map<Long, Row> byId = new HashMap<>();
        rows.forEach(row -> byId.put(row.id(), row));
        Map<Long, List<CategoryComponentDto>> components = new HashMap<>();
        jdbc.sql("""
                select composition_category_id, component_category_id
                from composition_category_component
                order by composition_category_id, component_category_id
                """).query((rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)}).list()
                .forEach(pair -> {
                    Row component = byId.get(pair[1]);
                    if (component != null) components.computeIfAbsent(pair[0], ignored -> new ArrayList<>())
                            .add(new CategoryComponentDto(component.id(), component.name(), component.icon(), true));
                });

        Map<Long, List<Row>> children = new HashMap<>();
        rows.stream().filter(row -> row.parentId() != row.id())
                .forEach(row -> children.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row));
        return rows.stream().filter(row -> row.parentId() == row.id()).map(row -> toDto(row, children, components)).toList();
    }

    // The direct children of one category (e.g. a Movie Guide's own anchor category) -- backs a guide's own,
    // sandboxed category picker.
    @Transactional(readOnly = true)
    public List<CategoryDto> subtree(long rootCategoryId, List<Long> excludeCategoryIds) {
        CategoryDto root = flatten(tree(null)).stream().filter(category -> category.id() == rootCategoryId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        Set<Long> excluded = new HashSet<>(excludeCategoryIds);
        return root.children().stream().filter(category -> !excluded.contains(category.id())).toList();
    }

    @Transactional
    public CategoryDto create(SaveCategoryRequest request, String username, boolean adminOrGuide) {
        if (request.parentId() == null) {
            requireAdminOrGuide(adminOrGuide);
        } else {
            requireManage(request.parentId(), username, adminOrGuide);
        }
        validateParent(request.parentId(), null);
        List<Long> components = request.componentCategoryIds() == null ? List.of()
                : request.componentCategoryIds().stream().distinct().toList();
        Operator operator = request.operator();
        if (!components.isEmpty()) {
            if (operator == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A composition/subscription category needs an operator");
            validateCompositionComponents(null, components);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", request.name().trim());
        values.put("description", text(request.description()));
        values.put("icon", text(request.icon()));
        Number key = new SimpleJdbcInsert(jdbcTemplate).withTableName("category")
                .usingGeneratedKeyColumns("id").executeAndReturnKey(values);
        long id = key.longValue();
        setParent(id, request.parentId());
        if (!components.isEmpty()) {
            jdbc.sql("insert into composition_category(category_id, operator) values (:id, :operator)")
                    .param("id", id).param("operator", operator.getCode()).update();
            for (Long component : components) {
                jdbc.sql("""
                        insert into composition_category_component(composition_category_id, component_category_id)
                        values (:composition, :component)
                        """).param("composition", id).param("component", component).update();
            }
        }
        rebuildClosure();
        return findInTree(id);
    }

    @Transactional
    public CategoryDto update(long id, SaveCategoryRequest request, String username, boolean adminOrGuide) {
        requireCategory(id);
        requireManage(id, username, adminOrGuide);
        Operator existingOperator = compositionOperator(id);
        if (existingOperator == null && request.componentCategoryIds() != null && !request.componentCategoryIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only a composition category can have components");
        }
        if (existingOperator != null && request.componentCategoryIds() != null) {
            List<Long> components = request.componentCategoryIds().stream().distinct().toList();
            validateCompositionComponents(id, components);
            Operator newOperator = request.operator() != null ? request.operator() : existingOperator;
            jdbc.sql("update composition_category set operator=:operator where category_id=:id")
                    .param("operator", newOperator.getCode()).param("id", id).update();
            jdbc.sql("delete from composition_category_component where composition_category_id=:id")
                    .param("id", id).update();
            for (Long component : components) {
                jdbc.sql("""
                        insert into composition_category_component(composition_category_id, component_category_id)
                        values (:composition, :component)
                        """).param("composition", id).param("component", component).update();
            }
        }
        String trimmedName = request.name().trim();
        syncGuideNameIfAnchor(id, trimmedName);
        jdbc.sql("update category set name=:name, description=:description, icon=:icon where id=:id")
                .param("name", trimmedName).param("description", text(request.description()))
                .param("icon", text(request.icon())).param("id", id).update();
        return findInTree(id);
    }

    // A guide/personality's own anchor category and movie_guide.name are two independent columns that only start
    // out equal (set together once, at creation -- MovieGuideService.createGuide). Nothing kept them in sync
    // after that: this "Edit" path (the only rename UI that exists) was updating the category only, silently
    // leaving movie_guide.name stale -- which broke anything keyed off the guide's own name, e.g.
    // MovieGuideService.submitRanking's synthetic-username slugification. Locks the two together going forward,
    // and if this personality already has a synthetic ranking user, renames that user's username to match the
    // new name too (renameRankingUsername below) -- otherwise a rename here would silently leave the old,
    // now-wrong-looking username (e.g. "robert-de-niro-suggestions" after renaming to "Robert De Niro") stuck
    // forever, since MovieGuideService.submitRanking only ever allocates a username once and reuses it.
    // Enforces movie_guide.name's own global case-insensitive uniqueness (uq_movie_guide_name), which plain
    // category names aren't otherwise subject to.
    private void syncGuideNameIfAnchor(long categoryId, String newName) {
        Long guideId = jdbc.sql("select id from movie_guide where category_id=:categoryId")
                .param("categoryId", categoryId).query(Long.class).optional().orElse(null);
        if (guideId == null) return;
        boolean nameTaken = jdbc.sql("select exists(select 1 from movie_guide where lower(name)=lower(:name) and id<>:id)")
                .param("name", newName).param("id", guideId).query(Boolean.class).single();
        if (nameTaken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Movie Guide with the name \"" + newName + "\" already Exists");
        }
        String previousRankingUsername = jdbc.sql("select ranking_username from movie_guide where id=:id")
                .param("id", guideId).query(String.class).optional().orElse(null);
        jdbc.sql("update movie_guide set name=:name where id=:id").param("name", newName).param("id", guideId).update();
        if (previousRankingUsername != null) {
            renameRankingUsername(guideId, previousRankingUsername, newName);
        }
    }

    // users.username is a plain (non-cascading) primary key -- none of the FKs referencing it declare "on update
    // cascade" -- so renaming it can't be a single UPDATE; every referencing row has to be repointed to a new
    // users row before the old one can be dropped, same as any manual primary-key rename.
    private void renameRankingUsername(long guideId, String previousUsername, String newGuideName) {
        String newUsername = allocatePersonaUsernameSlug(guideId, newGuideName, previousUsername);
        if (newUsername.equals(previousUsername)) return;
        jdbc.sql("insert into users(username, email, avatar) values (:username, :email, :username)")
                .param("username", newUsername).param("email", newUsername + "@skycomposer.net").update();
        jdbc.sql("update user_settings set username=:new where username=:old")
                .param("new", newUsername).param("old", previousUsername).update();
        jdbc.sql("update movie_recommendations set user_id=:new where user_id=:old")
                .param("new", newUsername).param("old", previousUsername).update();
        jdbc.sql("update user_movie_rank set user_id=:new where user_id=:old")
                .param("new", newUsername).param("old", previousUsername).update();
        jdbc.sql("update user_movie_challenge set user_id=:new where user_id=:old")
                .param("new", newUsername).param("old", previousUsername).update();
        jdbc.sql("update user_movie_challenge_vote set user_id=:new where user_id=:old")
                .param("new", newUsername).param("old", previousUsername).update();
        jdbc.sql("update movie_guide set ranking_username=:new where id=:id")
                .param("new", newUsername).param("id", guideId).update();
        jdbc.sql("delete from users where username=:old").param("old", previousUsername).update();
    }

    // Same slug + collision-disambiguation rule as MovieGuideService.resolveRankingUsername, adapted to exclude
    // this guide's own current (about-to-be-renamed-away) username from the collision check.
    private String allocatePersonaUsernameSlug(long guideId, String name, String excludingUsername) {
        String base = PersonaUsernames.slugify(name);
        if (base.isBlank()) base = "personality-" + guideId;
        if (!personaUsernameTaken(base, excludingUsername)) return base;
        String disambiguated = base + "-" + guideId;
        String candidate = disambiguated;
        int suffix = 1;
        while (personaUsernameTaken(candidate, excludingUsername)) {
            suffix++;
            candidate = disambiguated + "-" + suffix;
        }
        return candidate;
    }

    private boolean personaUsernameTaken(String candidate, String excludingUsername) {
        if (candidate.equals(excludingUsername)) return false;
        return jdbc.sql("select exists(select 1 from users where username=:candidate)")
                .param("candidate", candidate).query(Boolean.class).single();
    }

    @Transactional
    public CategoryDto move(long id, MoveCategoryRequest request, String username, boolean adminOrGuide) {
        requireCategory(id);
        requireManage(id, username, adminOrGuide);
        validateParent(request.targetParentId(), id);
        long target = request.targetParentId() == null ? id : request.targetParentId();
        jdbc.sql("delete from category_parent_child where parent_id=:parent and child_id=:id")
                .param("parent", request.sourceParentId()).param("id", id).update();
        jdbc.sql("insert into category_parent_child(parent_id,child_id) values (:parent,:id) on conflict do nothing")
                .param("parent", target).param("id", id).update();
        rebuildClosure();
        return findInTree(id);
    }

    @Transactional
    public void delete(long id, long parentId, String username, boolean adminOrGuide) {
        requireCategory(id);
        requireManage(id, username, adminOrGuide);
        disablePersonalityRankingPublicAccess(id);
        deleteSubtree(id, parentId);
        rebuildClosure();
    }

    // If this category is a Movie Personality's own anchor with a synthetic ranking user (see
    // MovieGuideService.submitRanking), deactivate that user's public favorite/recommended pages before the
    // guide (and its personality_movie_rank rows, via FK cascade) disappear. Surgical: never hard-deletes the
    // users row -- just flips both public flags off, so /my-favorite-movies/{username} and
    // /my-recommended-movies/{username} 404 again via the existing public-view guard, rather than serving a
    // frozen, orphaned snapshot forever.
    private void disablePersonalityRankingPublicAccess(long categoryId) {
        jdbc.sql("""
                update user_settings set is_my_favorite_movies_public=false, is_my_recommended_movies_public=false
                where username = (
                    select ranking_username from movie_guide
                    where category_id=:categoryId and ranking_username is not null
                )
                """).param("categoryId", categoryId).update();
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
            // A composition/subscription has derived membership, not an assignment of its own. Its components
            // are the only mutable facts, so an old/stale client trying to uncheck one is intentionally a no-op.
            if (!isCompositionCategory(categoryId)) removeDirectMovieCategory(movieId, categoryId);
        }
        for (Long categoryId : added) {
            if (isCompositionCategory(categoryId)) assignComposition(movieId, categoryId);
            else assignDirectMovieCategory(movieId, categoryId);
        }
        return tree(movieId);
    }

    // Removes a movie from every category within these scope ids' transitive subtrees (each scope id itself,
    // plus all its descendants) -- e.g. the "Delete Movies" dialog passes the guide's own category (or a picked
    // sub-category) as scope, and this reaches the movie regardless of which exact descendant category it's
    // actually filed under, matching how the movie list itself is matched via the same transitive closure.
    @Transactional
    public void removeMovieFromCategorySubtree(String movieId, List<Long> scopeCategoryIds, List<Long> protectedSubtreeCategoryIds) {
        requireMovie(movieId);
        if (scopeCategoryIds.isEmpty()) return;
        if (protectedSubtreeCategoryIds == null || protectedSubtreeCategoryIds.isEmpty()) {
            jdbc.sql("""
                    delete from movie_category
                    where movie_id=:movie
                      and category_id in (select descendant_id from category_parent_child_all where ancestor_id in (:scope))
                    """).param("movie", movieId).param("scope", scopeCategoryIds).update();
            return;
        }
        jdbc.sql("""
                delete from movie_category
                where movie_id=:movie
                  and category_id in (
                      select descendant_id from category_parent_child_all where ancestor_id in (:scope)
                      except
                      select descendant_id from category_parent_child_all where ancestor_id in (:protected)
                  )
                """).param("movie", movieId).param("scope", scopeCategoryIds).param("protected", protectedSubtreeCategoryIds).update();
    }

    @Transactional
    public void addMovieFromSearchToCategory(long categoryId, RecommendMovieRequest movieRequest) {
        requireCategory(categoryId);
        Movie movie = movieService.getOrCreateMovie(movieRequest);
        assignMovieToCategory(movie.getImdbId(), categoryId);
    }

    // Bulk-assigns already-cataloged movies (picked via the Movie Selector, not an OMDb search) to one category in
    // a single call -- backs the "Add Movies" action on the category browsing page, mirroring
    // MovieGuideService.assignMovies's bulk-insert shape but for an arbitrary (non-guide-anchored) category.
    @Transactional
    public void addMoviesToCategory(long categoryId, List<String> imdbIds) {
        requireCategory(categoryId);
        List<String> distinct = imdbIds.stream().distinct().toList();
        distinct.forEach(this::requireMovie);
        for (String imdbId : distinct) {
            assignMovieToCategory(imdbId, categoryId);
        }
    }

    // Reused by Guide/CSV assignment paths: a composition/subscription is assigned through its components, never
    // by a direct movie_category row for itself.
    public void assignMovieToCategory(String movieId, long categoryId) {
        requireCategory(categoryId);
        if (isCompositionCategory(categoryId)) assignComposition(movieId, categoryId);
        else assignDirectMovieCategory(movieId, categoryId);
    }

    // Recursive: a component that's itself composable is fanned out into ITS OWN components rather than written
    // as a direct movie_category row (which the DB wouldn't accept anyway -- composition_category_cannot_receive_
    // movies rejects it). Safe from infinite recursion because the component graph is acyclic by construction
    // (validateCompositionComponents rejects any edit that would create a cycle).
    private void assignComposition(String movieId, long compositionCategoryId) {
        for (Long componentId : compositionComponentIds(compositionCategoryId)) {
            if (isCompositionCategory(componentId)) assignComposition(movieId, componentId);
            else assignDirectMovieCategory(movieId, componentId);
        }
    }

    private void assignDirectMovieCategory(String movieId, long categoryId) {
        jdbc.sql("insert into movie_category(movie_id,category_id) values (:movie,:category) on conflict do nothing")
                .param("movie", movieId).param("category", categoryId).update();
    }

    private void removeDirectMovieCategory(String movieId, long categoryId) {
        jdbc.sql("delete from movie_category where movie_id=:movie and category_id=:category")
                .param("movie", movieId).param("category", categoryId).update();
    }

    private List<Long> compositionComponentIds(long compositionCategoryId) {
        return jdbc.sql("""
                select component_category_id from composition_category_component where composition_category_id=:composition
                """).param("composition", compositionCategoryId).query(Long.class).list();
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

    private CategoryDto toDto(Row row, Map<Long, List<Row>> children,
                              Map<Long, List<CategoryComponentDto>> components) {
        return toDto(row, children, components, new HashSet<>());
    }

    // Write-time checks (validateParent's cycle check) should make a genuine cycle in category_parent_child
    // impossible, but this walk is the one place a cycle would turn into infinite recursion / a StackOverflow
    // instead of a clean error, so it defends itself too: `onPath` tracks the current root-to-node path (not a
    // global visited set -- the same category legitimately appears in multiple branches under the DAG, that's
    // not a cycle) and a repeat within that one path is where a real cycle would show up.
    private CategoryDto toDto(Row row, Map<Long, List<Row>> children,
                              Map<Long, List<CategoryComponentDto>> components, Set<Long> onPath) {
        Operator operator = row.operator() == null ? null : Operator.fromCode(row.operator());
        if (!onPath.add(row.id())) {
            return new CategoryDto(row.id(), row.name(), row.description(), row.icon(),
                    row.parentId() == row.id() ? null : row.parentId(), row.checked(), row.leaf(), row.empty(),
                    List.of(), operator, components.getOrDefault(row.id(), List.of()));
        }
        List<CategoryDto> nested = children.getOrDefault(row.id(), List.of()).stream()
                .map(child -> toDto(child, children, components, onPath)).toList();
        onPath.remove(row.id());
        return new CategoryDto(row.id(), row.name(), row.description(), row.icon(),
                row.parentId() == row.id() ? null : row.parentId(), row.checked(), row.leaf(), row.empty(),
                nested, operator, components.getOrDefault(row.id(), List.of()));
    }

    // compositionId is null on create (a brand-new category can never already be part of a cycle, since it can
    // only reference categories that pre-date it) and the editingId on update (where a cycle genuinely becomes
    // possible -- see the class-level design note in the project plan for a worked example).
    private void validateCompositionComponents(Long compositionId, List<Long> components) {
        if (components.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A composition category needs at least one component");
        for (Long component : components) {
            requireCategory(component);
            if (compositionId != null && wouldCreateCycle(compositionId, component)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This component would create a circular dependency");
            }
        }
    }

    // Starting from candidateComponentId, walks forward through the existing "depends on" graph (an edge A->B
    // means "A has component B", i.e. A's match status depends on B's) -- if compositionId is ever reached,
    // adding the edge compositionId->candidateComponentId would close a cycle back to compositionId. The
    // self-reference case (candidateComponentId == compositionId) falls out for free: it's checked before the
    // first expansion. Deliberately iterative in Java (plain flat queries, no recursive CTE) rather than a
    // `WITH RECURSIVE` walk -- recursive CTEs are a portability hazard this codebase otherwise avoids.
    private boolean wouldCreateCycle(long compositionId, long candidateComponentId) {
        Set<Long> visited = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        pending.push(candidateComponentId);
        while (!pending.isEmpty()) {
            long current = pending.pop();
            if (current == compositionId) return true;
            if (!visited.add(current)) continue;
            jdbc.sql("select component_category_id from composition_category_component where composition_category_id=:id")
                    .param("id", current).query(Long.class).list().forEach(pending::push);
        }
        return false;
    }

    private boolean isCompositionCategory(long categoryId) {
        return jdbc.sql("select exists(select 1 from composition_category where category_id=:id)")
                .param("id", categoryId).query(Boolean.class).single();
    }

    private Operator compositionOperator(long categoryId) {
        Integer code = jdbc.sql("select operator from composition_category where category_id=:id")
                .param("id", categoryId).query(Integer.class).optional().orElse(null);
        return code == null ? null : Operator.fromCode(code);
    }

    private void validateParent(Long parentId, Long categoryId) {
        if (parentId == null) return;
        requireCategory(parentId);
        boolean compositionParent = jdbc.sql("select exists(select 1 from composition_category where category_id=:id)")
                .param("id", parentId).query(Boolean.class).single();
        if (compositionParent) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A composition category cannot have children");
        if (categoryId != null && parentId.equals(categoryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A category cannot be its own parent");
        }
        if (categoryId != null) {
            boolean cycle = jdbc.sql("select exists(select 1 from category_parent_child_all where ancestor_id=:category and descendant_id=:parent)")
                    .param("category", categoryId).param("parent", parentId).query(Boolean.class).single();
            if (cycle) throw new ResponseStatusException(HttpStatus.CONFLICT, "Category hierarchy cannot contain a cycle");
        }
    }

    private void setParent(long id, Long parentId) {
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

    // --- Authorization: MOVIES_ADMIN/MOVIES_GUIDE may manage any category; a plain user may only manage
    // categories inside a movie_guide they own (resolved via ancestry through category_parent_child_all). ---

    private void requireAdminOrGuide(boolean adminOrGuide) {
        if (!adminOrGuide) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only guide curators can create root categories");
    }

    private void requireManage(long categoryId, String username, boolean adminOrGuide) {
        if (adminOrGuide) return;
        boolean owns = jdbc.sql("""
                select exists(
                    select 1 from movie_guide g
                    join category_parent_child_all a on a.ancestor_id=g.category_id and a.descendant_id=:category
                    where g.owner=:username)
                """).param("category", categoryId).param("username", username).query(Boolean.class).single();
        if (!owns) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted to manage this category");
    }

    // Package-visible: reused by MovieGuideService to authorize its own movie-assignment endpoint.
    void requireGuideManage(long guideId, String username, boolean adminOrGuide) {
        if (adminOrGuide) return;
        boolean owns = jdbc.sql("select exists(select 1 from movie_guide where id=:id and owner=:username)")
                .param("id", guideId).param("username", username).query(Boolean.class).single();
        if (!owns) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted to manage this guide");
    }

    // Cascade-deletes id and its native descendants. Every category now has exactly one real parent edge (the
    // old Copy/reference mechanism that could create multi-parent nodes is gone), but this still defends against
    // that generically rather than assuming it -- cheap insurance, and it's the same proven logic as before.
    private void deleteSubtree(long id, long parentId) {
        boolean idHasOtherParent = jdbc.sql("""
                select exists(select 1 from category_parent_child where child_id=:id and parent_id<>:parent)
                """).param("id", id).param("parent", parentId).query(Boolean.class).single();
        if (idHasOtherParent) {
            jdbc.sql("delete from category_parent_child where parent_id=:parent and child_id=:id")
                    .param("parent", parentId).param("id", id).update();
            return;
        }
        List<Long> subtree = jdbc.sql("select descendant_id from category_parent_child_all where ancestor_id=:id")
                .param("id", id).query(Long.class).list();
        Set<Long> subtreeSet = new HashSet<>(subtree);
        Map<Long, List<Long>> parentsOf = new HashMap<>();
        for (Long node : subtree) {
            if (node.equals(id)) continue;
            parentsOf.put(node, jdbc.sql("select parent_id from category_parent_child where child_id=:id and parent_id<>:id")
                    .param("id", node).query(Long.class).list());
        }
        Set<Long> kept = new HashSet<>();
        Set<Long> roots = new HashSet<>(jdbc.sql("""
                select child_id from category_parent_child where parent_id=child_id and child_id in (:ids) and child_id<>:root
                """).param("ids", subtree).param("root", id).query(Long.class).list());
        kept.addAll(roots);
        parentsOf.forEach((node, parents) -> {
            if (parents.stream().anyMatch(parent -> !subtreeSet.contains(parent))) kept.add(node);
        });
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<Long, List<Long>> entry : parentsOf.entrySet()) {
                if (kept.contains(entry.getKey())) continue;
                if (entry.getValue().stream().anyMatch(kept::contains)) {
                    kept.add(entry.getKey());
                    changed = true;
                }
            }
        } while (changed);
        List<Long> destroyable = new ArrayList<>(subtree);
        destroyable.removeAll(kept);
        jdbc.sql("delete from movie_category where category_id in (:ids)").param("ids", destroyable).update();
        jdbc.sql("delete from category where id in (:ids)").param("ids", destroyable).update();
    }
}
