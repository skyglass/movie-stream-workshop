package skycomposer.moviechallenge.api.movie;

import skycomposer.moviechallenge.api.movie.exception.MovieNotFoundException;
import skycomposer.moviechallenge.api.movie.dto.RecommendMovieRequest;
import skycomposer.moviechallenge.api.movie.model.Movie;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@RequiredArgsConstructor
@Service
public class MovieService {

    private static final int HOMEPAGE_RATING_PRIOR_WEIGHT = 10;

    private final MovieRepository movieRepository;

    @Transactional(readOnly = true)
    public Movie validateAndGetMovie(String imdbId) {
        return movieRepository.findById(imdbId).orElseThrow(() -> new MovieNotFoundException(imdbId));
    }

    @Transactional(readOnly = true)
    public Page<Movie> getMovies(Pageable pageable) {
        return getMovies(pageable, null);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getMovies(Pageable pageable, String filter) {
        return getMovies(pageable, filter, null);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getMovies(Pageable pageable, String filter, String year) {
        return getMovies(pageable, filter, year, List.of());
    }

    @Transactional(readOnly = true)
    public Page<Movie> getMovies(Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        List<Long> categories = categoryParameters(selectedCategories);
        Pageable pageRequest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return movieRepository.findAllByUsersFavoritePopularity(
                normalizedFilter(filter),
                normalizedFilter(year),
                categoryCount(selectedCategories),
                categories,
                HOMEPAGE_RATING_PRIOR_WEIGHT,
                pageRequest);
    }

    @Transactional
    public Movie getOrCreateMovie(RecommendMovieRequest request) {
        return movieRepository.findById(request.imdbId())
                .orElseGet(() -> saveMovie(toMovie(request)));
    }

    @Transactional(readOnly = true)
    public Page<Movie> getFavoriteMovies(String username, Pageable pageable) {
        return getFavoriteMovies(username, pageable, null);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getFavoriteMovies(String username, Pageable pageable, String filter) {
        return getFavoriteMovies(username, pageable, filter, null);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getFavoriteMovies(String username, Pageable pageable, String filter, String year) {
        return getFavoriteMovies(username, pageable, filter, year, List.of());
    }

    @Transactional(readOnly = true)
    public Page<Movie> getFavoriteMovies(String username, Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        return movieRepository.findFavoriteMoviesByUsername(username, normalizedFilter(filter), normalizedFilter(year),
                categoryCount(selectedCategories), categoryParameters(selectedCategories), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getUsersFavoriteMovies(Pageable pageable) {
        return getUsersFavoriteMovies(pageable, null);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getUsersFavoriteMovies(Pageable pageable, String filter) {
        return getUsersFavoriteMovies(pageable, filter, null);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getUsersFavoriteMovies(Pageable pageable, String filter, String year) {
        return getUsersFavoriteMovies(pageable, filter, year, List.of());
    }

    @Transactional(readOnly = true)
    public Page<Movie> getUsersFavoriteMovies(Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        return movieRepository.findUsersFavoriteMovies(normalizedFilter(filter), normalizedFilter(year),
                categoryCount(selectedCategories), categoryParameters(selectedCategories), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getUsersRecommendedMovies(String username, Pageable pageable) {
        return getUsersRecommendedMovies(username, pageable, null);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getUsersRecommendedMovies(String username, Pageable pageable, String filter) {
        return getUsersRecommendedMovies(username, pageable, filter, null);
    }

    @Transactional(readOnly = true)
    public Page<Movie> getUsersRecommendedMovies(String username, Pageable pageable, String filter, String year) {
        return getUsersRecommendedMovies(username, pageable, filter, year, List.of());
    }

    @Transactional(readOnly = true)
    public Page<Movie> getUsersRecommendedMovies(String username, Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        return movieRepository.findUsersRecommendedMoviesByUsername(username, normalizedFilter(filter), normalizedFilter(year),
                categoryCount(selectedCategories), categoryParameters(selectedCategories), pageable);
    }

    @Transactional
    public Movie saveMovie(Movie movie) {
        return movieRepository.save(movie);
    }

    @Transactional
    public void deleteMovie(Movie movie) {
        movieRepository.delete(movie);
    }

    private Movie toMovie(RecommendMovieRequest request) {
        Movie movie = new Movie();
        movie.setImdbId(request.imdbId());
        movie.setTitle(firstNonBlank(request.title(), request.originalTitle(), "N/A"));
        movie.setDirector(firstNonBlank(request.director(), "N/A"));
        movie.setWriter(firstNonBlank(request.writer(), "N/A"));
        movie.setYear(firstNonBlank(request.year(), "N/A"));
        movie.setPoster(request.poster());
        movie.setGenre(trimToNull(request.genre()));
        movie.setCountry(trimToNull(request.country()));
        movie.setType(request.type());
        return movie;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizedFilter(String filter) {
        return filter == null || filter.isBlank() ? null : filter.trim();
    }

    private int categoryCount(List<Long> categories) { return categories == null ? 0 : categories.size(); }

    private List<Long> categoryParameters(List<Long> categories) {
        return categories == null || categories.isEmpty() ? List.of(-1L) : categories.stream().distinct().toList();
    }
}
