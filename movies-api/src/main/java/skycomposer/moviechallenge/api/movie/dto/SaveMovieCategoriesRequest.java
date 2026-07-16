package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SaveMovieCategoriesRequest(
        @NotNull List<Long> addedCategories,
        @NotNull List<Long> removedCategories) {
}
