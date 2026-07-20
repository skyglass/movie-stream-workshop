package skycomposer.moviechallenge.api.movie;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import skycomposer.moviechallenge.api.movie.dto.CollageRequest;

// Backs the "Download Poster Collage" action in the (rewritten) Share dialog on the Movie Guide and My Favorite
// Movies pages: renders a poster-only grid for a caller-ordered set of movies as a downloadable PNG. Public and
// unauthenticated, same as GET /api/movies -- everything it can render (posters, imdb ids) is already public
// catalog data.
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/movie-cards")
public class MovieCardsCollageController {
    private final MovieCardsCollageService collageService;

    @PostMapping(value = "/collage", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> collage(@Valid @RequestBody CollageRequest request) {
        byte[] png = collageService.generateCollage(request.imdbIds());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"movie-cards-collage.png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
}
