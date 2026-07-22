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
import skycomposer.moviechallenge.api.movie.dto.RecommendMovieRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.SaveMovieCategoriesRequest;
import skycomposer.moviechallenge.api.movie.model.Movie;

import java.util.ArrayList;
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
                       boolean checked, boolean leaf, boolean empty, Long referencedCategoryId) {}

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
                            where child.parent_id=c.id and child.child_id<>c.id) empty,
                    (select d.referenced_category_id from movie_guide_default_category d
                        join movie_guide g on g.id=d.movie_guide_id and g.category_id=direct.parent_id
                        where d.category_id=c.id and d.referenced_category_id is not null limit 1) referenced_category_id
                from category c
                join category_parent_child direct on direct.child_id=c.id
                order by lower(c.name), c.id
                """).param("movie", movieId, Types.VARCHAR).query((rs, n) -> new Row(
                rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getString("icon"),
                rs.getLong("parent_id"), rs.getBoolean("checked"), rs.getBoolean("leaf"), rs.getBoolean("empty"),
                (Long) rs.getObject("referenced_category_id"))).list();

        Map<Long, List<Row>> children = new HashMap<>();
        rows.stream().filter(row -> row.parentId() != row.id())
                .forEach(row -> children.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row));
        return rows.stream().filter(row -> row.parentId() == row.id()).map(row -> toDto(row, children)).toList();
    }

    // The direct children of one category (e.g. a Movie Guide's own anchor category), excluding a given set of
    // ids (e.g. its subscribed/referenced categories) -- backs a guide's own, sandboxed category picker so the
    // owner only ever sees/manages categories that are genuinely native to their guide.
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
    public CategoryDto update(long id, SaveCategoryRequest request, String username, boolean adminOrGuide) {
        requireCategory(id);
        if (isReferencedAnywhere(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Referenced categories are read-only here; edit them at their original location");
        }
        requireManage(id, username, adminOrGuide);
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
        // Only a real move (copy=false) deletes the sourceParentId->id edge, which could silently break some
        // guide's subscription if that edge is what it depends on -- a copy (subscribe) is purely additive (a
        // new edge, nothing removed), so it's always safe even when the category is already referenced
        // elsewhere, including by other guides subscribing to the same category.
        if (!request.copy() && isReferencedAnywhere(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Referenced categories are read-only here; unlink them from the guide instead of moving");
        }
        requireManage(id, username, adminOrGuide);
        validateParent(request.targetParentId(), id);
        long target = request.targetParentId() == null ? id : request.targetParentId();
        if (!request.copy()) {
            jdbc.sql("delete from category_parent_child where parent_id=:parent and child_id=:id")
                    .param("parent", request.sourceParentId()).param("id", id).update();
        }
        // ON CONFLICT DO NOTHING rather than insert-then-catch(DuplicateKeyException): Postgres aborts the whole
        // transaction at the server level the instant any statement raises a real error (SQLSTATE 25P02 "current
        // transaction is aborted" on everything that follows), so catching the exception in Java does not
        // actually let the transaction keep going for the rest of this request -- e.g. re-subscribing to an
        // already-subscribed category (the client resends the full checked list, not just newly-added ones).
        jdbc.sql("insert into category_parent_child(parent_id,child_id) values (:parent,:id) on conflict do nothing")
                .param("parent", target).param("id", id).update();
        if (request.copy()) {
            recordGuideReference(target, id);
        }
        rebuildClosure();
        return findInTree(id);
    }

    @Transactional
    public void delete(long id, long parentId, String username, boolean adminOrGuide) {
        requireCategory(id);
        Long referencingGuideId = guideIdForReferenceEdge(id, parentId);
        if (referencingGuideId != null) {
            requireGuideManage(referencingGuideId, username, adminOrGuide);
            unlinkGuideReference(referencingGuideId, parentId, id);
            return;
        }
        if (isReferencedAnywhere(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This category is referenced by a guide and can only be removed from that guide");
        }
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
            jdbc.sql("delete from movie_category where movie_id=:movie and category_id=:category")
                    .param("movie", movieId).param("category", categoryId).update();
        }
        for (Long categoryId : added) {
            jdbc.sql("insert into movie_category(movie_id,category_id) values (:movie,:category) on conflict do nothing")
                    .param("movie", movieId).param("category", categoryId).update();
        }
        return tree(movieId);
    }

    // Removes a movie from every category within these scope ids' transitive subtrees (each scope id itself,
    // plus all its descendants) -- e.g. the "Delete Movies" dialog passes the guide's own category (or a picked
    // sub-category) as scope, and this reaches the movie regardless of which exact descendant category it's
    // actually filed under, matching how the movie list itself is matched via the same transitive closure.
    //
    // protectedSubtreeCategoryIds carves out categories that must never be touched by this removal, however scope
    // was computed -- namely a guide's subscribed/referenced categories, which are physically DAG-linked into the
    // guide's own subtree (so scope=[guide's root] would otherwise reach them) but are read-only references to a
    // category that lives, and is owned, elsewhere. Pass an empty/null list when there's nothing to protect (e.g.
    // the Watchlist path, whose private tree never contains a subscribed category's real rows in the first place).
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
        jdbc.sql("insert into movie_category(movie_id,category_id) values (:movie,:category) on conflict do nothing")
                .param("movie", movie.getImdbId()).param("category", categoryId).update();
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
            jdbc.sql("insert into movie_category(movie_id,category_id) values (:movie,:category) on conflict do nothing")
                    .param("movie", imdbId).param("category", categoryId).update();
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
        return toDto(row, children, new HashSet<>());
    }

    // Write-time checks (validateParent's cycle check) should make a genuine cycle in category_parent_child
    // impossible, but this walk is the one place a cycle would turn into infinite recursion / a StackOverflow
    // instead of a clean error, so it defends itself too: `onPath` tracks the current root-to-node path (not a
    // global visited set -- the same category legitimately appears in multiple branches under the DAG, that's
    // not a cycle) and a repeat within that one path is where a real cycle would show up.
    private CategoryDto toDto(Row row, Map<Long, List<Row>> children, Set<Long> onPath) {
        if (!onPath.add(row.id())) {
            return new CategoryDto(row.id(), row.name(), row.description(), row.icon(),
                    row.parentId() == row.id() ? null : row.parentId(), row.checked(), row.leaf(), row.empty(),
                    row.referencedCategoryId(), false, List.of());
        }
        List<CategoryDto> nested = children.getOrDefault(row.id(), List.of()).stream()
                .map(child -> toDto(child, children, onPath)).toList();
        onPath.remove(row.id());
        return new CategoryDto(row.id(), row.name(), row.description(), row.icon(),
                row.parentId() == row.id() ? null : row.parentId(), row.checked(), row.leaf(), row.empty(),
                row.referencedCategoryId(), false, nested);
    }

    private void validateParent(Long parentId, Long categoryId) {
        if (parentId == null) return;
        requireCategory(parentId);
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

    // --- Movie Guide default-category reference tracking. ---

    private boolean isReferencedAnywhere(long categoryId) {
        return jdbc.sql("select exists(select 1 from movie_guide_default_category where category_id=:id and referenced_category_id is not null)")
                .param("id", categoryId).query(Boolean.class).single();
    }

    private Long guideIdForReferenceEdge(long categoryId, long parentId) {
        return jdbc.sql("""
                select g.id from movie_guide g
                join movie_guide_default_category d on d.movie_guide_id=g.id
                    and d.category_id=:category and d.referenced_category_id is not null
                where g.category_id=:parent
                """).param("category", categoryId).param("parent", parentId).query(Long.class).optional().orElse(null);
    }

    // Backs the "Subscribe to Categories" dialog's unsubscribe path (unchecking a previously-subscribed category):
    // removes the guide's own reference edge and its movie_guide_default_category tracking row, leaving the
    // category's original position (and any other guide's subscription to it) untouched.
    @Transactional
    public void unsubscribeGuideCategory(long guideId, long guideCategoryId, long categoryId) {
        unlinkGuideReference(guideId, guideCategoryId, categoryId);
    }

    private void unlinkGuideReference(long guideId, long parentId, long categoryId) {
        jdbc.sql("delete from movie_guide_default_category where movie_guide_id=:guide and category_id=:category")
                .param("guide", guideId).param("category", categoryId).update();
        jdbc.sql("delete from category_parent_child where parent_id=:parent and child_id=:category")
                .param("parent", parentId).param("category", categoryId).update();
        rebuildClosure();
    }

    // Copying a category into a guide's sandbox records it as that guide's default category, referencing its
    // own id (no row duplication) -- this is what keeps the copy "live": movies added to the original later are
    // automatically visible through this same category row from the guide's tree too.
    private void recordGuideReference(long targetParentId, long categoryId) {
        Long guideId = jdbc.sql("""
                select g.id from movie_guide g
                join category_parent_child_all a on a.ancestor_id=g.category_id
                where a.descendant_id=:target
                order by g.id limit 1
                """).param("target", targetParentId).query(Long.class).optional().orElse(null);
        if (guideId == null) return;
        jdbc.sql("""
                insert into movie_guide_default_category(movie_guide_id, category_id, referenced_category_id)
                values (:guide, :category, :category) on conflict do nothing
                """).param("guide", guideId).param("category", categoryId).update();
    }

    // Cascade-deletes id and its native descendants, but never destroys a category still reachable through some
    // other parent edge elsewhere in the tree (a Move/Copy link, or a guide reference) -- such nodes are only
    // unlinked from this branch, exactly like the top-level guide-reference unlink above, just for the general
    // multi-parent case rather than the guide-specific one.
    private void deleteSubtree(long id, long parentId) {
        // Deliberately does NOT exclude id's own self-referencing root edge here (unlike the parentsOf query
        // below, which has its own separate roots-seeding compensation): a category that is independently a
        // root AND also reachable via the specific (parentId, id) edge being removed must survive as that root,
        // not get destroyed just because this one edge happened to be its only *other* tracked parent.
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
        // A node that is independently a root (its own self-referencing edge) must never be destroyed just
        // because it's also reachable as a Copy-linked descendant of the thing being deleted -- the self-edge
        // itself is filtered out of parentsOf above (parent_id<>id), so root-ness would otherwise be invisible
        // to the reachability analysis below and the root would be wrongly swept up as "destroyable".
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
