package skycomposer.moviechallenge.api.movie;

import skycomposer.moviechallenge.api.movie.model.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, String> {

    boolean existsByUsernameAndMyFavoriteMoviesPublicTrue(String username);

    boolean existsByUsernameAndMyRecommendedMoviesPublicTrue(String username);
}
