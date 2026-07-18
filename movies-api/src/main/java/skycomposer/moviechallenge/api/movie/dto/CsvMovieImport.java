package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CsvMovieImport(@Valid @NotNull RecommendMovieRequest movie, List<String> categoryPaths) {
}
