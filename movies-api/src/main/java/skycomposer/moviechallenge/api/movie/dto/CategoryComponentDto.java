package skycomposer.moviechallenge.api.movie.dto;

/**
 * A category used as one required term of a composition/subscription category. isPublic is always true on the
 * public side (there's only one kind of component there); on the private/watchlist side it distinguishes a
 * private (watchlist-owned) component from a reference to an existing public category.
 */
public record CategoryComponentDto(long id, String name, String icon, boolean isPublic) {
}
