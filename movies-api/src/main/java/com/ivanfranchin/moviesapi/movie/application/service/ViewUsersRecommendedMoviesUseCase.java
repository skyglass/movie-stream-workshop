package com.ivanfranchin.moviesapi.movie.application.service;

import com.ivanfranchin.moviesapi.movie.MovieRecommendationService;
import com.ivanfranchin.moviesapi.movie.MovieService;
import com.ivanfranchin.moviesapi.movie.dto.MoviePageDto;
import com.ivanfranchin.moviesapi.movie.mapper.MovieDtoMapper;
import com.ivanfranchin.moviesapi.userextra.UserExtraService;
import com.ivanfranchin.moviesapi.userextra.model.UserExtra;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewUsersRecommendedMoviesUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieDtoMapper movieMapper;
    private final UserExtraService userExtraService;

    @Transactional
    public MoviePageDto viewUsersRecommendedMovies(Jwt jwt, Pageable pageable) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return viewUsersRecommendedMovies(userExtra.getUsername(), pageable);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewUsersRecommendedMovies(String username, Pageable pageable) {
        Set<String> recommendedMovieIds = movieRecommendationService.recommendedMovieIds(username);
        var movies = movieService.getUsersRecommendedMovies(username, pageable);
        return new MoviePageDto(
                movies.getContent().stream()
                        .map(movie -> movieMapper.toMovieDto(movie, recommendedMovieIds.contains(movie.getImdbId())))
                        .toList(),
                movies.getTotalElements());
    }
}
