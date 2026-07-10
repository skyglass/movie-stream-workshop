package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SubmitMovieChallengesRequest(
        @NotEmpty List<@Valid SelectMovieChallengeRequest> selections) {
}
