package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddCourseMovieFromSearchRequest(
        @Valid @NotNull RecommendMovieRequest movie,
        @Size(max = 200) String header,
        @Size(max = 10000) String description,
        Long linkedCourseId) {}
