package skycomposer.moviechallenge.api.movie.dto;

import skycomposer.moviechallenge.api.movie.model.MovieType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// rankPosition/rating mean different things depending on which use case populated them (the viewer's own rank
// almost everywhere, but the Movie Personality's own synthetic rank on the Personality page, or the page owner's
// rank on a public share page) -- viewerRankPosition/viewerRating are always, unconditionally, the actual
// currently-signed-in viewer's own rank, so "Your Rank" can be shown correctly even where rankPosition/rating
// mean something else. usersRankPosition/usersRating are the all-users popularity rank/rating (same score that
// sorts the Home page), independent of any viewer at all.
public record MovieDto(String imdbId, String title, String director, String writer, String year, String poster,
                       String genre, String country, MovieType type, String typeDescription, boolean recommended,
                       boolean disliked, Integer rankPosition, BigDecimal rating,
                       Integer usersRankPosition, BigDecimal usersRating,
                       Integer viewerRankPosition, BigDecimal viewerRating,
                       List<CommentDto> comments) {

    public record CommentDto(String username, String avatar, String text, Instant timestamp) {
    }
}
