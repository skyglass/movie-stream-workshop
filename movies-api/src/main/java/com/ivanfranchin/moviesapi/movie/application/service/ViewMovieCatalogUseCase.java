package com.ivanfranchin.moviesapi.movie.application.service;

import com.ivanfranchin.moviesapi.movie.MovieService;
import com.ivanfranchin.moviesapi.movie.MovieRecommendationService;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.movie.mapper.MovieDtoMapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewMovieCatalogUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieDtoMapper movieMapper;

    @Transactional(readOnly = true)
    public List<MovieDto> viewCatalog() {
        return movieService.getMovies().stream()
                .map(movieMapper::toMovieDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovieDto> viewCatalog(String username) {
        Set<String> recommendedMovieIds = movieRecommendationService.recommendedMovieIds(username);
        return movieService.getMovies().stream()
                .map(movie -> movieMapper.toMovieDto(movie, recommendedMovieIds.contains(movie.getImdbId())))
                .toList();
    }
}
