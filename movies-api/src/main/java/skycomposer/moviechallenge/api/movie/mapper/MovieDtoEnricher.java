package skycomposer.moviechallenge.api.movie.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import skycomposer.moviechallenge.api.movie.MovieChallengeRepository;
import skycomposer.moviechallenge.api.movie.dto.MovieDto;
import skycomposer.moviechallenge.api.movie.dto.MovieRatingDto;
import skycomposer.moviechallenge.api.movie.model.Movie;

import java.util.List;
import java.util.Map;
import java.util.Set;

// Shared batch enrichment for every listing use case that needs to turn a page of Movie entities into MovieDtos
// carrying rank/rating -- factored out because the fetch-two-or-three-rating-maps-then-map-each-movie shape was
// otherwise duplicated verbatim across half a dozen use cases.
//
// subjectUsername is whatever rankPosition/rating should mean in the caller's context (the viewer on Home/
// Favorites/Watchlist, a Movie Personality's own synthetic ranking user on the Personality page, or the page
// owner on a public share page) -- pass null where that concept doesn't apply (e.g. category-similarity
// candidates, which are guaranteed unrated by construction). viewerUsername is the actual signed-in visitor;
// only ever different from subjectUsername on the Personality page and public share pages, where it drives
// viewerRankPosition/viewerRating so "Your Rank" can be shown correctly even though rankPosition/rating mean
// someone else's rank in that context.
@RequiredArgsConstructor
@Component
public class MovieDtoEnricher {

    private final MovieChallengeRepository movieChallengeRepository;
    private final MovieDtoMapper movieMapper;

    public List<MovieDto> toMovieDtos(List<Movie> movies, Set<String> recommendedMovieIds, Set<String> dislikedMovieIds,
                                       String subjectUsername, String viewerUsername) {
        List<String> imdbIds = movies.stream().map(Movie::getImdbId).toList();
        Map<String, MovieRatingDto> ratings = subjectUsername == null
                ? Map.of() : movieChallengeRepository.movieRatings(subjectUsername, imdbIds);
        Map<String, MovieRatingDto> usersPopularity = movieChallengeRepository.usersPopularityRatings(imdbIds);
        Map<String, MovieRatingDto> viewerRatings = (viewerUsername == null || viewerUsername.equals(subjectUsername))
                ? Map.of() : movieChallengeRepository.movieRatings(viewerUsername, imdbIds);

        return movies.stream()
                .map(movie -> movieMapper.toMovieDto(movie,
                        recommendedMovieIds.contains(movie.getImdbId()),
                        dislikedMovieIds.contains(movie.getImdbId()),
                        ratings.get(movie.getImdbId()),
                        usersPopularity.get(movie.getImdbId()),
                        viewerRatings.get(movie.getImdbId())))
                .toList();
    }
}
