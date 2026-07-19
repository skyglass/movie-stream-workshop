package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RemoveGuideMovieRequest(@NotEmpty @Size(max = 200) List<Long> categoryIds) {
}
