package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.MovieRecommendationService;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewMovieCatalogUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieDtoMapper movieMapper;

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(Pageable pageable) {
        var movies = movieService.getMovies(pageable);
        return new MoviePageDto(
                movies.getContent().stream()
                        .map(movieMapper::toMovieDto)
                        .toList(),
                movies.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewCatalog(String username, Pageable pageable) {
        Set<String> recommendedMovieIds = movieRecommendationService.recommendedMovieIds(username);
        Set<String> dislikedMovieIds = movieRecommendationService.dislikedMovieIds(username);
        var movies = movieService.getMovies(pageable);
        return new MoviePageDto(
                movies.getContent().stream()
                        .map(movie -> movieMapper.toMovieDto(
                                movie,
                                recommendedMovieIds.contains(movie.getImdbId()),
                                dislikedMovieIds.contains(movie.getImdbId())))
                        .toList(),
                movies.getTotalElements());
    }
}
