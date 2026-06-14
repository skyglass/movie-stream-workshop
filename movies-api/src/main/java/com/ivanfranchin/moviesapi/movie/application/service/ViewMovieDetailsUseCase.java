package com.ivanfranchin.moviesapi.movie.application.service;

import com.ivanfranchin.moviesapi.movie.MovieService;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.movie.mapper.MovieDtoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewMovieDetailsUseCase {

    private final MovieService movieService;
    private final MovieDtoMapper movieMapper;

    @Transactional(readOnly = true)
    public MovieDto viewMovie(String imdbId) {
        return movieMapper.toMovieDto(movieService.validateAndGetMovie(imdbId));
    }
}
