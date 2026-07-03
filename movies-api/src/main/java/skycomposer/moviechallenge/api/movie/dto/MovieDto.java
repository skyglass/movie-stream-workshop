package skycomposer.moviechallenge.api.movie.dto;

import skycomposer.moviechallenge.api.movie.model.MovieType;

import java.time.Instant;
import java.util.List;

public record MovieDto(String imdbId, String title, String director, String writer, String year, String poster,
                       String genre, String country, MovieType type, String typeDescription, boolean recommended,
                       boolean disliked, List<CommentDto> comments) {

    public record CommentDto(String username, String avatar, String text, Instant timestamp) {
    }
}
