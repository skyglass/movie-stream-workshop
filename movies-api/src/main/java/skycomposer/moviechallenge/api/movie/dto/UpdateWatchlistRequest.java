package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWatchlistRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Size(max = 32) String icon) {
}
