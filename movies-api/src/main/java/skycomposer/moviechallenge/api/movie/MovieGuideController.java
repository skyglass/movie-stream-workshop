package skycomposer.moviechallenge.api.movie;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import skycomposer.moviechallenge.api.movie.dto.AssignGuideMoviesRequest;
import skycomposer.moviechallenge.api.movie.dto.CompleteCsvImportRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateGuideRequest;
import skycomposer.moviechallenge.api.movie.dto.ImportCsvMoviesRequest;
import skycomposer.moviechallenge.api.movie.dto.ImportCsvMoviesResponse;
import skycomposer.moviechallenge.api.movie.dto.MovieGuideDto;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.dto.SubscribeGuideCategoriesRequest;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/movie-guides")
public class MovieGuideController {
    private final MovieGuideService movieGuides;

    // Creates the guide (and its anchor category) with just its name/description/icon. Open to any authenticated
    // user -- they become the guide's owner. On success the client navigates straight to the guide's own page.
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/wizard")
    public MovieGuideDto createWizard(@Valid @RequestBody CreateGuideRequest request, Authentication authentication) {
        return movieGuides.createGuide(request, authentication.getName());
    }

    // Backs the guide page's "Subscribe to Categories" dialog: subscribes an already-created guide to additional
    // categories, on demand (not tied to any creation flow).
    @PostMapping("/{id}/subscribe")
    public MovieGuideDto subscribe(@PathVariable long id, @Valid @RequestBody SubscribeGuideCategoriesRequest request,
                                    Authentication authentication) {
        return movieGuides.subscribeCategories(id, request.categoryIds(), authentication.getName(), isAdminOrGuide(authentication));
    }

    // Public read, same as GET /api/categories.
    @GetMapping("/by-category/{categoryId}")
    public MovieGuideDto getByCategory(@PathVariable long categoryId) {
        return movieGuides.getByCategory(categoryId);
    }

    // Category ids of every guide the current user owns -- lets the Movie Guides/Personalities list show a
    // "Delete" action only on the rows the viewer actually owns, without an N+1 lookup per row.
    @GetMapping("/mine")
    public List<Long> mine(Authentication authentication) {
        return movieGuides.myGuideCategoryIds(authentication.getName());
    }

    // Backs the guide page's "Add Movies" action (MovieSelector): assigns movies to the guide's own category, or
    // one of its native sub-categories.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{id}/wizard-movies")
    public void assignMovies(@PathVariable long id, @Valid @RequestBody AssignGuideMoviesRequest request,
                              Authentication authentication) {
        movieGuides.assignMovies(id, request, authentication.getName(), isAdminOrGuide(authentication));
    }

    // Movies already assigned to the guide, excluding ones that only show up there via a subscribed category --
    // backs the guide page's movie list (must reflect only what the curator explicitly added).
    @GetMapping("/{id}/movies")
    public MoviePageDto guideMovies(@PathVariable long id,
                                     @RequestParam(required = false, defaultValue = "1") int page,
                                     @RequestParam(required = false, defaultValue = "50") int pageSize,
                                     @RequestParam(required = false) String filter,
                                     @RequestParam(required = false) String year) {
        return movieGuides.guideMovies(id, page, pageSize, filter, year);
    }

    // CSV import (default guide view "Import from CSV" dialog, owner/MOVIES_GUIDE/MOVIES_ADMIN only) Phase 1: see
    // MovieGuideService.importCsv for the resolve-by-imdbId-or-title+year and one-transaction-except-failures
    // semantics.
    @PostMapping("/{id}/import-csv")
    public ImportCsvMoviesResponse importCsv(@PathVariable long id, @Valid @RequestBody ImportCsvMoviesRequest request,
                                              Authentication authentication) {
        return movieGuides.importCsv(id, request, authentication.getName(), isAdminOrGuide(authentication));
    }

    // CSV import Phase 2b: full movie payloads resolved from OMDb (Phase 2a, client-side) for rows Phase 1
    // couldn't find in the catalog.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{id}/import-csv/complete")
    public void completeCsvImport(@PathVariable long id, @Valid @RequestBody CompleteCsvImportRequest request,
                                   Authentication authentication) {
        movieGuides.completeCsvImport(id, request, authentication.getName(), isAdminOrGuide(authentication));
    }

    private boolean isAdminOrGuide(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_MOVIES_ADMIN") || authority.equals("ROLE_MOVIES_GUIDE"));
    }
}
