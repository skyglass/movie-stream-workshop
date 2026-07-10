package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieChallengeRepository;
import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.MovieRecommendationService;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.dto.MovieRankHistoryDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import skycomposer.moviechallenge.api.movie.model.Movie;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ViewMovieDetailsUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieChallengeRepository movieChallengeRepository;
    private final MovieDtoMapper movieMapper;

    @Transactional(readOnly = true)
    public MovieDto viewMovie(String imdbId) {
        return movieMapper.toMovieDto(movieService.validateAndGetMovie(imdbId));
    }

    @Transactional(readOnly = true)
    public MovieDto viewMovie(String imdbId, String username) {
        Movie movie = movieService.validateAndGetMovie(imdbId);
        return movieMapper.toMovieDto(
                movie,
                movieRecommendationService.isRecommended(username, imdbId),
                movieRecommendationService.isDisliked(username, imdbId),
                movieChallengeRepository.movieRating(username, imdbId).orElse(null));
    }

    @Transactional(readOnly = true)
    public MovieRankHistoryDto viewRankHistory(String imdbId, String username) {
        movieService.validateAndGetMovie(imdbId);
        return movieChallengeRepository.findRankHistory(username, imdbId);
    }
}
