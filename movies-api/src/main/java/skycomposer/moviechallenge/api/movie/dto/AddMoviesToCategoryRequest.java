package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AddMoviesToCategoryRequest(
        @NotEmpty List<String> imdbIds) {
}
