package skycomposer.moviechallenge.api.movie.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @Column(nullable = false, updatable = false)
    private String username;

    @Column(name = "is_my_favorite_movies_public", nullable = false)
    private boolean myFavoriteMoviesPublic;

    @Column(name = "is_my_recommended_movies_public", nullable = false)
    private boolean myRecommendedMoviesPublic;

    public UserSettings(String username) {
        this.username = username;
        this.myFavoriteMoviesPublic = false;
        // Unlike favorites (private by default), a new user's Recommended Movies starts public -- "Make Your
        // Recommended Movies Private" is unchecked by default on the Profile page.
        this.myRecommendedMoviesPublic = true;
    }
}
