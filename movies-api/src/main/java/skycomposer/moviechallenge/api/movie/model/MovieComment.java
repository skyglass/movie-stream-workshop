package skycomposer.moviechallenge.api.movie.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "movie_comments")
public class MovieComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_imdb_id", nullable = false)
    private Movie movie;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, length = 4000)
    private String text;

    @Column(nullable = false)
    private Instant timestamp;

    public MovieComment(String username, String text, Instant timestamp) {
        this.username = username;
        this.text = text;
        this.timestamp = timestamp;
    }
}
