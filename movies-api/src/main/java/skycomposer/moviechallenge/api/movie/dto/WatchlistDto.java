package skycomposer.moviechallenge.api.movie.dto;

import java.util.List;

public record WatchlistDto(
        long id,
        long categoryId,
        String name,
        String description,
        String icon,
        String owner,
        List<Long> subscribedCategoryIds) {
}
