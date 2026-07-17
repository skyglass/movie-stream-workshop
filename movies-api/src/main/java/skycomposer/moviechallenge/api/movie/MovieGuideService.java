package skycomposer.moviechallenge.api.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import skycomposer.moviechallenge.api.movie.dto.CompleteMovieGuideRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieGuideRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieGuideResponse;
import skycomposer.moviechallenge.api.movie.dto.GuideMovieDetails;
import skycomposer.moviechallenge.api.movie.dto.GuideMovieRef;
import skycomposer.moviechallenge.api.movie.model.Movie;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class MovieGuideService {
    // Path-based, auto-creating (MOVIES_GUIDE / admin only): a single request can create many categories, so the
    // aggregate cap is generous but still bounded.
    private static final int MAX_MOVIES_WITH_CATEGORY_CREATION = 1000;
    private static final int MAX_CATEGORIES_WITH_CREATION = 20_000;
    // Path-based, existing-only (any authenticated user): nothing is ever created here, but the cap still bounds
    // DB load per request.
    private static final int MAX_MOVIES_EXISTING_ONLY = 100;
    private static final int MAX_CATEGORIES_EXISTING_ONLY = 700;

    private final JdbcClient jdbc;
    private final MovieService movieService;

    @Transactional
    public CreateMovieGuideResponse createGuide(CreateMovieGuideRequest request) {
        requireWithinLimits(request.movies().size(), request.movies().stream().mapToLong(m -> m.categories().size()).sum(),
                MAX_MOVIES_WITH_CATEGORY_CREATION, MAX_CATEGORIES_WITH_CREATION);
        long guideCategoryId = resolveGuideCategory(request.type(), request.name(), request.description());
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
        long guideCategoryId = resolveGuideCategory(request.type(), request.name(), request.description());
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

    private long resolveGuideCategory(String type, String name, String description) {
        String root = switch (type) {
            case "Guide" -> "Guides";
            case "Personality" -> "Personalities";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be \"Guide\" or \"Personality\"");
        };
        String icon = type.equals("Personality") ? "🌟" : "🗺️";
        long rootId = getOrCreateCategory(null, root, icon);
        String trimmedName = name.trim();
        // Bulk import must only ever create a brand-new Guide/Personality, never silently fold movies into an
        // existing one under a reused name — refining an existing one is left to manual category/movie edits.
        if (findExistingCategory(rootId, trimmedName) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + type + " named \"" + trimmedName + "\" already exists. Choose a different name — to add "
                            + "or remove movies and categories on the existing one, edit it directly instead of "
                            + "re-importing.");
        }
        long guideId = getOrCreateCategory(rootId, trimmedName, null);
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
