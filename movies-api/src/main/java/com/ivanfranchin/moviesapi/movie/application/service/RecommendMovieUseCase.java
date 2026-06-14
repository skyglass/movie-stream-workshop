package com.ivanfranchin.moviesapi.movie.application.service;

import com.ivanfranchin.moviesapi.movie.MovieRecommendedEvent;
import com.ivanfranchin.moviesapi.movie.MovieRecommendationService;
import com.ivanfranchin.moviesapi.movie.MovieService;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.movie.mapper.MovieDtoMapper;
import com.ivanfranchin.moviesapi.movie.model.Movie;
import com.ivanfranchin.moviesapi.userextra.UserExtraService;
import com.ivanfranchin.moviesapi.userextra.model.UserExtra;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class RecommendMovieUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieDtoMapper movieMapper;
    private final UserExtraService userExtraService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MovieDto recommendMovie(Jwt jwt, String imdbId) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return recommendMovie(userExtra.getUsername(), imdbId);
    }

    @Transactional
    public MovieDto recommendMovie(String username, String email, String imdbId) {
        syncUser(username, email);
        return recommendMovie(username, imdbId);
    }

    @Transactional
    public MovieDto unrecommendMovie(Jwt jwt, String imdbId) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return unrecommendMovie(userExtra.getUsername(), imdbId);
    }

    @Transactional
    public MovieDto unrecommendMovie(String username, String email, String imdbId) {
        syncUser(username, email);
        return unrecommendMovie(username, imdbId);
    }

    private MovieDto recommendMovie(String username, String imdbId) {
        Movie movie = movieService.validateAndGetMovie(imdbId);
        movieRecommendationService.recommend(username, imdbId);
        eventPublisher.publishEvent(new MovieRecommendedEvent(username));
        return movieMapper.toMovieDto(movie, true);
    }

    private MovieDto unrecommendMovie(String username, String imdbId) {
        Movie movie = movieService.validateAndGetMovie(imdbId);
        movieRecommendationService.unrecommend(username, imdbId);
        return movieMapper.toMovieDto(movie, false);
    }

    private void syncUser(String username, String email) {
        UserExtra userExtra = userExtraService.getUserExtra(username)
                .orElseGet(() -> new UserExtra(username, email));
        userExtra.setEmail(email);
        userExtraService.saveUserExtra(userExtra);
    }
}
