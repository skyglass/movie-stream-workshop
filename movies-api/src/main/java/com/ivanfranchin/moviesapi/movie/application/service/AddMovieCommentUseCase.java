package com.ivanfranchin.moviesapi.movie.application.service;

import com.ivanfranchin.moviesapi.movie.MovieService;
import com.ivanfranchin.moviesapi.movie.dto.MovieDto;
import com.ivanfranchin.moviesapi.movie.mapper.MovieDtoMapper;
import com.ivanfranchin.moviesapi.movie.model.Movie;
import com.ivanfranchin.moviesapi.movie.model.MovieComment;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AddMovieCommentUseCase {

    private final MovieService movieService;
    private final MovieDtoMapper movieMapper;

    @Transactional
    public MovieDto addComment(AddCommentCommand command) {
        if (command.text() == null || command.text().isBlank()) {
            throw new IllegalArgumentException("Comment text is required");
        }
        Movie movie = movieService.validateAndGetMovie(command.imdbId());
        movie.addComment(new MovieComment(command.username(), command.text(), Instant.now()));
        return movieMapper.toMovieDto(movieService.saveMovie(movie));
    }

    public record AddCommentCommand(String imdbId, String username, String text) {
    }
}
