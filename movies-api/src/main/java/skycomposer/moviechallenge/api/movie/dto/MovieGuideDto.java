package skycomposer.moviechallenge.api.movie.dto;

import java.util.List;

public record MovieGuideDto(
        long id,
        long categoryId,
        String type,
        String name,
        String description,
        String icon,
        String owner,
        List<Long> subscribedCategoryIds) {
}
