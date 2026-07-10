package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieChallengeRepository;
import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.MovieRecommendationService;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.dto.MovieRatingDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import skycomposer.moviechallenge.api.movie.model.Movie;
import java.util.Set;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewMovieCatalogUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieChallengeRepository movieChallengeRepository;
    private final MovieDtoMapper movieMapper;

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(Pageable pageable) {
        return viewCatalog(pageable, null);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(Pageable pageable, String filter) {
        var movies = movieService.getMovies(pageable, filter);
        return new MoviePageDto(
                movies.getContent().stream()
                        .map(movieMapper::toMovieDto)
                        .toList(),
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
        Map<String, MovieRatingDto> ratings = movieChallengeRepository.movieRatings(
                username,
                movies.getContent().stream().map(Movie::getImdbId).toList());
        return new MoviePageDto(
                movies.getContent().stream()
                        .map(movie -> movieMapper.toMovieDto(
                                movie,
                                recommendedMovieIds.contains(movie.getImdbId()),
                                dislikedMovieIds.contains(movie.getImdbId()),
                                ratings.get(movie.getImdbId())))
                        .toList(),
                movies.getTotalElements());
    }
}
