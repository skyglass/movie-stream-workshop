package skycomposer.moviechallenge.api.movie.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class SharedFavoriteMoviesNotFoundException extends RuntimeException {

    public SharedFavoriteMoviesNotFoundException(String encodedUsername) {
        super("Shared favorite movies for '%s' not found".formatted(encodedUsername));
    }
}
