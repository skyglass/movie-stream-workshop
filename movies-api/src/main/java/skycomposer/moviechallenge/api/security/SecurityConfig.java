package skycomposer.moviechallenge.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@RequiredArgsConstructor
@Configuration
public class SecurityConfig {

    private final JwtAuthConverter jwtAuthConverter;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuer;

    @Value("${security.expected-audience:movies-api}")
    private String expectedAudience;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/movies").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/categories/movies/*").hasAnyRole(MOVIES_ADMIN, MOVIES_USER)
                        .requestMatchers(HttpMethod.POST, "/api/categories/*/movies-from-search").hasAnyRole(MOVIES_ADMIN, MOVIES_USER)
                        .requestMatchers("/api/categories", "/api/categories/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/my-favorite-movies/**").permitAll()
                        // Public shared view of a user's Recommended Movies page, once they've opted in via
                        // Share -- read-only, gated server-side on is_my_recommended_movies_public (see
                        // ShareUsersRecommendedMoviesUseCase). The owner-only Share status/toggle endpoints stay
                        // under the existing authenticated "/api/users-recommended-movies/**" rule below.
                        .requestMatchers(HttpMethod.GET, "/api/my-recommended-movies/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/movies/*/similar-movies").permitAll()
                        // "Download Poster Collage": renders a poster collage from already-public catalog data
                        // (posters, imdb ids), same public-read posture as GET /api/movies.
                        .requestMatchers(HttpMethod.POST, "/api/movie-cards/collage").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/movies/*").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/movies/*").hasAnyRole(MOVIES_ADMIN, MOVIES_GUIDE)
                        .requestMatchers("/api/movies/*/comments").hasRole(MOVIES_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/movies/recommendation").hasAnyRole(MOVIES_ADMIN, MOVIES_USER)
                        .requestMatchers("/api/movies/*/recommendation").hasAnyRole(MOVIES_ADMIN, MOVIES_USER)
                        .requestMatchers("/api/movies/*/recommendation/replay").hasAnyRole(MOVIES_ADMIN, MOVIES_USER)
                        .requestMatchers("/api/movies/*/recommendation/dislike").hasAnyRole(MOVIES_ADMIN, MOVIES_USER)
                        .requestMatchers("/api/movie-challenges", "/api/movie-challenges/**").hasAnyRole(MOVIES_ADMIN, MOVIES_USER)
                        .requestMatchers("/api/favorite-movies", "/api/favorite-movies/**").hasAnyRole(MOVIES_ADMIN, MOVIES_USER)
                        .requestMatchers("/api/users-favorite-movies", "/api/users-favorite-movies/**").hasAnyRole(MOVIES_ADMIN, MOVIES_USER)
                        .requestMatchers("/api/users-recommended-movies", "/api/users-recommended-movies/**").hasAnyRole(MOVIES_ADMIN, MOVIES_USER)
                        .requestMatchers(HttpMethod.GET, "/api/movie-guides/by-category/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/movie-guides/*/movies").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/movie-guides/*/similar-movies").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/movie-guides/*/personality-movies").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/movie-guides/mine").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/movie-guides/wizard").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/movie-guides/*/subscribe").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/movie-guides/*/wizard-movies").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/movie-guides/*/import-csv").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/movie-guides/*/import-csv/complete").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/movie-guides/*/movies/*/remove").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/movie-guides/*/ranking").authenticated()
                        // "My Watchlists": private data, unlike the public /api/movie-guides and /api/categories
                        // above -- every method needs authentication, and WatchlistService/PrivateCategoryService
                        // additionally enforce an owner-or-MOVIES_ADMIN check on every single call.
                        .requestMatchers("/api/watchlists", "/api/watchlists/**").authenticated()
                        .requestMatchers("/api/private-categories", "/api/private-categories/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/movies").authenticated()
                        .requestMatchers("/api/movies", "/api/movies/**").hasRole(MOVIES_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/userextras/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/userextras/me").authenticated()
                        .requestMatchers("/api/userextras", "/api/userextras/**").hasRole(MOVIES_ADMIN)
                        .requestMatchers("/api/users", "/api/users/**").hasRole(MOVIES_ADMIN)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2ResourceServer -> oauth2ResourceServer.jwt(
                        jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthConverter)))
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    HttpFirewall allowUrlEncodedUsernameFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedPercent(true);
        return firewall;
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator()));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> audienceValidator() {
        return jwt -> {
            List<String> aud = jwt.getAudience();
            boolean ok = aud != null && aud.contains(expectedAudience);
            return ok
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "Missing required audience: " + expectedAudience + ", token aud: " + aud,
                    null
            ));
        };
    }

    public static final String MOVIES_ADMIN = "MOVIES_ADMIN";
    public static final String MOVIES_USER = "MOVIES_USER";
    public static final String USER = "USER";
    public static final String MOVIES_GUIDE = "MOVIES_GUIDE";
}
