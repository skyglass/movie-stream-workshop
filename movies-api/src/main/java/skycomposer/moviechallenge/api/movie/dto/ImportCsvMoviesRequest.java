package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ImportCsvMoviesRequest(
        @NotEmpty @Size(max = 2000) @Valid List<CsvMovieRef> movies,
        Long categoryId) {
}
