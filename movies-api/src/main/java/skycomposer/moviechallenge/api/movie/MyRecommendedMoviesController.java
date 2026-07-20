package skycomposer.moviechallenge.api.movie;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import skycomposer.moviechallenge.api.movie.application.service.ShareUsersRecommendedMoviesUseCase;
import skycomposer.moviechallenge.api.movie.dto.MoviePageDto;
import skycomposer.moviechallenge.api.movie.exception.SharedRecommendedMoviesNotFoundException;
import java.util.List;

// Mirrors MyFavoriteMoviesController exactly: the public, unauthenticated view of a user's Recommended Movies
// page once they've opted in via "Share" (is_my_recommended_movies_public). Anonymous and read-only -- no
// like/dislike actions are exposed here, only the movie list itself.
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/my-recommended-movies")
public class MyRecommendedMoviesController {

    private static final String PUBLIC_PATH_PREFIX = "/api/my-recommended-movies/";

    private final ShareUsersRecommendedMoviesUseCase shareUsersRecommendedMovies;
    private final MoviePaging moviePaging;

    @GetMapping("/**")
    public MoviePageDto getSharedRecommendedMovies(HttpServletRequest request,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer pageSize,
                                                    @RequestParam(required = false) String filter,
                                                    @RequestParam(required = false) String year,
                                                    @RequestParam(required = false) List<Long> selectedCategories) {
        return shareUsersRecommendedMovies.viewSharedRecommendedMovies(
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
            throw new SharedRecommendedMoviesNotFoundException("");
        }
        return requestUri.substring(prefix.length());
    }
}
