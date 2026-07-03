package skycomposer.moviechallenge.api.movie.model;

import skycomposer.moviechallenge.api.movie.dto.CreateMovieRequest;
import skycomposer.moviechallenge.api.userextra.dto.UpdateMovieRequest;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Entity
@Table(name = "movies")
public class Movie {

    @Id
    @Column(name = "imdb_id", nullable = false, updatable = false)
    private String imdbId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String director;

    @Column
    private String writer;

    @Column(name = "release_year", nullable = false)
    private String year;

    @Column(length = 2048)
    private String poster;

    @Column
    private String genre;

    @Column
    private String country;

    @Column(nullable = false)
    private MovieType type = MovieType.MOVIE;

    @OrderBy("timestamp DESC, id DESC")
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<MovieComment> comments = new ArrayList<>();

    public void addComment(MovieComment comment) {
        comment.setMovie(this);
        comments.addFirst(comment);
    }

    public static Movie from(CreateMovieRequest createMovieRequest) {
        Movie movie = new Movie();
        movie.setImdbId(createMovieRequest.imdbId());
        movie.setTitle(createMovieRequest.title());
        movie.setDirector(createMovieRequest.director());
        movie.setWriter(createMovieRequest.writer());
        movie.setYear(createMovieRequest.year());
        movie.setPoster(createMovieRequest.poster());
        movie.setGenre(createMovieRequest.genre());
        movie.setCountry(createMovieRequest.country());
        movie.setType(createMovieRequest.type());
        return movie;
    }

    public static void updateFrom(UpdateMovieRequest updateMovieRequest, Movie movie) {
        if (updateMovieRequest.title() != null) {
            movie.setTitle(updateMovieRequest.title());
        }
        if (updateMovieRequest.director() != null) {
            movie.setDirector(updateMovieRequest.director());
        }
        if (updateMovieRequest.writer() != null) {
            movie.setWriter(updateMovieRequest.writer());
        }
        if (updateMovieRequest.year() != null) {
            movie.setYear(updateMovieRequest.year());
        }
        if (updateMovieRequest.poster() != null) {
            movie.setPoster(updateMovieRequest.poster());
        }
        if (updateMovieRequest.genre() != null) {
            movie.setGenre(updateMovieRequest.genre());
        }
        if (updateMovieRequest.country() != null) {
            movie.setCountry(updateMovieRequest.country());
        }
        if (updateMovieRequest.type() != null) {
            movie.setType(updateMovieRequest.type());
        }
    }
}
