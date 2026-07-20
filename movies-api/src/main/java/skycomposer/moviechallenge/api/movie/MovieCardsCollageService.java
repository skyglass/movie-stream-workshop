package skycomposer.moviechallenge.api.movie;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import skycomposer.moviechallenge.api.movie.model.Movie;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// Renders a poster-only grid ("Movie Cards Collage") for a caller-ordered set of movies, as a single downloadable
// PNG. Deliberately composed entirely server-side (java.awt/ImageIO, no browser <canvas>): poster images are
// hosted cross-origin (OMDb/Amazon media URLs with no permissive CORS headers), so a client-side canvas reading
// them would hit a "tainted canvas" SecurityError on toDataURL/toBlob. Compositing here sidesteps that entirely,
// and also means the browser never needs direct network access to the poster host.
@Service
public class MovieCardsCollageService {
    static final int MAX_MOVIES = 50;

    private static final int CELL_WIDTH = 220;
    private static final int CELL_HEIGHT = 330; // standard 2:3 poster aspect ratio
    private static final int GUTTER = 12;
    private static final Color BACKGROUND = new Color(15, 23, 42); // slate-900 -- the "delimiter" gutter color
    private static final Color BORDER = new Color(100, 116, 139); // slate-500 -- a thin frame around each poster
    // Poster URLs are only ever resolved from this app's own catalog (never taken from the request), but the
    // fetch itself still reaches out to a caller-influenced URL (whatever was stored when the movie was added)
    // -- restricting to known poster CDNs bounds that to a harmless image fetch instead of an open SSRF proxy.
    private static final Set<String> ALLOWED_HOST_SUFFIXES = Set.of(".media-amazon.com");
    private static final Set<String> ALLOWED_HOSTS = Set.of("img.omdbapi.com");
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(5);
    private static final long MAX_POSTER_BYTES = 5_000_000;

    private final MovieRepository movieRepository;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(FETCH_TIMEOUT).build();
    private final BufferedImage placeholder = loadPlaceholder();

    public MovieCardsCollageService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public byte[] generateCollage(List<String> imdbIds) {
        Map<String, Movie> byId = movieRepository.findAllById(imdbIds).stream()
                .collect(Collectors.toMap(Movie::getImdbId, movie -> movie));
        // Preserves the caller's own order (the same order the movies are shown on-screen) -- JpaRepository's
        // findAllById() makes no ordering guarantee, so re-derive it from the requested id list, dropping any
        // id that no longer resolves to a catalog movie.
        List<Movie> ordered = imdbIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        if (ordered.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "None of the requested movies were found");
        }

        int count = ordered.size();
        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / columns);

        List<CompletableFuture<BufferedImage>> posterFutures = ordered.stream()
                .map(movie -> fetchPosterAsync(movie.getPoster())).toList();
        CompletableFuture.allOf(posterFutures.toArray(CompletableFuture[]::new)).join();
        List<BufferedImage> posters = posterFutures.stream().map(CompletableFuture::join).toList();

        int width = columns * CELL_WIDTH + (columns + 1) * GUTTER;
        int height = rows * CELL_HEIGHT + (rows + 1) * GUTTER;
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, width, height);

        for (int i = 0; i < count; i++) {
            int col = i % columns;
            int row = i / columns;
            int x = GUTTER + col * (CELL_WIDTH + GUTTER);
            int y = GUTTER + row * (CELL_HEIGHT + GUTTER);
            drawCover(g, posters.get(i), x, y, CELL_WIDTH, CELL_HEIGHT);
            g.setColor(BORDER);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(x, y, CELL_WIDTH - 1, CELL_HEIGHT - 1);
        }
        g.dispose();
        return encodePng(canvas);
    }

    private CompletableFuture<BufferedImage> fetchPosterAsync(String posterUrl) {
        if (posterUrl == null || posterUrl.isBlank() || "N/A".equals(posterUrl) || !isAllowedHost(posterUrl)) {
            return CompletableFuture.completedFuture(placeholder);
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(posterUrl)).timeout(FETCH_TIMEOUT).GET().build();
        } catch (RuntimeException malformed) {
            return CompletableFuture.completedFuture(placeholder);
        }
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .orTimeout(FETCH_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                .thenApply(this::decodeIfValid)
                .exceptionally(error -> placeholder);
    }

    private BufferedImage decodeIfValid(HttpResponse<byte[]> response) {
        if (response.statusCode() != 200 || response.body().length > MAX_POSTER_BYTES) return placeholder;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            return image != null ? image : placeholder;
        } catch (IOException malformedImage) {
            return placeholder;
        }
    }

    private boolean isAllowedHost(String posterUrl) {
        try {
            URI uri = URI.create(posterUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (ALLOWED_HOSTS.contains(host)) return true;
            return ALLOWED_HOST_SUFFIXES.stream().anyMatch(host::endsWith);
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    // "cover" crop: scales the source image so it fills the target box completely (cropping the overflow via a
    // clip), matching CSS object-fit: cover -- keeps every cell a uniform size regardless of each poster's own
    // aspect ratio, with no letterboxing.
    private void drawCover(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
        double scale = Math.max((double) width / image.getWidth(), (double) height / image.getHeight());
        int scaledWidth = (int) Math.ceil(image.getWidth() * scale);
        int scaledHeight = (int) Math.ceil(image.getHeight() * scale);
        int offsetX = x - (scaledWidth - width) / 2;
        int offsetY = y - (scaledHeight - height) / 2;
        Shape oldClip = g.getClip();
        g.setClip(x, y, width, height);
        g.drawImage(image, offsetX, offsetY, scaledWidth, scaledHeight, null);
        g.setClip(oldClip);
    }

    private byte[] encodePng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode collage image", e);
        }
    }

    private static BufferedImage loadPlaceholder() {
        try (InputStream in = MovieCardsCollageService.class.getResourceAsStream("/images/movie-poster-placeholder.jpg")) {
            BufferedImage image = ImageIO.read(Objects.requireNonNull(in, "movie-poster-placeholder.jpg missing from classpath"));
            return Objects.requireNonNull(image, "movie-poster-placeholder.jpg could not be decoded");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load poster placeholder", e);
        }
    }
}
