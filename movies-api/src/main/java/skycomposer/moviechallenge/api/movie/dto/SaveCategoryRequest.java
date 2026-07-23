package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import skycomposer.moviechallenge.api.movie.model.Operator;

import java.util.List;

public record SaveCategoryRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Size(max = 32) String icon,
        Long parentId,
        @Size(max = 50) List<Long> componentCategoryIds,
        // Private/watchlist categories only: existing PUBLIC categories to use as components alongside
        // componentCategoryIds (which are private-category ids there) -- lets a watchlist's composition include
        // an existing public category, same as today's "Subscribe to Categories" following a public category.
        // Always empty for the public CategoryController/CategoryService path.
        @Size(max = 50) List<Long> publicComponentCategoryIds,
        Operator operator) {

    public SaveCategoryRequest(String name, String description, String icon, Long parentId) {
        this(name, description, icon, parentId, List.of(), List.of(), null);
    }
}
