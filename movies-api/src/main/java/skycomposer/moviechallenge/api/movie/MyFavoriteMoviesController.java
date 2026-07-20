package skycomposer.moviechallenge.api.movie;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import skycomposer.moviechallenge.api.movie.application.service.ShareMyFavoriteMoviesUseCase;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.exception.SharedFavoriteMoviesNotFoundException;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/my-favorite-movies")
public class MyFavoriteMoviesController {

    private static final String PUBLIC_PATH_PREFIX = "/api/my-favorite-movies/";

    private final ShareMyFavoriteMoviesUseCase shareMyFavoriteMovies;
    private final MoviePaging moviePaging;

    // Declared ahead of the "/**" catch-all below: Spring ranks a literal-segment pattern like this one as more
    // specific than "/**" regardless of declaration order, but keeping it first here documents that ordering
    // isn't accidental. {username} is a single path segment, already URL-decoded by Spring's own path-variable
    // handling -- unlike the raw-URI extraction the wildcard mapping below needs (a route already covering
    // "everything after the prefix" can't also carve out a trailing "/similar" from the same raw string).
    @GetMapping("/{username}/similar")
    public MoviePageDto getSharedSimilarToFavoriteMovies(@PathVariable String username,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer pageSize,
                                                          @RequestParam(required = false) String filter,
                                                          @RequestParam(required = false) String year,
                                                          @RequestParam(required = false) List<Long> selectedCategories) {
        return shareMyFavoriteMovies.viewSharedSimilarToFavoriteMovies(
                username, moviePaging.pageable(page, pageSize), filter, year, selectedCategories);
    }

    @GetMapping("/**")
    public MoviePageDto getSharedFavoriteMovies(HttpServletRequest request,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer pageSize,
                                                @RequestParam(required = false) String filter,
                                                @RequestParam(required = false) String year,
                                                @RequestParam(required = false) List<Long> selectedCategories) {
        return shareMyFavoriteMovies.viewSharedFavoriteMovies(
                encodedUsername(request),
                moviePaging.pageable(page, pageSize),
                filter,
                year,
                selectedCategories);
    }

    private String encodedUsername(HttpServletRequest request) {
        String prefix = request.getContextPath() + PUBLIC_PATH_PREFIX;
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith(prefix) || requestUri.length() == prefix.length()) {
            throw new SharedFavoriteMoviesNotFoundException("");
        }
        return requestUri.substring(prefix.length());
    }
}
