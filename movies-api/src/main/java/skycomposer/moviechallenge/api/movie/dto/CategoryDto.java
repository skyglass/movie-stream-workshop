package skycomposer.moviechallenge.api.movie.dto;

import skycomposer.moviechallenge.api.movie.model.Operator;

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
        List<CategoryDto> children,
        Operator operator,
        List<CategoryComponentDto> components) {

    // A plain (non-composable) category: operator=null, no components.
    public CategoryDto(long id, String name, String description, String icon, Long parentId, boolean checked,
                       boolean leaf, boolean empty, List<CategoryDto> children) {
        this(id, name, description, icon, parentId, checked, leaf, empty, children, null, List.of());
    }
}
