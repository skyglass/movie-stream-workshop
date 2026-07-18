package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssignGuideMoviesRequest(
        @NotEmpty @Size(max = 500) List<@NotBlank @Size(max = 32) String> imdbIds,
        Long categoryId) {
}
