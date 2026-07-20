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
import skycomposer.moviechallenge.api.movie.dto.AssignWatchlistMoviesRequest;
import skycomposer.moviechallenge.api.movie.dto.CategoryDto;
import skycomposer.moviechallenge.api.movie.dto.CompleteCsvImportRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateWatchlistRequest;
import skycomposer.moviechallenge.api.movie.dto.CsvMovieImport;
import skycomposer.moviechallenge.api.movie.dto.CsvMovieRef;
import skycomposer.moviechallenge.api.movie.dto.ImportCsvMoviesRequest;
import skycomposer.moviechallenge.api.movie.dto.ImportCsvMoviesResponse;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.dto.UpdateWatchlistRequest;
import skycomposer.moviechallenge.api.movie.dto.WatchlistDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import skycomposer.moviechallenge.api.movie.model.Movie;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class WatchlistService {
    private static final int MAX_CSV_MOVIES = 2000;

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final MovieService movieService;
    private final PrivateCategoryService privateCategories;
    private final MovieDtoMapper movieMapper;

    private record Row(long id, long categoryId, String name, String description, String icon, String owner) {}

    @Transactional
    public WatchlistDto createWatchlist(CreateWatchlistRequest request, String owner) {
        String trimmedName = request.name().trim();
        requireNameAvailable(owner, trimmedName, null);
        long watchlistsRootId = resolveWatchlistsRoot(owner);
        String icon = (request.icon() != null && !request.icon().isBlank()) ? request.icon().trim() : "🔖";
        long categoryId = getOrCreatePrivateCategory(watchlistsRootId, trimmedName, icon);
        String description = request.description() != null && !request.description().isBlank()
                ? request.description().trim() : null;
        if (description != null) {
            jdbc.sql("update private_category set description=:description where id=:id")
                    .param("description", description).param("id", categoryId).update();
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", trimmedName);
        values.put("description", description);
        values.put("icon", icon);
        values.put("owner", owner);
        values.put("category_id", categoryId);
        Number key = new SimpleJdbcInsert(jdbcTemplate).withTableName("user_movie_watchlist")
                .usingGeneratedKeyColumns("id").executeAndReturnKey(values);
        long watchlistId = key.longValue();

        List<Long> subscribed = request.subscribedCategoryIds() == null
                ? List.of() : request.subscribedCategoryIds().stream().distinct().toList();
        for (Long categoryToSubscribe : subscribed) {
            requirePublicCategoryExists(categoryToSubscribe);
            jdbc.sql("insert into movie_watchlist_default_category(watchlist_id,category_id) values (:watchlist,:category) on conflict do nothing")
                    .param("watchlist", watchlistId).param("category", categoryToSubscribe).update();
        }
        return toDto(requireWatchlist(watchlistId));
    }

    // Reconciles the desired (full, live checkbox state) subscription set against what's currently subscribed:
    // missing ones are subscribed, no-longer-present ones are unsubscribed. Unlike MovieGuideService, this never
    // touches the category DAG at all -- a subscription is only ever a row in movie_watchlist_default_category.
    @Transactional
    public WatchlistDto subscribeCategories(long watchlistId, List<Long> categoryIds, String username, boolean isAdmin) {
        Row watchlist = requireWatchlist(watchlistId);
        requireOwnerOrAdmin(watchlist, username, isAdmin);
        Set<Long> desired = categoryIds == null ? Set.of() : new HashSet<>(categoryIds);
        Set<Long> current = new HashSet<>(subscribedCategoryIds(watchlistId));
        for (Long categoryId : desired) {
            if (current.contains(categoryId)) continue;
            requirePublicCategoryExists(categoryId);
            jdbc.sql("insert into movie_watchlist_default_category(watchlist_id,category_id) values (:watchlist,:category) on conflict do nothing")
                    .param("watchlist", watchlistId).param("category", categoryId).update();
        }
        for (Long categoryId : current) {
            if (desired.contains(categoryId)) continue;
            jdbc.sql("delete from movie_watchlist_default_category where watchlist_id=:watchlist and category_id=:category")
                    .param("watchlist", watchlistId).param("category", categoryId).update();
        }
        return toDto(requireWatchlist(watchlistId));
    }

    @Transactional
    public WatchlistDto updateWatchlist(long id, UpdateWatchlistRequest request, String username, boolean isAdmin) {
        Row watchlist = requireWatchlist(id);
        requireOwnerOrAdmin(watchlist, username, isAdmin);
        String trimmedName = request.name().trim();
        requireNameAvailable(watchlist.owner(), trimmedName, id);
        String description = request.description() != null && !request.description().isBlank()
                ? request.description().trim() : null;
        String icon = request.icon() != null && !request.icon().isBlank() ? request.icon().trim() : null;
        jdbc.sql("update private_category set name=:name, description=:description, icon=:icon where id=:id")
                .param("name", trimmedName).param("description", description).param("icon", icon)
                .param("id", watchlist.categoryId()).update();
        jdbc.sql("update user_movie_watchlist set name=:name, description=:description, icon=:icon where id=:id")
                .param("name", trimmedName).param("description", description).param("icon", icon)
                .param("id", id).update();
        return toDto(requireWatchlist(id));
    }

    // Deletes the watchlist's private_category anchor row and lets foreign keys cascade the rest -- same single
    // primitive CategoryService.delete() plays for a Movie Guide (user_movie_watchlist.category_id, and every
    // movie_watchlist_movie/movie_private_category row under the subtree, all cascade from there).
    @Transactional
    public void deleteWatchlist(long id, String username, boolean isAdmin) {
        Row watchlist = requireWatchlist(id);
        requireOwnerOrAdmin(watchlist, username, isAdmin);
        Long parentId = privateCategories.parentOf(watchlist.categoryId());
        if (parentId == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Watchlist category has no parent");
        privateCategories.delete(watchlist.categoryId(), parentId, username, isAdmin);
    }

    @Transactional(readOnly = true)
    public WatchlistDto getById(long id, String username, boolean isAdmin) {
        Row watchlist = requireWatchlist(id);
        requireOwnerOrAdmin(watchlist, username, isAdmin);
        return toDto(watchlist);
    }

    @Transactional(readOnly = true)
    public WatchlistDto getByCategory(long categoryId, String username, boolean isAdmin) {
        Row watchlist = jdbc.sql("""
                select id, category_id, name, description, icon, owner from user_movie_watchlist where category_id=:categoryId
                """).param("categoryId", categoryId).query((rs, n) -> new Row(
                rs.getLong("id"), rs.getLong("category_id"), rs.getString("name"), rs.getString("description"),
                rs.getString("icon"), rs.getString("owner")))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist not found"));
        requireOwnerOrAdmin(watchlist, username, isAdmin);
        return toDto(watchlist);
    }

    @Transactional(readOnly = true)
    public List<WatchlistDto> myWatchlists(String username) {
        List<Row> rows = jdbc.sql("""
                select id, category_id, name, description, icon, owner from user_movie_watchlist
                where owner=:owner order by lower(name)
                """).param("owner", username).query((rs, n) -> new Row(
                rs.getLong("id"), rs.getLong("category_id"), rs.getString("name"), rs.getString("description"),
                rs.getString("icon"), rs.getString("owner"))).list();
        return rows.stream().map(this::toDto).toList();
    }

    // Backs the 'watchlist'-scoped category-tree-dialog: direct children of the watchlist's own private anchor
    // (full CRUD, expandable) plus the flat subscribed public categories (leaf, checkbox-only, bell/"Subscribed"
    // badge), merged into one list the same way Guide's subscribed categories appear as normal tree children --
    // just without any physical DAG link backing them.
    @Transactional(readOnly = true)
    public List<CategoryDto> categoryPicker(long watchlistId, List<Long> exclude, String username, boolean isAdmin) {
        Row watchlist = requireWatchlist(watchlistId);
        requireOwnerOrAdmin(watchlist, username, isAdmin);
        Set<Long> excluded = exclude == null ? Set.of() : new HashSet<>(exclude);
        List<CategoryDto> privateChildren = privateCategories.subtree(watchlist.categoryId(), username, isAdmin).stream()
                .filter(category -> !excluded.contains(category.id())).toList();
        List<CategoryDto> subscribed = jdbc.sql("""
                select c.id, c.name, c.description, c.icon
                from category c join movie_watchlist_default_category d on d.category_id=c.id
                where d.watchlist_id=:watchlist order by lower(c.name)
                """).param("watchlist", watchlistId).query((rs, n) -> new CategoryDto(
                rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getString("icon"),
                null, false, true, false, null, true, List.of())).list().stream()
                .filter(category -> !excluded.contains(category.id())).toList();
        List<CategoryDto> merged = new ArrayList<>(privateChildren);
        merged.addAll(subscribed);
        return merged;
    }

    @Transactional
    public void assignMovies(long watchlistId, AssignWatchlistMoviesRequest request, String username, boolean isAdmin) {
        Row watchlist = requireWatchlist(watchlistId);
        requireOwnerOrAdmin(watchlist, username, isAdmin);
        long targetPrivateCategoryId = request.categoryId() == null ? -1 : resolvePrivateTarget(watchlist, request.categoryId());
        List<String> imdbIds = request.imdbIds().stream().distinct().toList();
        imdbIds.forEach(this::requireMovieExists);
        for (String imdbId : imdbIds) {
            if (request.categoryId() == null) {
                jdbc.sql("insert into movie_watchlist_movie(movie_watchlist_id,movie_id) values (:watchlist,:movie) on conflict do nothing")
                        .param("watchlist", watchlistId).param("movie", imdbId).update();
            } else {
                jdbc.sql("insert into movie_private_category(movie_id,private_category_id) values (:movie,:category) on conflict do nothing")
                        .param("movie", imdbId).param("category", targetPrivateCategoryId).update();
            }
        }
    }

    // Mirrors MovieGuideService.removeMovie's scope semantics but only ever touches this watchlist's own tables
    // (movie_watchlist_movie/movie_private_category) -- never the public movie_category, so a movie that only
    // shows up here via a subscribed category can't be removed this way (only by unsubscribing).
    @Transactional
    public void removeMovie(long watchlistId, String imdbId, List<Long> categoryIds, String username, boolean isAdmin) {
        Row watchlist = requireWatchlist(watchlistId);
        requireOwnerOrAdmin(watchlist, username, isAdmin);
        requireMovieExists(imdbId);
        if (categoryIds == null || categoryIds.isEmpty()) {
            jdbc.sql("delete from movie_watchlist_movie where movie_watchlist_id=:watchlist and movie_id=:movie")
                    .param("watchlist", watchlistId).param("movie", imdbId).update();
            jdbc.sql("""
                    delete from movie_private_category
                    where movie_id=:movie
                      and private_category_id in (select descendant_id from private_category_parent_child_all where ancestor_id=:root)
                    """).param("movie", imdbId).param("root", watchlist.categoryId()).update();
            return;
        }
        for (Long categoryId : categoryIds) {
            resolvePrivateTarget(watchlist, categoryId);
        }
        jdbc.sql("""
                delete from movie_private_category
                where movie_id=:movie
                  and private_category_id in (select descendant_id from private_category_parent_child_all where ancestor_id in (:scope))
                """).param("movie", imdbId).param("scope", categoryIds).update();
    }

    // categoryIds empty/null -> default union view. Otherwise every id must resolve the same way (all subscribed
    // public categories, or all private-tree nodes) -- the "Select Category" browse picker only ever sends 0 or 1
    // id; "Delete Movies"'s multi-select picker excludes subscribed categories entirely, so its own selections
    // are always all-private. A caller mixing the two is a client bug, not a real use case.
    @Transactional(readOnly = true)
    public MoviePageDto watchlistMovies(long watchlistId, List<Long> categoryIds, int page, int pageSize, String filter,
                                         String year, String username, boolean isAdmin) {
        Row watchlist = requireWatchlist(watchlistId);
        requireOwnerOrAdmin(watchlist, username, isAdmin);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), pageSize);
        Page<Movie> movies;
        if (categoryIds == null || categoryIds.isEmpty()) {
            movies = movieService.getWatchlistMovies(watchlistId, watchlist.categoryId(), subscribedCategoryIds(watchlistId),
                    filter, year, pageable);
        } else if (categoryIds.stream().allMatch(id -> isSubscribedCategory(watchlistId, id))) {
            movies = movieService.getMovies(pageable, filter, year, categoryIds);
        } else if (categoryIds.stream().allMatch(id -> isWithinWatchlist(watchlist, id))) {
            movies = movieService.getPrivateCategoryMovies(categoryIds, filter, year, pageable);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot mix subscribed and private categories in one selection");
        }
        return new MoviePageDto(movies.getContent().stream().map(movieMapper::toMovieDto).toList(), movies.getTotalElements());
    }

    @Transactional
    public ImportCsvMoviesResponse importCsv(long watchlistId, ImportCsvMoviesRequest request, String username, boolean isAdmin) {
        if (request.movies().size() > MAX_CSV_MOVIES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Too many rows: " + request.movies().size() + " (maximum " + MAX_CSV_MOVIES + ")");
        }
        Row watchlist = requireWatchlist(watchlistId);
        requireOwnerOrAdmin(watchlist, username, isAdmin);
        Long targetCategoryId = request.categoryId() == null ? null : resolvePrivateTarget(watchlist, request.categoryId());
        List<CsvMovieRef> failed = new ArrayList<>();
        for (CsvMovieRef ref : request.movies()) {
            String imdbId = resolveCsvMovie(ref);
            if (imdbId == null) {
                failed.add(ref);
                continue;
            }
            assignToPathsOrTarget(watchlistId, watchlist, imdbId, targetCategoryId, ref.categoryPaths());
        }
        return new ImportCsvMoviesResponse(failed);
    }

    @Transactional
    public void completeCsvImport(long watchlistId, CompleteCsvImportRequest request, String username, boolean isAdmin) {
        Row watchlist = requireWatchlist(watchlistId);
        requireOwnerOrAdmin(watchlist, username, isAdmin);
        Long targetCategoryId = request.categoryId() == null ? null : resolvePrivateTarget(watchlist, request.categoryId());
        for (CsvMovieImport item : request.movies()) {
            Movie movie = movieService.getOrCreateMovie(item.movie());
            assignToPathsOrTarget(watchlistId, watchlist, movie.getImdbId(), targetCategoryId, item.categoryPaths());
        }
    }

    // No suggested paths and no explicit target category -> the watchlist's own flat top level. No suggested
    // paths but a target category was picked -> that private sub-category directly. One or more paths -> each is
    // resolved/created (as a private sub-category) relative to the target (or the watchlist's own anchor when no
    // target was picked) and the movie is linked to every resolved leaf.
    private void assignToPathsOrTarget(long watchlistId, Row watchlist, String imdbId, Long targetCategoryId, List<String> categoryPaths) {
        if (categoryPaths == null || categoryPaths.isEmpty()) {
            if (targetCategoryId == null) {
                jdbc.sql("insert into movie_watchlist_movie(movie_watchlist_id,movie_id) values (:watchlist,:movie) on conflict do nothing")
                        .param("watchlist", watchlistId).param("movie", imdbId).update();
            } else {
                jdbc.sql("insert into movie_private_category(movie_id,private_category_id) values (:movie,:category) on conflict do nothing")
                        .param("movie", imdbId).param("category", targetCategoryId).update();
            }
            return;
        }
        long rootForPaths = targetCategoryId != null ? targetCategoryId : watchlist.categoryId();
        for (String path : categoryPaths) {
            if (path == null || path.isBlank()) continue;
            long leafCategoryId = resolveCategoryPath(rootForPaths, path);
            jdbc.sql("insert into movie_private_category(movie_id,private_category_id) values (:movie,:category) on conflict do nothing")
                    .param("movie", imdbId).param("category", leafCategoryId).update();
        }
    }

    private long resolveCategoryPath(long rootParentId, String dotPath) {
        Long parentId = rootParentId;
        long currentId = -1;
        for (String segment : dotPath.split("\\.")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) continue;
            currentId = getOrCreatePrivateCategory(parentId, trimmed, null);
            parentId = currentId;
        }
        if (currentId < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category path must not be blank: \"" + dotPath + "\"");
        }
        return currentId;
    }

    private String resolveCsvMovie(CsvMovieRef ref) {
        if (ref.imdbId() == null || ref.imdbId().isBlank()) return null;
        String imdbId = ref.imdbId().trim();
        return jdbc.sql("select exists(select 1 from movies where imdb_id=:id)").param("id", imdbId).query(Boolean.class).single()
                ? imdbId : null;
    }

    private long resolveWatchlistsRoot(String owner) {
        long usersId = getOrCreatePrivateCategory(null, "Users", "👥");
        long userId = getOrCreatePrivateCategory(usersId, owner, "👤");
        return getOrCreatePrivateCategory(userId, "Watchlists", "🔖");
    }

    // Validates categoryId is a descendant of the watchlist's own anchor in the private tree (its own anchor
    // counts too, e.g. when CSV import targets the watchlist directly) and returns it unchanged.
    private long resolvePrivateTarget(Row watchlist, long categoryId) {
        if (!isWithinWatchlist(watchlist, categoryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category is not part of this watchlist");
        }
        return categoryId;
    }

    private boolean isWithinWatchlist(Row watchlist, long categoryId) {
        return jdbc.sql("""
                select exists(select 1 from private_category_parent_child_all where ancestor_id=:root and descendant_id=:target)
                """).param("root", watchlist.categoryId()).param("target", categoryId).query(Boolean.class).single();
    }

    private boolean isSubscribedCategory(long watchlistId, long categoryId) {
        return jdbc.sql("select exists(select 1 from movie_watchlist_default_category where watchlist_id=:watchlist and category_id=:category)")
                .param("watchlist", watchlistId).param("category", categoryId).query(Boolean.class).single();
    }

    private List<Long> subscribedCategoryIds(long watchlistId) {
        return jdbc.sql("select category_id from movie_watchlist_default_category where watchlist_id=:watchlist")
                .param("watchlist", watchlistId).query(Long.class).list();
    }

    private WatchlistDto toDto(Row row) {
        return new WatchlistDto(row.id(), row.categoryId(), row.name(), row.description(), row.icon(), row.owner(),
                subscribedCategoryIds(row.id()));
    }

    private Row requireWatchlist(long id) {
        return jdbc.sql("""
                select id, category_id, name, description, icon, owner from user_movie_watchlist where id=:id
                """).param("id", id).query((rs, n) -> new Row(
                rs.getLong("id"), rs.getLong("category_id"), rs.getString("name"), rs.getString("description"),
                rs.getString("icon"), rs.getString("owner")))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist not found"));
    }

    // Private data: only the owner (or MOVIES_ADMIN) may ever see or touch a watchlist -- no MOVIES_GUIDE
    // override, that role is specifically about curating public Guides/Personalities.
    private void requireOwnerOrAdmin(Row watchlist, String username, boolean isAdmin) {
        if (isAdmin) return;
        if (!watchlist.owner().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted to access this watchlist");
        }
    }

    private void requireNameAvailable(String owner, String name, Long excludingId) {
        boolean exists = excludingId == null
                ? jdbc.sql("select exists(select 1 from user_movie_watchlist where owner=:owner and lower(name)=lower(:name))")
                    .param("owner", owner).param("name", name).query(Boolean.class).single()
                : jdbc.sql("select exists(select 1 from user_movie_watchlist where owner=:owner and lower(name)=lower(:name) and id<>:id)")
                    .param("owner", owner).param("name", name).param("id", excludingId).query(Boolean.class).single();
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your watchlist with the name \"" + name + "\" already exists");
        }
    }

    private long getOrCreatePrivateCategory(Long parentId, String name, String icon) {
        return jdbc.sql("select get_or_create_private_category(:parent, :name, :icon)")
                .param("parent", parentId, Types.BIGINT).param("name", name).param("icon", icon, Types.VARCHAR)
                .query(Long.class).single();
    }

    private void requirePublicCategoryExists(long id) {
        if (!jdbc.sql("select exists(select 1 from category where id=:id)").param("id", id).query(Boolean.class).single())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + id);
    }

    private void requireMovieExists(String imdbId) {
        if (!jdbc.sql("select exists(select 1 from movies where imdb_id=:id)").param("id", imdbId).query(Boolean.class).single())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found: " + imdbId);
    }
}
