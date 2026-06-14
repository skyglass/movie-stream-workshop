package com.ivanfranchin.moviesapi.movie.application.service;

import com.ivanfranchin.moviesapi.movie.MovieService;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.movie.mapper.MovieDtoMapper;
import com.ivanfranchin.moviesapi.movie.model.Movie;
import com.ivanfranchin.moviesapi.userextra.dto.UpdateMovieRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AdministerMovieCatalogUseCase {

    private final MovieService movieService;
    private final MovieDtoMapper movieMapper;

    @Transactional
    public MovieDto updateMovie(UpdateMovieCommand command) {
        Movie movie = movieService.validateAndGetMovie(command.imdbId());
        Movie.updateFrom(
                new UpdateMovieRequest(command.title(), command.director(), command.year(), command.poster()),
                movie);
        return movieMapper.toMovieDto(movieService.saveMovie(movie));
    }

    @Transactional
    public MovieDto deleteMovie(String imdbId) {
        Movie movie = movieService.validateAndGetMovie(imdbId);
        MovieDto deleted = movieMapper.toMovieDto(movie);
        movieService.deleteMovie(movie);
        return deleted;
    }

    public record UpdateMovieCommand(String imdbId, String title, String director, String year, String poster) {
    }
}
