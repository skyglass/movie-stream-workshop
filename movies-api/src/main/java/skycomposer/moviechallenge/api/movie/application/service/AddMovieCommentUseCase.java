package skycomposer.moviechallenge.api.movie.application.service;

import skycomposer.moviechallenge.api.movie.MovieChallengeRepository;
import skycomposer.moviechallenge.api.movie.MovieService;
import skycomposer.moviechallenge.api.movie.MovieRecommendationService;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.mapper.MovieDtoMapper;
import skycomposer.moviechallenge.api.movie.model.Movie;
import skycomposer.moviechallenge.api.movie.model.MovieComment;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AddMovieCommentUseCase {

    private final MovieService movieService;
    private final MovieRecommendationService movieRecommendationService;
    private final MovieChallengeRepository movieChallengeRepository;
    private final MovieDtoMapper movieMapper;

    @Transactional
    public MovieDto addComment(AddCommentCommand command) {
        if (command.text() == null || command.text().isBlank()) {
            throw new IllegalArgumentException("Comment text is required");
        }
        Movie movie = movieService.validateAndGetMovie(command.imdbId());
        movie.addComment(new MovieComment(command.username(), command.text(), Instant.now()));
        Movie savedMovie = movieService.saveMovie(movie);
        return movieMapper.toMovieDto(
                savedMovie,
                movieRecommendationService.isRecommended(command.username(), command.imdbId()),
                movieRecommendationService.isDisliked(command.username(), command.imdbId()),
                movieChallengeRepository.movieRating(command.username(), command.imdbId()).orElse(null));
    }

    public record AddCommentCommand(String imdbId, String username, String text) {
    }
}
