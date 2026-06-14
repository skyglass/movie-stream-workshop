package com.ivanfranchin.moviesapi.movie.application.service;

import com.ivanfranchin.moviesapi.movie.MovieService;
import com.ivanfranchin.moviesapi.movie.dto.CreateMovieRequest;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.movie.mapper.MovieDtoMapper;
import com.ivanfranchin.moviesapi.movie.model.Movie;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AddMovieToCatalogUseCase {

    private final MovieService movieService;
    private final MovieDtoMapper movieMapper;

    @Transactional
    public MovieDto addMovie(AddMovieCommand command) {
        Movie movie = new Movie();
        movie.setImdbId(command.imdbId());
        movie.setTitle(command.title());
        movie.setDirector(command.director());
        movie.setYear(command.year());
        movie.setPoster(command.poster());
        return movieMapper.toMovieDto(movieService.saveMovie(movie));
    }

    public MovieDto addMovie(CreateMovieRequest request) {
        return addMovie(new AddMovieCommand(
                request.imdbId(),
                request.title(),
                request.director(),
                request.year(),
                request.poster()));
    }

    public record AddMovieCommand(String imdbId, String title, String director, String year, String poster) {
    }
}
