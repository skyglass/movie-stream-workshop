package com.ivanfranchin.moviesapi.movie;

import com.ivanfranchin.moviesapi.movie.exception.MovieNotFoundException;
import com.ivanfranchin.moviesapi.movie.model.Movie;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MovieService {

    private final MovieRepository movieRepository;

    @Transactional(readOnly = true)
    public Movie validateAndGetMovie(String imdbId) {
        return movieRepository.findById(imdbId).orElseThrow(() -> new MovieNotFoundException(imdbId));
    }

    @Transactional(readOnly = true)
    public Page<Movie> getMovies(Pageable pageable) {
        Sort titleSort = Sort.by(Sort.Direction.ASC, "title").and(Sort.by(Sort.Direction.ASC, "imdbId"));
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), titleSort);
        return movieRepository.findAll(sortedPageable);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getFavoriteMovies(String username, Pageable pageable) {
        return movieRepository.findFavoriteMoviesByUsername(username, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getUsersFavoriteMovies(Pageable pageable) {
        return movieRepository.findUsersFavoriteMovies(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getUsersRecommendedMovies(String username, Pageable pageable) {
        return movieRepository.findUsersRecommendedMoviesByUsername(username, pageable);
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
