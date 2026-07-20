package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateWatchlistRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Size(max = 32) String icon,
        @NotNull @Size(max = 200) List<Long> subscribedCategoryIds) {
}
