package skycomposer.moviechallenge.api.movie.dto;

public record UsersRecommendedMoviesShareDto(boolean myRecommendedMoviesPublic, String encodedUsername, String sharePath) {
}
