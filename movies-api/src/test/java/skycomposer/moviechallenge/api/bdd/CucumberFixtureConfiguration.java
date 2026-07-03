package skycomposer.moviechallenge.api.bdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import skycomposer.moviechallenge.api.movie.MovieChallengeRepository;
import skycomposer.moviechallenge.api.movie.MovieRepository;
import skycomposer.moviechallenge.api.movie.MovieRecommendationRepository;
import skycomposer.moviechallenge.api.bdd.movie.fixture.MovieCatalogFixture;
import skycomposer.moviechallenge.api.bdd.user.fixture.UserAccessFixture;
import skycomposer.moviechallenge.api.userextra.UserExtraRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration
public class CucumberFixtureConfiguration {

    @Bean
    MovieCatalogFixture movieCatalogFixture(MovieRepository movieRepository,
                                            MovieRecommendationRepository movieRecommendationRepository,
                                            MovieChallengeRepository movieChallengeRepository,
                                            JdbcTemplate jdbcTemplate) {
        return new MovieCatalogFixture(movieRepository, movieRecommendationRepository, movieChallengeRepository, jdbcTemplate);
    }

    @Bean
    UserAccessFixture userAccessFixture(UserExtraRepository userExtraRepository,
                                        ObjectMapper objectMapper,
                                        JdbcTemplate jdbcTemplate) {
        return new UserAccessFixture(userExtraRepository, objectMapper, jdbcTemplate);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException("Cucumber tests use MockMvc JWT request post-processors");
        };
    }
}
