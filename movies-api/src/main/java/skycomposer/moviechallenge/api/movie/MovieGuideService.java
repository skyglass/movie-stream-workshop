package skycomposer.moviechallenge.api.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import skycomposer.moviechallenge.api.movie.dto.AssignGuideMoviesRequest;
import skycomposer.moviechallenge.api.movie.dto.CompleteCsvImportRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateGuideRequest;
import skycomposer.moviechallenge.api.movie.dto.CsvMovieImport;
import skycomposer.moviechallenge.api.movie.dto.CsvMovieRef;
import skycomposer.moviechallenge.api.movie.dto.ImportCsvMoviesRequest;
import skycomposer.moviechallenge.api.movie.dto.ImportCsvMoviesResponse;
import skycomposer.moviechallenge.api.movie.dto.MoveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.MovieGuideDto;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import skycomposer.moviechallenge.api.movie.model.Movie;
import skycomposer.moviechallenge.api.movie.model.MovieGuideType;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class MovieGuideService {
    private static final int MAX_CSV_MOVIES = 2000;

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final MovieService movieService;
    private final CategoryService categories;
    private final MovieDtoMapper movieMapper;

    private record Row(long id, long categoryId, int type, String name, String description, String icon,
                       String owner) {}

    @Transactional
    public MovieGuideDto createGuide(CreateGuideRequest request, String owner) {
        MovieGuideType type = normalizeType(request.type());
        String root = type == MovieGuideType.PERSONALITY ? "Personalities" : "Guides";
        String defaultIcon = type == MovieGuideType.PERSONALITY ? "🌟" : "🗺️";
        long rootId = getOrCreateCategory(null, root, defaultIcon);
        String trimmedName = request.name().trim();
        // movie_guide.name is globally unique (uq_movie_guide_name) -- a friendly pre-check ahead of the DB
        // constraint. This subsumes the old same-root-only category-name check: since movie_guide.name is now
        // unique across both Guides and Personalities, no two guides can ever collide on a category name within
        // one root either.
        if (jdbc.sql("select exists(select 1 from movie_guide where lower(name)=lower(:name))")
                .param("name", trimmedName).query(Boolean.class).single()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Movie Guide with the name \"" + trimmedName + "\" already Exists");
        }
        String icon = (request.icon() != null && !request.icon().isBlank()) ? request.icon().trim() : defaultIcon;
        long categoryId = getOrCreateCategory(rootId, trimmedName, icon);
        String description = request.description() != null && !request.description().isBlank()
                ? request.description().trim() : null;
        if (description != null) {
            jdbc.sql("update category set description=:description where id=:id")
                    .param("description", description).param("id", categoryId).update();
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", trimmedName);
        values.put("description", description);
        values.put("icon", icon);
        values.put("owner", owner);
        values.put("category_id", categoryId);
        values.put("type", type.getCode());
        Number key = new SimpleJdbcInsert(jdbcTemplate).withTableName("movie_guide")
                .usingGeneratedKeyColumns("id").executeAndReturnKey(values);
        long guideId = key.longValue();

        List<Long> subscribed = request.subscribedCategoryIds() == null
                ? List.of() : request.subscribedCategoryIds().stream().distinct().toList();
        for (Long subscribedCategoryId : subscribed) {
            requireCategoryExists(subscribedCategoryId);
            // Subscribing links an arbitrary existing category into the guide as a Copy edge. move()'s normal
            // authorization checks manage-rights on the *source* category, which is the wrong check here --
            // subscribing to "New 2026" doesn't require owning it. The real authorization boundary is ownership
            // of the destination guide, already established above by inserting the movie_guide row with
            // owner=owner, so the bypass flag is safe: it never lets this caller touch anything but their own
            // brand-new guide's anchor category.
            categories.move(subscribedCategoryId, new MoveCategoryRequest(subscribedCategoryId, categoryId, true), owner, true);
        }
        return toDto(requireGuide(guideId));
    }

    // Step 2 of the interactive wizard: subscribe an already-created guide to additional categories. Split out
    // from createGuide's own subscribedCategoryIds handling above because Step 1 no longer collects categories
    // at creation time -- Step 1 now only creates the guide's name/description/icon, and category subscription
    // happens here as its own step, on a guide that already exists.
    //
    // categoryIds is the full desired set (matching the dialog's live checkbox state, sent whole on Submit, not
    // just newly-checked ones) -- reconciled against what's currently subscribed: missing ones are subscribed,
    // no-longer-present ones are unsubscribed (unchecking a category removes it).
    @Transactional
    public MovieGuideDto subscribeCategories(long guideId, List<Long> categoryIds, String username, boolean adminOrGuide) {
        Row guide = requireGuide(guideId);
        categories.requireGuideManage(guideId, username, adminOrGuide);
        Set<Long> desired = categoryIds == null ? Set.of() : new HashSet<>(categoryIds);
        Set<Long> current = new HashSet<>(subscribedCategoryIds(guideId));
        for (Long categoryId : desired) {
            if (current.contains(categoryId)) continue;
            requireCategoryExists(categoryId);
            // Same bypass reasoning as createGuide's subscribedCategoryIds loop: the real authorization boundary
            // is ownership of this guide, already verified by requireGuideManage above.
            categories.move(categoryId, new MoveCategoryRequest(categoryId, guide.categoryId(), true), username, true);
        }
        for (Long categoryId : current) {
            if (desired.contains(categoryId)) continue;
            categories.unsubscribeGuideCategory(guideId, guide.categoryId(), categoryId);
        }
        return toDto(requireGuide(guideId));
    }

    @Transactional(readOnly = true)
    public MovieGuideDto getByCategory(long categoryId) {
        Row row = jdbc.sql("""
                select id, category_id, type, name, description, icon, owner
                from movie_guide where category_id=:categoryId
                """).param("categoryId", categoryId).query((rs, n) -> new Row(
                rs.getLong("id"), rs.getLong("category_id"), rs.getInt("type"), rs.getString("name"),
                rs.getString("description"), rs.getString("icon"), rs.getString("owner")))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie Guide not found"));
        return toDto(row);
    }

    // Backs the "Delete" action on the Movie Guides/Personalities list: it's only ever shown for guides the
    // current user owns, so the list page needs to know which category ids those are without an N+1 lookup.
    @Transactional(readOnly = true)
    public List<Long> myGuideCategoryIds(String username) {
        return jdbc.sql("select category_id from movie_guide where owner=:username")
                .param("username", username).query(Long.class).list();
    }

    @Transactional
    public void assignMovies(long guideId, AssignGuideMoviesRequest request, String username, boolean adminOrGuide) {
        Row guide = requireGuide(guideId);
        categories.requireGuideManage(guideId, username, adminOrGuide);
        long targetCategoryId = resolveAssignmentTarget(guide, request.categoryId(), adminOrGuide);
        List<String> imdbIds = request.imdbIds().stream().distinct().toList();
        imdbIds.forEach(this::requireMovieExists);
        for (String imdbId : imdbIds) {
            // ON CONFLICT DO NOTHING rather than insert-then-catch(DuplicateKeyException): Postgres aborts the
            // whole transaction server-side the instant any statement raises a real error, so catching it in
            // Java would not actually let this loop keep going for the rest of the batch.
            jdbc.sql("insert into movie_category(movie_id,category_id) values (:movie,:category) on conflict do nothing")
                    .param("movie", imdbId).param("category", targetCategoryId).update();
        }
    }

    // Backs the "Delete Movies" dialog: removes a movie from every category within the given scope's transitive
    // subtrees (each scope id itself, plus all its descendants) -- e.g. scope=[guide's own category] reaches the
    // movie no matter which native sub-category it's actually filed under, matching how the movie list itself is
    // matched via the same transitive closure (category_parent_child_all).
    @Transactional
    public void removeMovie(long guideId, String imdbId, List<Long> categoryIds, String username, boolean adminOrGuide) {
        requireGuide(guideId);
        categories.requireGuideManage(guideId, username, adminOrGuide);
        requireMovieExists(imdbId);
        categories.removeMovieFromCategorySubtree(imdbId, categoryIds);
    }

    // Movies may be assigned directly to the guide's own anchor category, or to one of its native sub-categories
    // (picked via the guide-scoped category selector) -- but never anywhere outside the guide's own sandbox, and
    // a plain owner (not MOVIES_GUIDE/MOVIES_ADMIN) may never target one of the guide's own default/subscribed
    // categories directly -- those are read-only references to a category that lives (and is managed) elsewhere.
    private long resolveAssignmentTarget(Row guide, Long requestedCategoryId, boolean adminOrGuide) {
        if (requestedCategoryId == null) return guide.categoryId();
        boolean withinGuide = jdbc.sql("""
                select exists(select 1 from category_parent_child_all where ancestor_id=:root and descendant_id=:target)
                """).param("root", guide.categoryId()).param("target", requestedCategoryId).query(Boolean.class).single();
        if (!withinGuide) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category is not part of this guide");
        if (!adminOrGuide) {
            boolean isDefaultCategory = jdbc.sql("""
                    select exists(select 1 from movie_guide_default_category
                        where movie_guide_id=:guide and category_id=:category and referenced_category_id is not null)
                    """).param("guide", guide.id()).param("category", requestedCategoryId).query(Boolean.class).single();
            if (isDefaultCategory) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "This category is a read-only reference; only MOVIES_GUIDE/MOVIES_ADMIN can add movies to it directly");
            }
        }
        return requestedCategoryId;
    }

    // CSV import Phase 1 (default-view "Import from CSV" dialog): links every row whose imdb_id already exists in
    // the catalog into the guide, in one transaction, and reports back any rows that couldn't be resolved -- the
    // client re-attempts those against OMDb (Phase 2a) and, for ones that resolve to exactly one OMDb result,
    // submits them to completeCsvImport below (Phase 2b).
    @Transactional
    public ImportCsvMoviesResponse importCsv(long guideId, ImportCsvMoviesRequest request, String username, boolean adminOrGuide) {
        if (request.movies().size() > MAX_CSV_MOVIES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Too many rows: " + request.movies().size() + " (maximum " + MAX_CSV_MOVIES + ")");
        }
        Row guide = requireGuide(guideId);
        categories.requireGuideManage(guideId, username, adminOrGuide);
        long targetCategoryId = resolveAssignmentTarget(guide, request.categoryId(), adminOrGuide);
        List<CsvMovieRef> failed = new ArrayList<>();
        for (CsvMovieRef ref : request.movies()) {
            String imdbId = resolveCsvMovie(ref);
            if (imdbId == null) {
                failed.add(ref);
                continue;
            }
            assignToPathsOrTarget(imdbId, targetCategoryId, ref.categoryPaths());
        }
        return new ImportCsvMoviesResponse(failed);
    }

    // CSV import Phase 2b: for rows Phase 1 couldn't resolve, the client looked them up on OMDb (Phase 2a) and
    // only kept the ones that matched exactly one result -- those full movie payloads (each still carrying its
    // own row's suggested category paths) land here.
    @Transactional
    public void completeCsvImport(long guideId, CompleteCsvImportRequest request, String username, boolean adminOrGuide) {
        Row guide = requireGuide(guideId);
        categories.requireGuideManage(guideId, username, adminOrGuide);
        long targetCategoryId = resolveAssignmentTarget(guide, request.categoryId(), adminOrGuide);
        for (CsvMovieImport item : request.movies()) {
            Movie movie = movieService.getOrCreateMovie(item.movie());
            assignToPathsOrTarget(movie.getImdbId(), targetCategoryId, item.categoryPaths());
        }
    }

    // No suggested category paths: assign directly to the import's target (the guide's own category, or the
    // selected sub-category). One or more paths: each is resolved/created relative to that same target (e.g.
    // "Genres.Drama" under a guide anchored at "My Favorites" becomes "My Favorites -> Genres -> Drama") and the
    // movie is linked to every resolved leaf -- reusing the exact path-walking helper the JSON-upload flow uses,
    // just rooted at the guide's target instead of the global root.
    private void assignToPathsOrTarget(String imdbId, long targetCategoryId, List<String> categoryPaths) {
        if (categoryPaths == null || categoryPaths.isEmpty()) {
            linkMovieToCategory(imdbId, targetCategoryId);
            return;
        }
        for (String path : categoryPaths) {
            if (path == null || path.isBlank()) continue;
            long leafCategoryId = resolveCategoryPath(targetCategoryId, path);
            linkMovieToCategory(imdbId, leafCategoryId);
        }
    }

    private void linkMovieToCategory(String imdbId, long categoryId) {
        jdbc.sql("insert into movie_category(movie_id,category_id) values (:movie,:category) on conflict do nothing")
                .param("movie", imdbId).param("category", categoryId).update();
    }

    // null means "not found" (the row is well-formed -- the client never sends rows it couldn't parse an
    // imdb_id out of).
    private String resolveCsvMovie(CsvMovieRef ref) {
        if (ref.imdbId() == null || ref.imdbId().isBlank()) return null;
        String imdbId = ref.imdbId().trim();
        return movieExists(imdbId) ? imdbId : null;
    }

    @Transactional(readOnly = true)
    public MoviePageDto guideMovies(long guideId, int page, int pageSize, String filter, String year) {
        Row guide = requireGuide(guideId);
        List<Long> subscribed = subscribedCategoryIds(guideId);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), pageSize);
        Page<Movie> movies = movieService.getGuideMovies(guide.categoryId(), subscribed, filter, year, pageable);
        return new MoviePageDto(movies.getContent().stream().map(movieMapper::toMovieDto).toList(), movies.getTotalElements());
    }

    // Backs the guide page's bottom "Recommend Similar Movies" section -- public (username nullable for an
    // anonymous viewer, see MovieService.getCategorySimilarToGuideMovies).
    @Transactional(readOnly = true)
    public MoviePageDto similarMovies(long guideId, int page, int pageSize, String username, String filter,
                                       String year, List<Long> selectedCategories) {
        Row guide = requireGuide(guideId);
        List<Long> subscribed = subscribedCategoryIds(guideId);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), pageSize);
        Page<Movie> movies = movieService.getCategorySimilarToGuideMovies(
                guide.categoryId(), subscribed, username, pageable, filter, year, selectedCategories);
        return new MoviePageDto(movies.getContent().stream().map(movieMapper::toMovieDto).toList(), movies.getTotalElements());
    }

    private Row requireGuide(long guideId) {
        return jdbc.sql("""
                select id, category_id, type, name, description, icon, owner
                from movie_guide where id=:id
                """).param("id", guideId).query((rs, n) -> new Row(
                rs.getLong("id"), rs.getLong("category_id"), rs.getInt("type"), rs.getString("name"),
                rs.getString("description"), rs.getString("icon"), rs.getString("owner")))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie Guide not found"));
    }

    private List<Long> subscribedCategoryIds(long guideId) {
        return jdbc.sql("""
                select category_id from movie_guide_default_category
                where movie_guide_id=:guide and referenced_category_id is not null
                """).param("guide", guideId).query(Long.class).list();
    }

    private MovieGuideDto toDto(Row row) {
        return new MovieGuideDto(row.id(), row.categoryId(), MovieGuideType.fromCode(row.type()).getDescription(),
                row.name(), row.description(), row.icon(), row.owner(), subscribedCategoryIds(row.id()));
    }

    private MovieGuideType normalizeType(String type) {
        if ("Guide".equals(type)) return MovieGuideType.GUIDE;
        if ("Personality".equals(type)) return MovieGuideType.PERSONALITY;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be \"Guide\" or \"Personality\"");
    }

    private long getOrCreateCategory(Long parentId, String name, String icon) {
        return jdbc.sql("select get_or_create_category(:parent, :name, :icon)")
                .param("parent", parentId, Types.BIGINT).param("name", name).param("icon", icon, Types.VARCHAR)
                .query(Long.class).single();
    }

    private void requireCategoryExists(long id) {
        if (!jdbc.sql("select exists(select 1 from category where id=:id)").param("id", id).query(Boolean.class).single())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + id);
    }

    private void requireMovieExists(String imdbId) {
        if (!jdbc.sql("select exists(select 1 from movies where imdb_id=:id)").param("id", imdbId).query(Boolean.class).single())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found: " + imdbId);
    }

    // Same dot-separated path walk used by CSV import's suggested_categories, so "Genres.Drama" resolves under
    // the guide's own target category (rootParentId) instead of as a top-level path.
    private long resolveCategoryPath(Long rootParentId, String dotPath) {
        Long parentId = rootParentId;
        long currentId = -1;
        for (String segment : dotPath.split("\\.")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) continue;
            currentId = getOrCreateCategory(parentId, trimmed, null);
            parentId = currentId;
        }
        if (currentId < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category path must not be blank: \"" + dotPath + "\"");
        }
        return currentId;
    }

    private boolean movieExists(String imdbId) {
        return jdbc.sql("select exists(select 1 from movies where imdb_id=:id)").param("id", imdbId).query(Boolean.class).single();
    }
}
