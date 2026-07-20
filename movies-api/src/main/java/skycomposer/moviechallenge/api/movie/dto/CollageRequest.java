package skycomposer.moviechallenge.api.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

// Ordered list of imdb ids to render into a "Movie Cards Collage" (see MovieCardsCollageService) -- the client
// resolves this order itself (same filter/sort as whatever page it's downloading from), the server only ever
// resolves poster URLs from its own catalog, never trusting a client-supplied URL directly.
public record CollageRequest(@NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 32) String> imdbIds) {
}
