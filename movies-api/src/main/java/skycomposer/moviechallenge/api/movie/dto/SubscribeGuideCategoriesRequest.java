package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubscribeGuideCategoriesRequest(@NotNull @Size(max = 200) List<Long> categoryIds) {
}
