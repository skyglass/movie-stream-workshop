package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.MovieRecommendationService;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoEnricher;
import java.util.Set;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewMovieCatalogUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieDtoEnricher movieDtoEnricher;

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(Pageable pageable) {
        return viewCatalog(pageable, null);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(Pageable pageable, String filter) {
        return viewCatalog(pageable, filter, null);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(Pageable pageable, String filter, String year) {
        return viewCatalog(pageable, filter, year, List.of());
    }

    // Anonymous (no viewer at all) -- recommended/disliked/rating/viewerRating are all meaningless here, but
    // usersPopularity ("Users Rank") isn't viewer-dependent, so it still populates.
    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        var movies = movieService.getMovies(pageable, filter, year, selectedCategories);
        return new MoviePageDto(
                movieDtoEnricher.toMovieDtos(movies.getContent(), Set.of(), Set.of(), null, null),
                movies.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(String username, Pageable pageable) {
        return viewCatalog(username, pageable, null);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(String username, Pageable pageable, String filter) {
        Set<String> recommendedMovieIds = movieRecommendationService.recommendedMovieIds(username);
        Set<String> dislikedMovieIds = movieRecommendationService.dislikedMovieIds(username);
        var movies = movieService.getMovies(pageable, filter);
        return new MoviePageDto(
                movieDtoEnricher.toMovieDtos(movies.getContent(), recommendedMovieIds, dislikedMovieIds, username, username),
                movies.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(String username, Pageable pageable, String filter, String year) {
        return viewCatalog(username, pageable, filter, year, List.of());
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(String username, Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        return viewCatalog(username, pageable, filter, year, selectedCategories, false);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(String username, Pageable pageable, String filter, String year,
                                     List<Long> selectedCategories, boolean onlyNotRecommended) {
        Set<String> recommendedMovieIds = movieRecommendationService.recommendedMovieIds(username);
        Set<String> dislikedMovieIds = movieRecommendationService.dislikedMovieIds(username);
        var movies = movieService.getMovies(pageable, filter, year, selectedCategories, username, onlyNotRecommended);
        return new MoviePageDto(
                movieDtoEnricher.toMovieDtos(movies.getContent(), recommendedMovieIds, dislikedMovieIds, username, username),
                movies.getTotalElements());
    }
}
