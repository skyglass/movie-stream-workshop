package skycomposer.moviechallenge.api.movie.dto;

import java.util.List;

public record ImportCsvMoviesResponse(List<CsvMovieRef> failedMovies) {
}
