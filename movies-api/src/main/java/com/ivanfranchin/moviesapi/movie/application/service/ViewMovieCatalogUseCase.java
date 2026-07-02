package com.ivanfranchin.moviesapi.movie.application.service;

import com.ivanfranchin.moviesapi.movie.MovieService;
import com.ivanfranchin.moviesapi.movie.MovieRecommendationService;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.movie.dto.MoviePageDto;
import com.ivanfranchin.moviesapi.movie.mapper.MovieDtoMapper;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewMovieCatalogUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieDtoMapper movieMapper;

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(Pageable pageable) {
        var movies = movieService.getMovies(pageable);
        return new MoviePageDto(
                movies.getContent().stream()
                        .map(movieMapper::toMovieDto)
                        .toList(),
                movies.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(String username, Pageable pageable) {
        Set<String> recommendedMovieIds = movieRecommendationService.recommendedMovieIds(username);
        var movies = movieService.getMovies(pageable);
        return new MoviePageDto(
                movies.getContent().stream()
                        .map(movie -> movieMapper.toMovieDto(movie, recommendedMovieIds.contains(movie.getImdbId())))
                        .toList(),
                movies.getTotalElements());
    }
}
