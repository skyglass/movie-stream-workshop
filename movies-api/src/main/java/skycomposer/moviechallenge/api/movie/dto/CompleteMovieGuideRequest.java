package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CompleteMovieGuideRequest(
        @NotEmpty @Size(max = 1000) @Valid List<GuideMovieDetails> movies) {}
