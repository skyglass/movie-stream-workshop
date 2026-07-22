package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieRecommendationService;
import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoEnricher;
import skycomposer.moviechallenge.api.userextra.UserExtraService;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import java.util.Set;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewUsersFavoriteMoviesUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieDtoEnricher movieDtoEnricher;
    private final UserExtraService userExtraService;

    @Transactional
    public MoviePageDto viewUsersFavoriteMovies(Jwt jwt, Pageable pageable) {
        return viewUsersFavoriteMovies(jwt, pageable, null);
    }

    @Transactional
    public MoviePageDto viewUsersFavoriteMovies(Jwt jwt, Pageable pageable, String filter) {
        return viewUsersFavoriteMovies(jwt, pageable, filter, null);
    }

    @Transactional
    public MoviePageDto viewUsersFavoriteMovies(Jwt jwt, Pageable pageable, String filter, String year) {
        return viewUsersFavoriteMovies(jwt, pageable, filter, year, List.of());
    }

    @Transactional
    public MoviePageDto viewUsersFavoriteMovies(Jwt jwt, Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return viewUsersFavoriteMovies(userExtra.getUsername(), pageable, filter, year, selectedCategories);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewUsersFavoriteMovies(String username, Pageable pageable) {
        return viewUsersFavoriteMovies(username, pageable, null);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewUsersFavoriteMovies(String username, Pageable pageable, String filter) {
        return viewUsersFavoriteMovies(username, pageable, filter, null);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewUsersFavoriteMovies(String username, Pageable pageable, String filter, String year) {
        return viewUsersFavoriteMovies(username, pageable, filter, year, List.of());
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewUsersFavoriteMovies(String username, Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        Set<String> recommendedMovieIds = movieRecommendationService.recommendedMovieIds(username);
        var movies = movieService.getUsersFavoriteMovies(pageable, filter, year, selectedCategories);
        return new MoviePageDto(
                movieDtoEnricher.toMovieDtos(movies.getContent(), recommendedMovieIds, Set.of(), username, username),
                movies.getTotalElements());
    }
}
