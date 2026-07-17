package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GuideMovieDetails(
        @Valid @NotNull RecommendMovieRequest movie,
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 500) String> categories) {}
