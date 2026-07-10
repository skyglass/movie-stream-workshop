package skycomposer.moviechallenge.api.movie;

import skycomposer.moviechallenge.api.movie.dto.AddCommentRequest;
import skycomposer.moviechallenge.api.movie.application.service.AddMovieCommentUseCase;
import skycomposer.moviechallenge.api.movie.application.service.AddMovieCommentUseCase.AddCommentCommand;
import skycomposer.moviechallenge.api.movie.application.service.AddMovieToCatalogUseCase;
import skycomposer.moviechallenge.api.movie.application.service.AdministerMovieCatalogUseCase;
import skycomposer.moviechallenge.api.movie.application.service.AdministerMovieCatalogUseCase.UpdateMovieCommand;
import skycomposer.moviechallenge.api.movie.application.service.RecommendMovieUseCase;
import skycomposer.moviechallenge.api.movie.application.service.ViewMovieCatalogUseCase;
import skycomposer.moviechallenge.api.movie.application.service.ViewMovieDetailsUseCase;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieRequest;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.dto.MovieRankHistoryDto;
import skycomposer.moviechallenge.api.movie.dto.RecommendMovieRequest;
import skycomposer.moviechallenge.api.userextra.dto.UpdateMovieRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

import static skycomposer.moviechallenge.api.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/movies")
public class MoviesController {

    private final ViewMovieCatalogUseCase viewMovieCatalog;
    private final ViewMovieDetailsUseCase viewMovieDetails;
    private final AddMovieToCatalogUseCase addMovieToCatalog;
    private final AddMovieCommentUseCase addMovieComment;
    private final AdministerMovieCatalogUseCase administerMovieCatalog;
    private final RecommendMovieUseCase recommendMovie;
    private final MoviePaging moviePaging;

    @GetMapping
    public MoviePageDto getMovies(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer pageSize,
                                  @RequestParam(required = false) String filter) {
        String username = username(jwt);
        var pageable = moviePaging.pageable(page, pageSize);
        return username == null
                ? viewMovieCatalog.viewCatalog(pageable, filter)
                : viewMovieCatalog.viewCatalog(username, pageable, filter);
    }

    @GetMapping("/{imdbId}")
    public MovieDto getMovie(@PathVariable String imdbId, @AuthenticationPrincipal Jwt jwt) {
        String username = username(jwt);
        return username == null ? viewMovieDetails.viewMovie(imdbId) : viewMovieDetails.viewMovie(imdbId, username);
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @GetMapping("/{imdbId}/rank-history")
    public MovieRankHistoryDto getMovieRankHistory(@PathVariable String imdbId, @AuthenticationPrincipal Jwt jwt) {
        return viewMovieDetails.viewRankHistory(imdbId, username(jwt));
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public MovieDto createMovie(@Valid @RequestBody CreateMovieRequest createMovieRequest) {
        return addMovieToCatalog.addMovie(createMovieRequest);
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @PutMapping("/{imdbId}")
    public MovieDto updateMovie(@PathVariable String imdbId, @Valid @RequestBody UpdateMovieRequest updateMovieRequest) {
        return administerMovieCatalog.updateMovie(new UpdateMovieCommand(
                imdbId,
                updateMovieRequest.title(),
                updateMovieRequest.director(),
                updateMovieRequest.writer(),
                updateMovieRequest.year(),
                updateMovieRequest.poster(),
                updateMovieRequest.genre(),
                updateMovieRequest.country(),
                updateMovieRequest.type()));
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @DeleteMapping("/{imdbId}")
    public MovieDto deleteMovie(@PathVariable String imdbId) {
        return administerMovieCatalog.deleteMovie(imdbId);
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @PostMapping("/{imdbId}/recommendation")
    public MovieDto recommendMovie(@PathVariable String imdbId, @AuthenticationPrincipal Jwt jwt) {
        return recommendMovie.recommendMovie(jwt, imdbId);
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @PostMapping("/recommendation")
    public MovieDto recommendMovie(@Valid @RequestBody RecommendMovieRequest recommendMovieRequest,
                                   @AuthenticationPrincipal Jwt jwt) {
        return recommendMovie.recommendMovie(jwt, recommendMovieRequest);
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @DeleteMapping("/{imdbId}/recommendation")
    public MovieDto unrecommendMovie(@PathVariable String imdbId, @AuthenticationPrincipal Jwt jwt) {
        return recommendMovie.unrecommendMovie(jwt, imdbId);
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @PostMapping("/{imdbId}/recommendation/replay")
    public MovieDto replayMovie(@PathVariable String imdbId, @AuthenticationPrincipal Jwt jwt) {
        return recommendMovie.replayMovie(jwt, imdbId);
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @PostMapping("/{imdbId}/recommendation/dislike")
    public MovieDto dislikeMovie(@PathVariable String imdbId, @AuthenticationPrincipal Jwt jwt) {
        return recommendMovie.dislikeMovie(jwt, imdbId);
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{imdbId}/comments")
    public MovieDto addMovieComment(@PathVariable String imdbId,
                                    @Valid @RequestBody AddCommentRequest addCommentRequest,
                                    Principal principal) {
        return addMovieComment.addComment(new AddCommentCommand(imdbId, principal.getName(), addCommentRequest.text()));
    }

    private String username(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        return firstNonBlank(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("username"),
                jwt.getSubject());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
