package skycomposer.moviechallenge.api.userextra;

import skycomposer.moviechallenge.api.userextra.model.UserExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserExtraRepository extends JpaRepository<UserExtra, String> {
}
