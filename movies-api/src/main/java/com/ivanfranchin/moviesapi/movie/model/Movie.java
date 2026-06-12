package com.ivanfranchin.moviesapi.movie.model;

import com.ivanfranchin.moviesapi.movie.dto.CreateMovieRequest;
import com.ivanfranchin.moviesapi.userextra.dto.UpdateMovieRequest;
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

    @Column(name = "release_year", nullable = false)
    private String year;

    @Column(length = 2048)
    private String poster;

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
        movie.setYear(createMovieRequest.year());
        movie.setPoster(createMovieRequest.poster());
        return movie;
    }

    public static void updateFrom(UpdateMovieRequest updateMovieRequest, Movie movie) {
        if (updateMovieRequest.title() != null) {
            movie.setTitle(updateMovieRequest.title());
        }
        if (updateMovieRequest.director() != null) {
            movie.setDirector(updateMovieRequest.director());
        }
        if (updateMovieRequest.year() != null) {
            movie.setYear(updateMovieRequest.year());
        }
        if (updateMovieRequest.poster() != null) {
            movie.setPoster(updateMovieRequest.poster());
        }
    }
}
