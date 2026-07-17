package skycomposer.moviechallenge.api.movie;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import skycomposer.moviechallenge.api.movie.dto.CompleteMovieGuideRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieGuideRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieGuideResponse;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/movie-guides")
public class MovieGuideController {
    private final MovieGuideService movieGuides;

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
}
