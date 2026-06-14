package com.ivanfranchin.moviesapi.movie;

import com.ivanfranchin.moviesapi.movie.application.service.MovieChallengeUseCase;
import com.ivanfranchin.moviesapi.movie.dto.MovieChallengeDto;
import com.ivanfranchin.moviesapi.movie.dto.SelectMovieChallengeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.ivanfranchin.moviesapi.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/movie-challenges")
public class MovieChallengesController {

    private final MovieChallengeUseCase movieChallengeUseCase;

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @GetMapping("/next")
    public ResponseEntity<MovieChallengeDto> nextChallenge(@AuthenticationPrincipal Jwt jwt) {
        return movieChallengeUseCase.nextChallenge(jwt)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
    @ResponseStatus(NO_CONTENT)
    @PostMapping("/votes")
    public void selectMovie(@Valid @RequestBody SelectMovieChallengeRequest request,
                            @AuthenticationPrincipal Jwt jwt) {
        movieChallengeUseCase.selectMovie(jwt, request.movie1Id(), request.movie2Id(), request.selectedMovieId());
    }
}
