package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import skycomposer.moviechallenge.api.movie.model.Movie;
import skycomposer.moviechallenge.api.userextra.UserExtraService;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Every candidate this use case returns is guaranteed to not already be recommended (liked or disliked) by the
// viewer, by construction of MovieService.getCategorySimilarMovies -- unlike ViewFavoriteMoviesUseCase/
// ViewUsersRecommendedMoviesUseCase, there's no need to look up recommendedMovieIds/ratings maps to decorate the
// DTO, since they'd always come back false/null for this movie set.
@RequiredArgsConstructor
@Service
public class ViewCategorySimilarMoviesUseCase {

    private final MovieService movieService;
    private final MovieDtoMapper movieMapper;
    private final UserExtraService userExtraService;

    @Transactional(readOnly = true)
    public MoviePageDto viewSimilarToFavorites(Jwt jwt, Pageable pageable, String filter, String year,
                                               List<Long> selectedCategories) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return viewSimilarToFavorites(userExtra.getUsername(), pageable, filter, year, selectedCategories);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewSimilarToFavorites(String username, Pageable pageable, String filter, String year,
                                               List<Long> selectedCategories) {
        return viewSimilarMovies(username, null, pageable, filter, year, selectedCategories);
    }

    // Unlike viewSimilarToFavorites above, this has no jwt-based overload: the single-movie variant is reachable
    // by anonymous viewers (Movie Details page), so the controller always resolves username itself (nullable,
    // same null-safe helper as MoviesController.getMovie) rather than requiring a JWT to sync a UserExtra from.
    @Transactional(readOnly = true)
    public MoviePageDto viewSimilarMoviesTo(String username, String imdbId, Pageable pageable, String filter,
                                            String year, List<Long> selectedCategories) {
        movieService.validateAndGetMovie(imdbId);
        return viewSimilarMovies(username, imdbId, pageable, filter, year, selectedCategories);
    }

    private MoviePageDto viewSimilarMovies(String username, String seedMovieId, Pageable pageable, String filter,
                                           String year, List<Long> selectedCategories) {
        Page<Movie> movies = movieService.getCategorySimilarMovies(
                username, seedMovieId, pageable, filter, year, selectedCategories);
        return new MoviePageDto(
                movies.getContent().stream().map(movie -> movieMapper.toMovieDto(movie, false, false, null)).toList(),
                movies.getTotalElements());
    }
}
