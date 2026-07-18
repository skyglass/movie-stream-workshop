package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CompleteCsvImportRequest(
        @NotEmpty @Size(max = 2000) @Valid List<CsvMovieImport> movies,
        Long categoryId) {
}
