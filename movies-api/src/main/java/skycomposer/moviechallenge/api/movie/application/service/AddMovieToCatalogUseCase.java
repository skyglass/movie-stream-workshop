package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieRequest;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import skycomposer.moviechallenge.api.movie.model.Movie;
import skycomposer.moviechallenge.api.movie.model.MovieType;
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
        movie.setWriter(command.writer());
        movie.setYear(command.year());
        movie.setPoster(command.poster());
        movie.setGenre(command.genre());
        movie.setCountry(command.country());
        movie.setType(command.type());
        return movieMapper.toMovieDto(movieService.saveMovie(movie));
    }

    public MovieDto addMovie(CreateMovieRequest request) {
        return addMovie(new AddMovieCommand(
                request.imdbId(),
                request.title(),
                request.director(),
                request.writer(),
                request.year(),
                request.poster(),
                request.genre(),
                request.country(),
                request.type()));
    }

    public record AddMovieCommand(String imdbId, String title, String director, String writer, String year, String poster,
                                  String genre, String country, MovieType type) {
        public AddMovieCommand {
            type = type == null ? MovieType.MOVIE : type;
        }
    }
}
