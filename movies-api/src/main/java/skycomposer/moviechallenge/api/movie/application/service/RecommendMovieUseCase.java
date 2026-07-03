package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieRecommendationService;
import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.dto.RecommendMovieRequest;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import skycomposer.moviechallenge.api.movie.model.Movie;
import skycomposer.moviechallenge.api.userextra.UserExtraService;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class RecommendMovieUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieDtoMapper movieMapper;
    private final UserExtraService userExtraService;

    @Transactional
    public MovieDto recommendMovie(Jwt jwt, String imdbId) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return recommendExistingMovie(userExtra.getUsername(), imdbId);
    }

    @Transactional
    public MovieDto recommendMovie(Jwt jwt, RecommendMovieRequest request) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        Movie movie = movieService.getOrCreateMovie(request);
        movieRecommendationService.recommend(userExtra.getUsername(), movie.getImdbId());
        return movieMapper.toMovieDto(movie, true, false);
    }

    @Transactional
    public MovieDto recommendMovie(String username, String imdbId) {
        syncUser(username);
        return recommendExistingMovie(username, imdbId);
    }

    @Transactional
    public MovieDto unrecommendMovie(Jwt jwt, String imdbId) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return unrecommendExistingMovie(userExtra.getUsername(), imdbId);
    }

    @Transactional
    public MovieDto unrecommendMovie(String username, String imdbId) {
        syncUser(username);
        return unrecommendExistingMovie(username, imdbId);
    }

    @Transactional
    public MovieDto dislikeMovie(Jwt jwt, String imdbId) {
        UserExtra userExtra = userExtraService.syncFromJwt(jwt);
        return dislikeExistingMovie(userExtra.getUsername(), imdbId);
    }

    @Transactional
    public MovieDto dislikeMovie(String username, String imdbId) {
        syncUser(username);
        return dislikeExistingMovie(username, imdbId);
    }

    private MovieDto recommendExistingMovie(String username, String imdbId) {
        Movie movie = movieService.validateAndGetMovie(imdbId);
        movieRecommendationService.recommend(username, imdbId);
        return movieMapper.toMovieDto(movie, true, false);
    }

    private MovieDto unrecommendExistingMovie(String username, String imdbId) {
        Movie movie = movieService.validateAndGetMovie(imdbId);
        movieRecommendationService.unrecommend(username, imdbId);
        return movieMapper.toMovieDto(movie, false, false);
    }

    private MovieDto dislikeExistingMovie(String username, String imdbId) {
        Movie movie = movieService.validateAndGetMovie(imdbId);
        movieRecommendationService.dislike(username, imdbId);
        return movieMapper.toMovieDto(movie, false, true);
    }

    private void syncUser(String username) {
        String syntheticEmail = UserExtra.emailForUsername(username);
        UserExtra userExtra = userExtraService.getUserExtra(username)
                .orElseGet(() -> new UserExtra(username, syntheticEmail));
        userExtra.setEmail(syntheticEmail);
        userExtraService.saveUserExtra(userExtra);
    }
}
