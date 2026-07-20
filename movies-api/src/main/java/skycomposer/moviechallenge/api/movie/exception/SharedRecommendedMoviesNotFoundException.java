package skycomposer.moviechallenge.api.movie.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class SharedRecommendedMoviesNotFoundException extends RuntimeException {

    public SharedRecommendedMoviesNotFoundException(String encodedUsername) {
        super("Shared recommended movies for '%s' not found".formatted(encodedUsername));
    }
}
