package com.ivanfranchin.moviesapi.movie;

import com.ivanfranchin.moviesapi.movie.exception.MovieNotFoundException;
import com.ivanfranchin.moviesapi.movie.model.Movie;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class MovieService {

    private final MovieRepository movieRepository;

    @Transactional(readOnly = true)
    public Movie validateAndGetMovie(String imdbId) {
        return movieRepository.findById(imdbId).orElseThrow(() -> new MovieNotFoundException(imdbId));
    }

    @Transactional(readOnly = true)
    public List<Movie> getMovies() {
        return movieRepository.findAll(Sort.by(Sort.Direction.ASC, "title"));
    }

    @Transactional
    public Movie saveMovie(Movie movie) {
        return movieRepository.save(movie);
    }

    @Transactional
    public void deleteMovie(Movie movie) {
        movieRepository.delete(movie);
    }
}
