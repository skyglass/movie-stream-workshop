package com.ivanfranchin.moviesapi.bdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanfranchin.moviesapi.movie.MovieRepository;
import com.ivanfranchin.moviesapi.bdd.movie.fixture.MovieCatalogFixture;
import com.ivanfranchin.moviesapi.bdd.user.fixture.UserAccessFixture;
import com.ivanfranchin.moviesapi.userextra.UserExtraRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration
public class CucumberFixtureConfiguration {

    @Bean
    MovieCatalogFixture movieCatalogFixture(MovieRepository movieRepository) {
        return new MovieCatalogFixture(movieRepository);
    }

    @Bean
    UserAccessFixture userAccessFixture(UserExtraRepository userExtraRepository, ObjectMapper objectMapper) {
        return new UserAccessFixture(userExtraRepository, objectMapper);
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
