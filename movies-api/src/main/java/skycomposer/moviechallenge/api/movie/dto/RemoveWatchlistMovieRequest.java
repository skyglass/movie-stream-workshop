package skycomposer.moviechallenge.api.movie.dto;

import java.util.List;

// Null/empty categoryIds means "remove from everywhere in this watchlist" (its flat top level plus every private
// sub-category); a non-empty list scopes the removal to those private sub-categories' own transitive subtrees,
// matching MovieGuideService.removeMovie's scope semantics. Never touches the public category tree -- a movie
// that only appears via a subscribed category can't be removed this way, only by unsubscribing.
public record RemoveWatchlistMovieRequest(List<Long> categoryIds) {
}
