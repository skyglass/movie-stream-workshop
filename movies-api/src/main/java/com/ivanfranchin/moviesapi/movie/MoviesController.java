package com.ivanfranchin.moviesapi.movie;

import com.ivanfranchin.moviesapi.movie.dto.AddCommentRequest;
import com.ivanfranchin.moviesapi.movie.application.service.AddMovieCommentUseCase;
import com.ivanfranchin.moviesapi.movie.application.service.AddMovieCommentUseCase.AddCommentCommand;
import com.ivanfranchin.moviesapi.movie.application.service.AddMovieToCatalogUseCase;
import com.ivanfranchin.moviesapi.movie.application.service.AdministerMovieCatalogUseCase;
import com.ivanfranchin.moviesapi.movie.application.service.AdministerMovieCatalogUseCase.UpdateMovieCommand;
import com.ivanfranchin.moviesapi.movie.application.service.ViewMovieCatalogUseCase;
import com.ivanfranchin.moviesapi.movie.application.service.ViewMovieDetailsUseCase;
import com.ivanfranchin.moviesapi.movie.dto.CreateMovieRequest;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.userextra.dto.UpdateMovieRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

import static com.ivanfranchin.moviesapi.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/movies")
public class MoviesController {

    private final ViewMovieCatalogUseCase viewMovieCatalog;
    private final ViewMovieDetailsUseCase viewMovieDetails;
    private final AddMovieToCatalogUseCase addMovieToCatalog;
    private final AddMovieCommentUseCase addMovieComment;
    private final AdministerMovieCatalogUseCase administerMovieCatalog;

    @GetMapping
    public List<MovieDto> getMovies() {
        return viewMovieCatalog.viewCatalog();
    }

    @GetMapping("/{imdbId}")
    public MovieDto getMovie(@PathVariable String imdbId) {
        return viewMovieDetails.viewMovie(imdbId);
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
                updateMovieRequest.year(),
                updateMovieRequest.poster()));
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @DeleteMapping("/{imdbId}")
    public MovieDto deleteMovie(@PathVariable String imdbId) {
        return administerMovieCatalog.deleteMovie(imdbId);
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{imdbId}/comments")
    public MovieDto addMovieComment(@PathVariable String imdbId,
                                    @Valid @RequestBody AddCommentRequest addCommentRequest,
                                    Principal principal) {
        return addMovieComment.addComment(new AddCommentCommand(imdbId, principal.getName(), addCommentRequest.text()));
    }
}
