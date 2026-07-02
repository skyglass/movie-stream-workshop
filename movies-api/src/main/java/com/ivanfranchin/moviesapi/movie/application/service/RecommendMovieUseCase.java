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
        return recommendExistingMovie(userExtra.getUsername(), imdbId);
    }

    @Transactional
    public MovieDto recommendMovie(String username, String imdbId) {
        syncUser(username);
        return recommendExistingMovie(username, imdbId);
    }

    @Transactional
    public MovieDto unrecommendMovie(Jwt jwt, String imdbId) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return unrecommendExistingMovie(userExtra.getUsername(), imdbId);
    }

    @Transactional
    public MovieDto unrecommendMovie(String username, String imdbId) {
        syncUser(username);
        return unrecommendExistingMovie(username, imdbId);
    }

    @Transactional
    public MovieDto dislikeMovie(Jwt jwt, String imdbId) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return dislikeExistingMovie(userExtra.getUsername(), imdbId);
    }

    @Transactional
    public MovieDto dislikeMovie(String username, String imdbId) {
        syncUser(username);
        return dislikeExistingMovie(username, imdbId);
    }

    private MovieDto recommendExistingMovie(String username, String imdbId) {
        Movie movie = movieService.validateAndGetMovie(imdbId);
        movieRecommendationService.recommend(username, imdbId);
        eventPublisher.publishEvent(new MovieRecommendedEvent(username));
        return movieMapper.toMovieDto(movie, true, false);
    }

    private MovieDto unrecommendExistingMovie(String username, String imdbId) {
        Movie movie = movieService.validateAndGetMovie(imdbId);
        movieRecommendationService.unrecommend(username, imdbId);
        return movieMapper.toMovieDto(movie, false, false);
    }

    private MovieDto dislikeExistingMovie(String username, String imdbId) {
        Movie movie = movieService.validateAndGetMovie(imdbId);
        movieRecommendationService.dislike(username, imdbId);
        return movieMapper.toMovieDto(movie, false, true);
    }

    private void syncUser(String username) {
        String syntheticEmail = UserExtra.emailForUsername(username);
        UserExtra userExtra = userExtraService.getUserExtra(username)
                .orElseGet(() -> new UserExtra(username, syntheticEmail));
        userExtra.setEmail(syntheticEmail);
        userExtraService.saveUserExtra(userExtra);
    }
}
