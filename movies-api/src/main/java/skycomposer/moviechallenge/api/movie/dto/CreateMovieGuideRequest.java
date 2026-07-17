package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateMovieGuideRequest(
        @NotBlank @Size(max = 20) String type,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotEmpty @Size(max = 1000) @Valid List<GuideMovieRef> movies) {}
