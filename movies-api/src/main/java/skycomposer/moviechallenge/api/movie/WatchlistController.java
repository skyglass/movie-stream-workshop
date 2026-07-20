package skycomposer.moviechallenge.api.movie;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import skycomposer.moviechallenge.api.movie.dto.AssignWatchlistMoviesRequest;
import skycomposer.moviechallenge.api.movie.dto.CategoryDto;
import skycomposer.moviechallenge.api.movie.dto.CompleteCsvImportRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateWatchlistRequest;
import skycomposer.moviechallenge.api.movie.dto.ImportCsvMoviesRequest;
import skycomposer.moviechallenge.api.movie.dto.ImportCsvMoviesResponse;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.dto.RemoveWatchlistMovieRequest;
import skycomposer.moviechallenge.api.movie.dto.SubscribeWatchlistCategoriesRequest;
import skycomposer.moviechallenge.api.movie.dto.UpdateWatchlistRequest;
import skycomposer.moviechallenge.api.movie.dto.WatchlistDto;

import java.util.List;

// "My Watchlists": unlike /api/movie-guides, every endpoint here requires authentication and every handler is
// additionally ownership-checked in WatchlistService (owner-or-MOVIES_ADMIN) -- these are private, never
// shareable/public like a Movie Guide.
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/watchlists")
public class WatchlistController {
    private final WatchlistService watchlists;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public WatchlistDto create(@Valid @RequestBody CreateWatchlistRequest request, Authentication authentication) {
        return watchlists.createWatchlist(request, authentication.getName());
    }

    @GetMapping("/mine")
    public List<WatchlistDto> mine(Authentication authentication) {
        return watchlists.myWatchlists(authentication.getName());
    }

    @GetMapping("/{id}")
    public WatchlistDto getById(@PathVariable long id, Authentication authentication) {
        return watchlists.getById(id, authentication.getName(), isAdmin(authentication));
    }

    @GetMapping("/by-category/{categoryId}")
    public WatchlistDto getByCategory(@PathVariable long categoryId, Authentication authentication) {
        return watchlists.getByCategory(categoryId, authentication.getName(), isAdmin(authentication));
    }

    @PutMapping("/{id}")
    public WatchlistDto update(@PathVariable long id, @Valid @RequestBody UpdateWatchlistRequest request,
                                Authentication authentication) {
        return watchlists.updateWatchlist(id, request, authentication.getName(), isAdmin(authentication));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id, Authentication authentication) {
        watchlists.deleteWatchlist(id, authentication.getName(), isAdmin(authentication));
    }

    @PostMapping("/{id}/subscribe")
    public WatchlistDto subscribe(@PathVariable long id, @Valid @RequestBody SubscribeWatchlistCategoriesRequest request,
                                   Authentication authentication) {
        return watchlists.subscribeCategories(id, request.categoryIds(), authentication.getName(), isAdmin(authentication));
    }

    // Merged "Select Category" source: direct children of the watchlist's own private anchor, plus its flat
    // subscribed public categories -- see WatchlistService.categoryPicker.
    @GetMapping("/{id}/categories")
    public List<CategoryDto> categoryPicker(@PathVariable long id, @RequestParam(required = false) List<Long> exclude,
                                             Authentication authentication) {
        return watchlists.categoryPicker(id, exclude == null ? List.of() : exclude, authentication.getName(), isAdmin(authentication));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{id}/movies")
    public void assignMovies(@PathVariable long id, @Valid @RequestBody AssignWatchlistMoviesRequest request,
                              Authentication authentication) {
        watchlists.assignMovies(id, request, authentication.getName(), isAdmin(authentication));
    }

    @GetMapping("/{id}/movies")
    public MoviePageDto movies(@PathVariable long id,
                                @RequestParam(required = false, defaultValue = "1") int page,
                                @RequestParam(required = false, defaultValue = "50") int pageSize,
                                @RequestParam(required = false) String filter,
                                @RequestParam(required = false) String year,
                                @RequestParam(required = false) List<Long> categoryIds,
                                Authentication authentication) {
        return watchlists.watchlistMovies(id, categoryIds == null ? List.of() : categoryIds, page, pageSize, filter, year,
                authentication.getName(), isAdmin(authentication));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{id}/movies/{imdbId}/remove")
    public void removeMovie(@PathVariable long id, @PathVariable String imdbId,
                             @RequestBody(required = false) RemoveWatchlistMovieRequest request, Authentication authentication) {
        List<Long> categoryIds = request == null ? List.of() : request.categoryIds();
        watchlists.removeMovie(id, imdbId, categoryIds, authentication.getName(), isAdmin(authentication));
    }

    @PostMapping("/{id}/import-csv")
    public ImportCsvMoviesResponse importCsv(@PathVariable long id, @Valid @RequestBody ImportCsvMoviesRequest request,
                                              Authentication authentication) {
        return watchlists.importCsv(id, request, authentication.getName(), isAdmin(authentication));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{id}/import-csv/complete")
    public void completeCsvImport(@PathVariable long id, @Valid @RequestBody CompleteCsvImportRequest request,
                                   Authentication authentication) {
        watchlists.completeCsvImport(id, request, authentication.getName(), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_MOVIES_ADMIN"));
    }
}
