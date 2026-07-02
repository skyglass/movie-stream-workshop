package com.ivanfranchin.moviesapi.movie;

import com.ivanfranchin.moviesapi.movie.application.service.ViewUsersFavoriteMoviesUseCase;
import com.ivanfranchin.moviesapi.movie.dto.MoviePageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.ivanfranchin.moviesapi.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users-favorite-movies")
public class UsersFavoriteMoviesController {

    private final ViewUsersFavoriteMoviesUseCase viewUsersFavoriteMovies;
    private final MoviePaging moviePaging;

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @GetMapping
    public MoviePageDto getUsersFavoriteMovies(@AuthenticationPrincipal Jwt jwt,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer pageSize) {
        return viewUsersFavoriteMovies.viewUsersFavoriteMovies(jwt, moviePaging.pageable(page, pageSize));
    }
}
