package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCourseMovieRequest(
        @NotBlank String movieId,
        @Size(max = 200) String header,
        @Size(max = 10000) String description,
        @Min(1) int watchOrder,
        Long linkedCourseId) {}
