package skycomposer.moviechallenge.api.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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
import skycomposer.moviechallenge.api.movie.dto.CompleteMovieGuideRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateGuideRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieGuideRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieGuideResponse;
import skycomposer.moviechallenge.api.movie.dto.CsvMovieRef;
import skycomposer.moviechallenge.api.movie.dto.GuideMovieDetails;
import skycomposer.moviechallenge.api.movie.dto.GuideMovieRef;
import skycomposer.moviechallenge.api.movie.dto.ImportCsvMoviesRequest;
import skycomposer.moviechallenge.api.movie.dto.ImportCsvMoviesResponse;
import skycomposer.moviechallenge.api.movie.dto.MoveCategoryRequest;
import skycomposer.moviechallenge.api.movie.dto.MovieGuideDto;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.dto.RecommendMovieRequest;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import skycomposer.moviechallenge.api.movie.model.Movie;
import skycomposer.moviechallenge.api.movie.model.MovieGuideStatus;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class MovieGuideService {
    // Path-based, auto-creating (MOVIES_GUIDE / admin only): a single request can create many categories, so the
    // aggregate cap is generous but still bounded. Used only by the JSON-upload bulk-import flow below.
    private static final int MAX_MOVIES_WITH_CATEGORY_CREATION = 1000;
    private static final int MAX_CATEGORIES_WITH_CREATION = 20_000;
    // Path-based, existing-only (any authenticated user): nothing is ever created here, but the cap still bounds
    // DB load per request.
    private static final int MAX_MOVIES_EXISTING_ONLY = 100;
    private static final int MAX_CATEGORIES_EXISTING_ONLY = 700;

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final MovieService movieService;
    private final CategoryService categories;
    private final MovieDtoMapper movieMapper;

    private record Row(long id, long categoryId, String type, String name, String description, String icon,
                       String owner, int status) {}

    @Transactional
    public MovieGuideDto createGuide(CreateGuideRequest request, String owner) {
        String type = normalizeType(request.type());
        String root = type.equals("Personality") ? "Personalities" : "Guides";
        String defaultIcon = type.equals("Personality") ? "🌟" : "🗺️";
        long rootId = getOrCreateCategory(null, root, defaultIcon);
        String trimmedName = request.name().trim();
        if (findExistingCategory(rootId, trimmedName) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + type + " named \"" + trimmedName + "\" already exists. Choose a different name.");
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
        values.put("status", MovieGuideStatus.STARTED.getCode());
        values.put("type", type);
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

    @Transactional(readOnly = true)
    public MovieGuideDto getByCategory(long categoryId) {
        Row row = jdbc.sql("""
                select id, category_id, type, name, description, icon, owner, status
                from movie_guide where category_id=:categoryId
                """).param("categoryId", categoryId).query((rs, n) -> new Row(
                rs.getLong("id"), rs.getLong("category_id"), rs.getString("type"), rs.getString("name"),
                rs.getString("description"), rs.getString("icon"), rs.getString("owner"), rs.getInt("status")))
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
            try {
                jdbc.sql("insert into movie_category(movie_id,category_id) values (:movie,:category)")
                        .param("movie", imdbId).param("category", targetCategoryId).update();
            } catch (DuplicateKeyException ignored) {
                // Idempotent submission.
            }
        }
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

    @Transactional
    public void completeGuide(long guideId, String username, boolean adminOrGuide) {
        requireGuide(guideId);
        categories.requireGuideManage(guideId, username, adminOrGuide);
        jdbc.sql("update movie_guide set status=:status where id=:id")
                .param("status", MovieGuideStatus.COMPLETED.getCode()).param("id", guideId).update();
    }

    // CSV import Phase 1 (default-view "Import from CSV" dialog): links every row that already resolves to an
    // existing movie (by imdbId, or by exact title+year match) into the guide, in one transaction, and reports
    // back any rows that couldn't be resolved -- the client re-attempts those against OMDb (Phase 2a) and, for
    // ones that resolve to exactly one OMDb result, submits them to completeCsvImport below (Phase 2b).
    private static final int MAX_CSV_MOVIES = 2000;

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
            try {
                jdbc.sql("insert into movie_category(movie_id,category_id) values (:movie,:category)")
                        .param("movie", imdbId).param("category", targetCategoryId).update();
            } catch (DuplicateKeyException ignored) {
                // Idempotent submission.
            }
        }
        return new ImportCsvMoviesResponse(failed);
    }

    // CSV import Phase 2b: for rows Phase 1 couldn't resolve, the client looked them up on OMDb (Phase 2a) and
    // only kept the ones that matched exactly one result -- those full movie payloads land here.
    @Transactional
    public void completeCsvImport(long guideId, CompleteCsvImportRequest request, String username, boolean adminOrGuide) {
        Row guide = requireGuide(guideId);
        categories.requireGuideManage(guideId, username, adminOrGuide);
        long targetCategoryId = resolveAssignmentTarget(guide, request.categoryId(), adminOrGuide);
        for (RecommendMovieRequest movieRequest : request.movies()) {
            Movie movie = movieService.getOrCreateMovie(movieRequest);
            try {
                jdbc.sql("insert into movie_category(movie_id,category_id) values (:movie,:category)")
                        .param("movie", movie.getImdbId()).param("category", targetCategoryId).update();
            } catch (DuplicateKeyException ignored) {
                // Idempotent submission.
            }
        }
    }

    // null means "not found" (the row is well-formed -- the client never sends rows it couldn't parse an
    // identity out of): resolves by imdbId first, else falls back to an exact title+year match, discarding the
    // match entirely (same as "not found") if it's ambiguous (more than one movie sharing that title+year).
    private String resolveCsvMovie(CsvMovieRef ref) {
        if (ref.imdbId() != null && !ref.imdbId().isBlank()) {
            String imdbId = ref.imdbId().trim();
            return movieExists(imdbId) ? imdbId : null;
        }
        if (ref.title() != null && !ref.title().isBlank() && ref.year() != null && !ref.year().isBlank()) {
            List<String> matches = jdbc.sql("select imdb_id from movies where lower(title)=lower(:title) and release_year=:year")
                    .param("title", ref.title().trim()).param("year", ref.year().trim()).query(String.class).list();
            return matches.size() == 1 ? matches.get(0) : null;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public MoviePageDto guideMovies(long guideId, int page, int pageSize, String filter, String year) {
        Row guide = requireGuide(guideId);
        List<Long> subscribed = subscribedCategoryIds(guideId);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), pageSize);
        Page<Movie> movies = movieService.getGuideMovies(guide.categoryId(), subscribed, filter, year, pageable);
        return new MoviePageDto(movies.getContent().stream().map(movieMapper::toMovieDto).toList(), movies.getTotalElements());
    }

    private Row requireGuide(long guideId) {
        return jdbc.sql("""
                select id, category_id, type, name, description, icon, owner, status
                from movie_guide where id=:id
                """).param("id", guideId).query((rs, n) -> new Row(
                rs.getLong("id"), rs.getLong("category_id"), rs.getString("type"), rs.getString("name"),
                rs.getString("description"), rs.getString("icon"), rs.getString("owner"), rs.getInt("status")))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie Guide not found"));
    }

    private List<Long> subscribedCategoryIds(long guideId) {
        return jdbc.sql("""
                select category_id from movie_guide_default_category
                where movie_guide_id=:guide and referenced_category_id is not null
                """).param("guide", guideId).query(Long.class).list();
    }

    private MovieGuideDto toDto(Row row) {
        return new MovieGuideDto(row.id(), row.categoryId(), row.type(), row.name(), row.description(), row.icon(),
                row.owner(), MovieGuideStatus.fromCode(row.status()).name(), subscribedCategoryIds(row.id()));
    }

    private String normalizeType(String type) {
        if ("Guide".equals(type) || "Personality".equals(type)) return type;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be \"Guide\" or \"Personality\"");
    }

    private Long findExistingCategory(Long parentId, String name) {
        return jdbc.sql("""
                select c.id from category c join category_parent_child pc on pc.child_id=c.id
                where pc.parent_id = coalesce(:parent, c.id) and lower(c.name) = lower(:name)
                """)
                .param("parent", parentId, Types.BIGINT).param("name", name)
                .query(Long.class).optional().orElse(null);
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

    // --- JSON-upload bulk-import flow (paste a hand-crafted/LLM-generated JSON file): kept alongside the
    // interactive wizard above as a second, faster creation path for large/AI-assisted imports. ---

    @Transactional
    public CreateMovieGuideResponse createGuide(CreateMovieGuideRequest request) {
        requireWithinLimits(request.movies().size(), request.movies().stream().mapToLong(m -> m.categories().size()).sum(),
                MAX_MOVIES_WITH_CATEGORY_CREATION, MAX_CATEGORIES_WITH_CREATION);
        long guideCategoryId = resolveGuideCategory(request.type(), request.name(), request.description(), request.icon());
        List<String> failedImdbIds = new ArrayList<>();
        for (GuideMovieRef movieRef : request.movies()) {
            List<Long> categoryIds = movieRef.categories().stream().map(this::resolveCategoryPath).toList();
            if (movieExists(movieRef.imdbId())) {
                linkMovie(movieRef.imdbId(), categoryIds, guideCategoryId);
            } else {
                failedImdbIds.add(movieRef.imdbId());
            }
        }
        return new CreateMovieGuideResponse(guideCategoryId, failedImdbIds);
    }

    @Transactional
    public void completeGuide(long guideCategoryId, CompleteMovieGuideRequest request) {
        requireWithinLimits(request.movies().size(), request.movies().stream().mapToLong(m -> m.categories().size()).sum(),
                MAX_MOVIES_WITH_CATEGORY_CREATION, MAX_CATEGORIES_WITH_CREATION);
        requireCategory(guideCategoryId);
        for (GuideMovieDetails details : request.movies()) {
            Movie movie = movieService.getOrCreateMovie(details.movie());
            List<Long> categoryIds = details.categories().stream().map(this::resolveCategoryPath).toList();
            linkMovie(movie.getImdbId(), categoryIds, guideCategoryId);
        }
    }

    @Transactional
    public CreateMovieGuideResponse createGuideExistingOnly(CreateMovieGuideRequest request) {
        requireWithinLimits(request.movies().size(), request.movies().stream().mapToLong(m -> m.categories().size()).sum(),
                MAX_MOVIES_EXISTING_ONLY, MAX_CATEGORIES_EXISTING_ONLY);
        long guideCategoryId = resolveGuideCategory(request.type(), request.name(), request.description(), request.icon());
        List<String> failedImdbIds = new ArrayList<>();
        for (GuideMovieRef movieRef : request.movies()) {
            List<Long> categoryIds = movieRef.categories().stream()
                    .map(this::resolveExistingCategoryPath).filter(Objects::nonNull).toList();
            if (movieExists(movieRef.imdbId())) {
                linkMovie(movieRef.imdbId(), categoryIds, guideCategoryId);
            } else {
                failedImdbIds.add(movieRef.imdbId());
            }
        }
        return new CreateMovieGuideResponse(guideCategoryId, failedImdbIds);
    }

    @Transactional
    public void completeGuideExistingOnly(long guideCategoryId, CompleteMovieGuideRequest request) {
        requireWithinLimits(request.movies().size(), request.movies().stream().mapToLong(m -> m.categories().size()).sum(),
                MAX_MOVIES_EXISTING_ONLY, MAX_CATEGORIES_EXISTING_ONLY);
        requireCategory(guideCategoryId);
        for (GuideMovieDetails details : request.movies()) {
            Movie movie = movieService.getOrCreateMovie(details.movie());
            List<Long> categoryIds = details.categories().stream()
                    .map(this::resolveExistingCategoryPath).filter(Objects::nonNull).toList();
            linkMovie(movie.getImdbId(), categoryIds, guideCategoryId);
        }
    }

    private void requireWithinLimits(int movieCount, long categoryCount, int maxMovies, int maxCategories) {
        if (movieCount > maxMovies) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Too many movies: " + movieCount + " (maximum " + maxMovies + ")");
        }
        if (categoryCount > maxCategories) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Too many categories across all movies: " + categoryCount + " (maximum " + maxCategories + ")");
        }
    }

    private long resolveGuideCategory(String type, String name, String description, String icon) {
        String root = switch (type) {
            case "Guide" -> "Guides";
            case "Personality" -> "Personalities";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be \"Guide\" or \"Personality\"");
        };
        String defaultIcon = type.equals("Personality") ? "🌟" : "🗺️";
        long rootId = getOrCreateCategory(null, root, defaultIcon);
        String trimmedName = name.trim();
        // Bulk import must only ever create a brand-new Guide/Personality, never silently fold movies into an
        // existing one under a reused name — refining an existing one is left to manual category/movie edits.
        if (findExistingCategory(rootId, trimmedName) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + type + " named \"" + trimmedName + "\" already exists. Choose a different name — to add "
                            + "or remove movies and categories on the existing one, edit it directly instead of "
                            + "re-importing.");
        }
        String guideIcon = (icon != null && !icon.isBlank()) ? icon.trim() : defaultIcon;
        long guideId = getOrCreateCategory(rootId, trimmedName, guideIcon);
        if (description != null && !description.isBlank()) {
            jdbc.sql("update category set description=:description where id=:id and description is null")
                    .param("description", description.trim()).param("id", guideId).update();
        }
        return guideId;
    }

    private long resolveCategoryPath(String dotPath) {
        Long parentId = null;
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

    // Regular users cannot create categories: walks the path but never creates anything, returning null (and
    // silently dropping that whole path in the caller) the moment any segment doesn't already exist.
    private Long resolveExistingCategoryPath(String dotPath) {
        Long parentId = null;
        Long currentId = null;
        for (String segment : dotPath.split("\\.")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) continue;
            currentId = findExistingCategory(parentId, trimmed);
            if (currentId == null) return null;
            parentId = currentId;
        }
        return currentId;
    }

    // Uses ON CONFLICT rather than insert-then-catch(DuplicateKeyException): Postgres aborts the whole
    // transaction at the server level the instant any statement raises a real error (SQLSTATE 25P02
    // "current transaction is aborted" on everything that follows), so catching the exception in Java does
    // not actually let the transaction keep going for the rest of this loop/request.
    private void linkMovie(String movieId, List<Long> categoryIds, long guideCategoryId) {
        List<Long> all = new ArrayList<>(categoryIds);
        all.add(guideCategoryId);
        for (Long categoryId : all) {
            jdbc.sql("insert into movie_category(movie_id,category_id) values (:movie,:category) on conflict do nothing")
                    .param("movie", movieId).param("category", categoryId).update();
        }
    }

    private boolean movieExists(String imdbId) {
        return jdbc.sql("select exists(select 1 from movies where imdb_id=:id)").param("id", imdbId).query(Boolean.class).single();
    }

    private void requireCategory(long id) {
        if (!jdbc.sql("select exists(select 1 from category where id=:id)").param("id", id).query(Boolean.class).single())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
    }
}
