package com.ivanfranchin.moviesapi.movie.application.service;

import com.ivanfranchin.moviesapi.movie.MovieService;
import com.ivanfranchin.moviesapi.movie.MovieRecommendationService;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.movie.mapper.MovieDtoMapper;
import com.ivanfranchin.moviesapi.movie.model.Movie;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewMovieDetailsUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieDtoMapper movieMapper;

    @Transactional(readOnly = true)
    public MovieDto viewMovie(String imdbId) {
        return movieMapper.toMovieDto(movieService.validateAndGetMovie(imdbId));
    }

    @Transactional(readOnly = true)
    public MovieDto viewMovie(String imdbId, String username) {
        Movie movie = movieService.validateAndGetMovie(imdbId);
        return movieMapper.toMovieDto(movie, movieRecommendationService.isRecommended(username, imdbId));
    }
}
