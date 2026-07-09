package skycomposer.moviechallenge.api.movie.dto;

public record FavoriteMoviesShareDto(boolean myFavoriteMoviesPublic, String encodedUsername, String sharePath) {
}
