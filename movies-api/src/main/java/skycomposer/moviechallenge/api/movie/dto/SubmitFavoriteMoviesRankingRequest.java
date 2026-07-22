package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitFavoriteMoviesRankingRequest(
        @NotEmpty @Size(max = 50000) List<String> orderedImdbIds) {
}
