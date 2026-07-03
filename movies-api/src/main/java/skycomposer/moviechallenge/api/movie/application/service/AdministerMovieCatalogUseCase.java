package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import skycomposer.moviechallenge.api.movie.model.Movie;
import skycomposer.moviechallenge.api.movie.model.MovieType;
import skycomposer.moviechallenge.api.userextra.dto.UpdateMovieRequest;
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
                new UpdateMovieRequest(command.title(), command.director(), command.writer(), command.year(), command.poster(),
                        command.genre(), command.country(), command.type()),
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

    public record UpdateMovieCommand(String imdbId, String title, String director, String writer, String year, String poster,
                                     String genre, String country, MovieType type) {
    }
}
