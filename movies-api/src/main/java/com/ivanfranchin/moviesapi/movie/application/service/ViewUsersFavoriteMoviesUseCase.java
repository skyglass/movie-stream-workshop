package com.ivanfranchin.moviesapi.movie.application.service;

import com.ivanfranchin.moviesapi.movie.MovieRecommendationService;
import com.ivanfranchin.moviesapi.movie.MovieService;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.movie.mapper.MovieDtoMapper;
import com.ivanfranchin.moviesapi.userextra.UserExtraService;
import com.ivanfranchin.moviesapi.userextra.model.UserExtra;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewUsersFavoriteMoviesUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieDtoMapper movieMapper;
    private final UserExtraService userExtraService;

    @Transactional
    public List<MovieDto> viewUsersFavoriteMovies(Jwt jwt) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return viewUsersFavoriteMovies(userExtra.getUsername());
    }

    @Transactional(readOnly = true)
    public List<MovieDto> viewUsersFavoriteMovies(String username) {
        Set<String> recommendedMovieIds = movieRecommendationService.recommendedMovieIds(username);
        return movieService.getUsersFavoriteMovies().stream()
                .map(movie -> movieMapper.toMovieDto(movie, recommendedMovieIds.contains(movie.getImdbId())))
                .toList();
    }
}
