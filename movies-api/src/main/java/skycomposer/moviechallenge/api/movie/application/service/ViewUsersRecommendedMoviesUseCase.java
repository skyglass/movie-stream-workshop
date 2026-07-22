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
public class ViewUsersRecommendedMoviesUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieDtoEnricher movieDtoEnricher;
    private final UserExtraService userExtraService;

    @Transactional
    public MoviePageDto viewUsersRecommendedMovies(Jwt jwt, Pageable pageable) {
        return viewUsersRecommendedMovies(jwt, pageable, null);
    }

    @Transactional
    public MoviePageDto viewUsersRecommendedMovies(Jwt jwt, Pageable pageable, String filter) {
        return viewUsersRecommendedMovies(jwt, pageable, filter, null);
    }

    @Transactional
    public MoviePageDto viewUsersRecommendedMovies(Jwt jwt, Pageable pageable, String filter, String year) {
        return viewUsersRecommendedMovies(jwt, pageable, filter, year, List.of());
    }

    @Transactional
    public MoviePageDto viewUsersRecommendedMovies(Jwt jwt, Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return viewUsersRecommendedMovies(userExtra.getUsername(), pageable, filter, year, selectedCategories);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewUsersRecommendedMovies(String username, Pageable pageable) {
        return viewUsersRecommendedMovies(username, pageable, null);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewUsersRecommendedMovies(String username, Pageable pageable, String filter) {
        return viewUsersRecommendedMovies(username, pageable, filter, null);
    }

    @Transactional(readOnly = true)
    public MoviePageDto viewUsersRecommendedMovies(String username, Pageable pageable, String filter, String year) {
        return viewUsersRecommendedMovies(username, pageable, filter, year, List.of());
    }

    // Owner's own page: subject and viewer are the same person.
    @Transactional(readOnly = true)
    public MoviePageDto viewUsersRecommendedMovies(String username, Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        return viewUsersRecommendedMovies(username, username, pageable, filter, year, selectedCategories);
    }

    // Public share page: subjectUsername is the page owner (whose recommendations these are, and whose rank
    // rankPosition/rating already carry); viewerUsername is the actual signed-in visitor, only used for
    // viewerRankPosition/viewerRating so "Your Rank" reflects the visitor, not the page owner.
    @Transactional(readOnly = true)
    public MoviePageDto viewUsersRecommendedMovies(String subjectUsername, String viewerUsername, Pageable pageable,
                                                    String filter, String year, List<Long> selectedCategories) {
        Set<String> recommendedMovieIds = movieRecommendationService.recommendedMovieIds(subjectUsername);
        var movies = movieService.getUsersRecommendedMovies(subjectUsername, pageable, filter, year, selectedCategories);
        return new MoviePageDto(
                movieDtoEnricher.toMovieDtos(movies.getContent(), recommendedMovieIds, Set.of(), subjectUsername, viewerUsername),
                movies.getTotalElements());
    }
}
