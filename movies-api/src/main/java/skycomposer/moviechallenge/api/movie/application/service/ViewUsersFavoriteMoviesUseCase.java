package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieRecommendationService;
import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import skycomposer.moviechallenge.api.userextra.UserExtraService;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import java.util.Set;
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
    private final MovieDtoMapper movieMapper;
    private final UserExtraService userExtraService;

    @Transactional
    public MoviePageDto viewUsersFavoriteMovies(Jwt jwt, Pageable pageable) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return viewUsersFavoriteMovies(userExtra.getUsername(), pageable);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewUsersFavoriteMovies(String username, Pageable pageable) {
        Set<String> recommendedMovieIds = movieRecommendationService.recommendedMovieIds(username);
        var movies = movieService.getUsersFavoriteMovies(pageable);
        return new MoviePageDto(
                movies.getContent().stream()
                        .map(movie -> movieMapper.toMovieDto(movie, recommendedMovieIds.contains(movie.getImdbId())))
                        .toList(),
                movies.getTotalElements());
    }
}
