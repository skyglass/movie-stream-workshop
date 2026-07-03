package skycomposer.moviechallenge.api.movie;

import skycomposer.moviechallenge.api.movie.application.service.ViewFavoriteMoviesUseCase;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static skycomposer.moviechallenge.api.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/favorite-movies")
public class FavoriteMoviesController {

    private final ViewFavoriteMoviesUseCase viewFavoriteMovies;
    private final MoviePaging moviePaging;

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @GetMapping
    public MoviePageDto getFavoriteMovies(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer pageSize) {
        return viewFavoriteMovies.viewFavoriteMovies(jwt, moviePaging.pageable(page, pageSize));
    }
}
