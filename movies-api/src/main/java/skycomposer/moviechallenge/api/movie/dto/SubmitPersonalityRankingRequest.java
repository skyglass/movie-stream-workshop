package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitPersonalityRankingRequest(@NotEmpty @Size(max = 20000) List<String> orderedImdbIds) {
}
