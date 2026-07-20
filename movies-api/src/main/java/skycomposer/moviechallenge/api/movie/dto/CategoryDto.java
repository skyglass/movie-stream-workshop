package skycomposer.moviechallenge.api.movie.dto;

import java.util.List;

public record CategoryDto(
        long id,
        String name,
        String description,
        String icon,
        Long parentId,
        boolean checked,
        boolean leaf,
        boolean empty,
        Long referencedCategoryId,
        boolean subscribed,
        List<CategoryDto> children) {
}
