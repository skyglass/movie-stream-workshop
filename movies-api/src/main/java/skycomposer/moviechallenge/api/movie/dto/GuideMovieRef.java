package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GuideMovieRef(
        @NotBlank @Size(max = 32) String imdbId,
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 500) String> categories) {}
