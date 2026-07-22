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
public class ViewFavoriteMoviesUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieDtoEnricher movieDtoEnricher;
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

    // The owner's own page (private view): subject and viewer are the same person, so "Your Rank" and whatever
    // rankPosition/rating already mean here coincide -- no need for the two-username overload below.
    @Transactional(readOnly = true)
    public MoviePageDto viewFavoriteMovies(String username, Pageable pageable, String filter, String year, List<Long> selectedCategories) {
        return viewFavoriteMovies(username, username, pageable, filter, year, selectedCategories);
    }

    // Public share page: subjectUsername is the page owner (whose favorites these are, and whose rank
    // rankPosition/rating already carry); viewerUsername is the actual signed-in visitor (null if anonymous, or
    // if they're a different person than the owner) -- only used to populate viewerRankPosition/viewerRating so
    // "Your Rank" reflects the visitor, not the page owner.
    @Transactional(readOnly = true)
    public MoviePageDto viewFavoriteMovies(String subjectUsername, String viewerUsername, Pageable pageable,
                                            String filter, String year, List<Long> selectedCategories) {
        Set<String> recommendedMovieIds = movieRecommendationService.recommendedMovieIds(subjectUsername);
        var movies = movieService.getFavoriteMovies(subjectUsername, pageable, filter, year, selectedCategories);
        return new MoviePageDto(
                movieDtoEnricher.toMovieDtos(movies.getContent(), recommendedMovieIds, Set.of(), subjectUsername, viewerUsername),
                movies.getTotalElements());
    }
}
