package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoEnricher;
import skycomposer.moviechallenge.api.movie.model.Movie;
import skycomposer.moviechallenge.api.userextra.UserExtraService;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Every candidate this use case returns is guaranteed to not already be recommended (liked or disliked) by
// :username, by construction of MovieService.getCategorySimilarMovies -- so rankPosition/rating (subject =
// :username) always come back null regardless of who :username is, and recommended/disliked are always false.
// viewerRankPosition/viewerRating are a separate, genuine lookup though: on a public share page, a different
// signed-in visitor could easily have their own real rating for one of these "unrated by the page owner"
// candidates, so viewerUsername is still worth threading through for "Your Rank" there.
@RequiredArgsConstructor
@Service
public class ViewCategorySimilarMoviesUseCase {

    private final MovieService movieService;
    private final MovieDtoEnricher movieDtoEnricher;
    private final UserExtraService userExtraService;

    @Transactional(readOnly = true)
    public MoviePageDto viewSimilarToFavorites(Jwt jwt, Pageable pageable, String filter, String year,
                                               List<Long> selectedCategories) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return viewSimilarToFavorites(userExtra.getUsername(), pageable, filter, year, selectedCategories);
    }

    // Owner's own page: subject and viewer are the same person.
    @Transactional(readOnly = true)
    public MoviePageDto viewSimilarToFavorites(String username, Pageable pageable, String filter, String year,
                                               List<Long> selectedCategories) {
        return viewSimilarToFavorites(username, username, pageable, filter, year, selectedCategories);
    }

    // Public share page: subjectUsername is the page owner (whose favorites the candidates are similar to);
    // viewerUsername is the actual signed-in visitor, only used for viewerRankPosition/viewerRating.
    @Transactional(readOnly = true)
    public MoviePageDto viewSimilarToFavorites(String subjectUsername, String viewerUsername, Pageable pageable,
                                               String filter, String year, List<Long> selectedCategories) {
        return viewSimilarMovies(subjectUsername, viewerUsername, null, pageable, filter, year, selectedCategories);
    }

    // Unlike viewSimilarToFavorites above, this has no jwt-based overload: the single-movie variant is reachable
    // by anonymous viewers (Movie Details page), so the controller always resolves username itself (nullable,
    // same null-safe helper as MoviesController.getMovie) rather than requiring a JWT to sync a UserExtra from.
    // Subject and viewer are the same single resolved username here -- there's no separate "page owner" concept
    // for this single-movie variant.
    @Transactional(readOnly = true)
    public MoviePageDto viewSimilarMoviesTo(String username, String imdbId, Pageable pageable, String filter,
                                            String year, List<Long> selectedCategories) {
        movieService.validateAndGetMovie(imdbId);
        return viewSimilarMovies(username, username, imdbId, pageable, filter, year, selectedCategories);
    }

    private MoviePageDto viewSimilarMovies(String subjectUsername, String viewerUsername, String seedMovieId,
                                           Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        Page<Movie> movies = movieService.getCategorySimilarMovies(
                subjectUsername, seedMovieId, pageable, filter, year, selectedCategories);
        return new MoviePageDto(
                movieDtoEnricher.toMovieDtos(movies.getContent(), Set.of(), Set.of(), null, viewerUsername),
                movies.getTotalElements());
    }
}
