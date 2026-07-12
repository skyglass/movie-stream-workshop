package skycomposer.moviechallenge.api.movie.dto;

import java.math.BigDecimal;

public record SuggestedMovieChallengeDto(SuggestedMovieChallengeMovieDto movie1,
                                         SuggestedMovieChallengeMovieDto movie2) {

    public record SuggestedMovieChallengeMovieDto(String imdbId,
                                                  String title,
                                                  String poster,
                                                  String year,
                                                  String director,
                                                  int winProbabilityPercent,
                                                  Integer rankPosition,
                                                  BigDecimal rating) {
    }
}
