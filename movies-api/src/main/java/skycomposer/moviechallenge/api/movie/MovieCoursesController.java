package skycomposer.moviechallenge.api.movie;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import skycomposer.moviechallenge.api.movie.dto.AddCourseMovieRequest;
import skycomposer.moviechallenge.api.movie.dto.CreateMovieCourseRequest;
import skycomposer.moviechallenge.api.movie.dto.MovieCourseDto;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/movie-courses")
public class MovieCoursesController {
    private final MovieCourseRepository courses;

    @GetMapping public List<MovieCourseDto> list(@AuthenticationPrincipal Jwt jwt) { return courses.findAll(username(jwt)); }
    @GetMapping("/{id}") public MovieCourseDto get(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) { return courses.find(id, username(jwt)); }
    @GetMapping("/{id}/manage") public MovieCourseDto manage(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        courses.requireOwner(id, username(jwt)); return courses.find(id, username(jwt));
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MovieCourseDto create(@Valid @RequestBody CreateMovieCourseRequest r, @AuthenticationPrincipal Jwt jwt) { return courses.create(r, username(jwt)); }
    @PutMapping("/{id}") public MovieCourseDto update(@PathVariable long id, @Valid @RequestBody CreateMovieCourseRequest r, @AuthenticationPrincipal Jwt jwt) { return courses.update(id, r, username(jwt)); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) { courses.delete(id, username(jwt)); }
    @PostMapping("/{id}/applications") public MovieCourseDto apply(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) { return courses.apply(id, username(jwt)); }
    @PostMapping("/{id}/movies") public MovieCourseDto addMovie(@PathVariable long id, @Valid @RequestBody AddCourseMovieRequest r, @AuthenticationPrincipal Jwt jwt) { return courses.addMovie(id, r, username(jwt)); }
    @PutMapping("/{id}/movies/{movieId}") public MovieCourseDto updateMovie(@PathVariable long id, @PathVariable String movieId, @Valid @RequestBody AddCourseMovieRequest r, @AuthenticationPrincipal Jwt jwt) { return courses.updateMovie(id, movieId, r, username(jwt)); }
    @DeleteMapping("/{id}/movies/{movieId}") public MovieCourseDto removeMovie(@PathVariable long id, @PathVariable String movieId, @AuthenticationPrincipal Jwt jwt) { return courses.removeMovie(id, movieId, username(jwt)); }

    private String username(Jwt jwt) {
        for (String claim : List.of("preferred_username", "username")) {
            String value = jwt.getClaimAsString(claim); if (value != null && !value.isBlank()) return value;
        }
        return jwt.getSubject();
    }
}
