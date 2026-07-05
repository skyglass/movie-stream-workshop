package skycomposer.moviechallenge.api.movie.dto;

import java.math.BigDecimal;

public record MovieRatingDto(Integer rankPosition, BigDecimal rating) {
}
