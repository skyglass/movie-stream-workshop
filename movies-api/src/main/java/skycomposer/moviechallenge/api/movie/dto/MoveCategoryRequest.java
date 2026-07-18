package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotNull;

public record MoveCategoryRequest(
        @NotNull Long sourceParentId,
        Long targetParentId,
        boolean copy) {
}
