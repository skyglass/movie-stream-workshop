package skycomposer.moviechallenge.api.userextra.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import skycomposer.moviechallenge.api.movie.model.MovieType;

public record UpdateMovieRequest(
        @Schema(example = "Resident Evil: Apocalypse") String title,
        @Schema(example = "Paul W.S. Anderson", description = "Set \"N/A\" if the director of the movie is unknown") String director,
        @Schema(example = "Paul W.S. Anderson", description = "Set \"N/A\" if the writer of the movie is unknown") String writer,
        @Schema(example = "2004", description = "Set \"N/A\" if the year of the movie is unknown") String year,
        @Schema(example = "https://m.media-amazon.com/images/M/MV5BMTc1NTUxMzk0Nl5BMl5BanBnXkFtZTcwNDQ1MDIzMw@@._V1_SX300.jpg") String poster,
        @Schema(example = "Action, Horror, Sci-Fi") String genre,
        @Schema(example = "Canada, France, Germany, United Kingdom, United States") String country,
        @Schema(example = "MOVIE") MovieType type) {
}
