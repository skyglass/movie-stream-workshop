package com.ivanfranchin.moviesapi.movie;

import com.ivanfranchin.moviesapi.movie.application.service.ViewUsersRecommendedMoviesUseCase;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.ivanfranchin.moviesapi.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users-recommended-movies")
public class UsersRecommendedMoviesController {

    private final ViewUsersRecommendedMoviesUseCase viewUsersRecommendedMovies;

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @GetMapping
    public List<MovieDto> getUsersRecommendedMovies(@AuthenticationPrincipal Jwt jwt) {
        return viewUsersRecommendedMovies.viewUsersRecommendedMovies(jwt);
    }
}
