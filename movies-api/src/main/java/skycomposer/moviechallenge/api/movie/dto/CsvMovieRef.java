package skycomposer.moviechallenge.api.movie.dto;

// Either imdbId, or title+year, is expected to be non-blank -- the client only ever sends rows it could parse at
// least one identity out of. Also doubles as the "failed" element shape in ImportCsvMoviesResponse.
public record CsvMovieRef(String imdbId, String title, String year) {
}
