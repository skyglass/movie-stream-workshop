package skycomposer.moviechallenge.api.movie.dto;

import java.util.List;

// imdbId is always non-blank -- the client only ever sends rows it could parse an imdb_id out of. categoryPaths
// are dot-separated paths (e.g. "Genres.Drama"), resolved relative to the import's target category using the
// same path parser as the JSON-upload flow; empty/absent means "assign directly to the target". Also doubles as
// the "failed" element shape in ImportCsvMoviesResponse.
public record CsvMovieRef(String imdbId, List<String> categoryPaths) {
}
