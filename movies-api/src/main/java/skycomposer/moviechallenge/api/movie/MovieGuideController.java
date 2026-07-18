package skycomposer.moviechallenge.api.movie;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import skycomposer.moviechallenge.api.movie.dto.AssignGuideMoviesRequest;
import skycomposer.moviechallenge.api.movie.dto.CompleteCsvImportRequest;
import skycomposer.moviechallenge.api.movie.dto.CompleteMovieGuideRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateGuideRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieGuideRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieGuideResponse;
import skycomposer.moviechallenge.api.movie.dto.ImportCsvMoviesRequest;
import skycomposer.moviechallenge.api.movie.dto.ImportCsvMoviesResponse;
import skycomposer.moviechallenge.api.movie.dto.MovieGuideDto;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/movie-guides")
public class MovieGuideController {
    private final MovieGuideService movieGuides;

    // Step 1 of the interactive creation wizard: create the guide (and its anchor category) and subscribe it to
    // any selected categories, in one transaction. Open to any authenticated user -- they become the guide's
    // owner. Distinct path from the JSON-upload flow's own POST /api/movie-guides below.
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/wizard")
    public MovieGuideDto createWizard(@Valid @RequestBody CreateGuideRequest request, Authentication authentication) {
        return movieGuides.createGuide(request, authentication.getName());
    }

    // Lets a guide page decide whether to show/resume the creation wizard: compare `owner` to the current user
    // and `status` client-side. Public read, same as GET /api/categories.
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

    // Step 2 of the interactive wizard: assign movies picked in MovieSelector to the guide's own category (or one
    // of its native sub-categories). Distinct path from the JSON-upload flow's own POST /{id}/movies below.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{id}/wizard-movies")
    public void assignMovies(@PathVariable long id, @Valid @RequestBody AssignGuideMoviesRequest request,
                              Authentication authentication) {
        movieGuides.assignMovies(id, request, authentication.getName(), isAdminOrGuide(authentication));
    }

    // Ends the interactive wizard: STARTED -> COMPLETED. From then on, visiting the guide's page (even as the
    // owner) shows the normal, full guide view instead of resuming Step 2.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{id}/complete")
    public void completeWizard(@PathVariable long id, Authentication authentication) {
        movieGuides.completeGuide(id, authentication.getName(), isAdminOrGuide(authentication));
    }

    // Movies already assigned to the guide, excluding ones that only show up there via a subscribed category --
    // backs the interactive wizard's Step 2 list (must reflect only what the curator explicitly added).
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

    // --- JSON-upload bulk-import flow (paste a hand-crafted/LLM-generated JSON file) ---

    // Dot-separated category paths, auto-created as needed. Gated to MOVIES_GUIDE/MOVIES_ADMIN in SecurityConfig,
    // since a single request can create arbitrarily many categories (bounded by MAX_CATEGORIES_WITH_CREATION).
    @PostMapping
    public CreateMovieGuideResponse create(@Valid @RequestBody CreateMovieGuideRequest request) {
        return movieGuides.createGuide(request);
    }

    @PostMapping("/{id}/movies")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable long id, @Valid @RequestBody CompleteMovieGuideRequest request) {
        movieGuides.completeGuide(id, request);
    }

    // Same dot-separated category paths, but nothing is ever created — a path that doesn't already fully
    // resolve is silently dropped. Open to any authenticated user in SecurityConfig.
    @PostMapping("/existing")
    public CreateMovieGuideResponse createExistingOnly(@Valid @RequestBody CreateMovieGuideRequest request) {
        return movieGuides.createGuideExistingOnly(request);
    }

    @PostMapping("/{id}/movies/existing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeExistingOnly(@PathVariable long id, @Valid @RequestBody CompleteMovieGuideRequest request) {
        movieGuides.completeGuideExistingOnly(id, request);
    }

    private boolean isAdminOrGuide(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_MOVIES_ADMIN") || authority.equals("ROLE_MOVIES_GUIDE"));
    }
}
