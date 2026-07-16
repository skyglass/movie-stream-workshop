package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieChallengeRepository;
import skycomposer.moviechallenge.api.movie.MovieRecommendationService;
import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.dto.MovieRatingDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import skycomposer.moviechallenge.api.movie.model.Movie;
import skycomposer.moviechallenge.api.userextra.UserExtraService;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import java.util.Map;
import java.util.Set;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewFavoriteMoviesUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieChallengeRepository movieChallengeRepository;
    private final MovieDtoMapper movieMapper;
    private final UserExtraService userExtraService;

    @Transactional
    public MoviePageDto viewFavoriteMovies(Jwt jwt, Pageable pageable) {
        return viewFavoriteMovies(jwt, pageable, null);
    }

    @Transactional
    public MoviePageDto viewFavoriteMovies(Jwt jwt, Pageable pageable, String filter) {
        return viewFavoriteMovies(jwt, pageable, filter, null);
    }

    @Transactional
    public MoviePageDto viewFavoriteMovies(Jwt jwt, Pageable pageable, String filter, String year) {
        return viewFavoriteMovies(jwt, pageable, filter, year, List.of());
    }

    @Transactional
    public MoviePageDto viewFavoriteMovies(Jwt jwt, Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return viewFavoriteMovies(userExtra.getUsername(), pageable, filter, year, selectedCategories);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewFavoriteMovies(String username, Pageable pageable) {
        return viewFavoriteMovies(username, pageable, null);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewFavoriteMovies(String username, Pageable pageable, String filter) {
        return viewFavoriteMovies(username, pageable, filter, null);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewFavoriteMovies(String username, Pageable pageable, String filter, String year) {
        return viewFavoriteMovies(username, pageable, filter, year, List.of());
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewFavoriteMovies(String username, Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        Set<String> recommendedMovieIds = movieRecommendationService.recommendedMovieIds(username);
        var movies = movieService.getFavoriteMovies(username, pageable, filter, year, selectedCategories);
        Map<String, MovieRatingDto> ratings = movieChallengeRepository.movieRatings(
                username,
                movies.getContent().stream().map(Movie::getImdbId).toList());
        return new MoviePageDto(
                movies.getContent().stream()
                        .map(movie -> movieMapper.toMovieDto(
                                movie,
                                recommendedMovieIds.contains(movie.getImdbId()),
                                false,
                                ratings.get(movie.getImdbId())))
                        .toList(),
                movies.getTotalElements());
    }
}
