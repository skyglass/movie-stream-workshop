package skycomposer.moviechallenge.api.movie.mapper;

import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.dto.MovieRatingDto;
import skycomposer.moviechallenge.api.movie.model.Movie;
import skycomposer.moviechallenge.api.movie.model.MovieComment;
import skycomposer.moviechallenge.api.userextra.UserExtraService;
import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
@Component
public class MovieDtoMapper {

    private final UserExtraService userExtraService;

    public MovieDto toMovieDto(Movie movie) {
        return toMovieDto(movie, false);
    }

    public MovieDto toMovieDto(Movie movie, boolean recommended) {
        return toMovieDto(movie, recommended, false);
    }

    public MovieDto toMovieDto(Movie movie, boolean recommended, boolean disliked) {
        return toMovieDto(movie, recommended, disliked, null);
    }

    public MovieDto toMovieDto(Movie movie, boolean recommended, boolean disliked, MovieRatingDto rating) {
        return toMovieDto(movie, recommended, disliked, rating, null);
    }

    public MovieDto toMovieDto(Movie movie, boolean recommended, boolean disliked, MovieRatingDto rating,
                               MovieRatingDto usersPopularity) {
        return toMovieDto(movie, recommended, disliked, rating, usersPopularity, null);
    }

    public MovieDto toMovieDto(Movie movie, boolean recommended, boolean disliked, MovieRatingDto rating,
                               MovieRatingDto usersPopularity, MovieRatingDto viewerRating) {
        List<MovieDto.CommentDto> comments = movie.getComments().stream()
                .map(this::toMovieDtoCommentDto)
                .toList();

        return new MovieDto(
                movie.getImdbId(),
                movie.getTitle(),
                movie.getDirector(),
                movie.getWriter(),
                movie.getYear(),
                movie.getPoster(),
                movie.getGenre(),
                movie.getCountry(),
                movie.getType(),
                movie.getType().getDescription(),
                recommended,
                disliked,
                rating == null ? null : rating.rankPosition(),
                rating == null ? null : rating.rating(),
                usersPopularity == null ? null : usersPopularity.rankPosition(),
                usersPopularity == null ? null : usersPopularity.rating(),
                viewerRating == null ? null : viewerRating.rankPosition(),
                viewerRating == null ? null : viewerRating.rating(),
                comments
        );
    }

    public MovieDto.CommentDto toMovieDtoCommentDto(MovieComment comment) {
        String username = comment.getUsername();
        String avatar = getAvatarForUser(username);
        String text = comment.getText();
        Instant timestamp = comment.getTimestamp();

        return new MovieDto.CommentDto(username, avatar, text, timestamp);
    }

    private String getAvatarForUser(String username) {
        return userExtraService.getUserExtra(username)
                .map(UserExtra::getAvatar)
                .orElse(username);
    }
}
