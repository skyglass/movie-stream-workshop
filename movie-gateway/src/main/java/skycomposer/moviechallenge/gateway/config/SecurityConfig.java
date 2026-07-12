package skycomposer.moviechallenge.gateway.config;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.firewall.ServerWebExchangeFirewall;
import org.springframework.security.web.server.firewall.StrictServerWebExchangeFirewall;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${security.expected-audience:movies-ui}")
    private String expectedAudience;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuer;

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
              .csrf(ServerHttpSecurity.CsrfSpec::disable)
              .authorizeExchange(exchanges -> exchanges
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/favicon.ico").permitAll()
                    .pathMatchers("/actuator/**").permitAll()
                    .pathMatchers("/auth/token").permitAll()
                    .pathMatchers("/api/movies/ws", "/api/movies/ws/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/movies/movies").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/movies/my-favorite-movies/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/movies/movie-journeys/*", "/api/movies/movie-courses/*").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/movies/movie-journeys", "/api/movies/movie-courses").permitAll()
                    .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/webjars/**").permitAll()
                    .pathMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
                    .pathMatchers("/api/movies", "/api/movies/**").authenticated()
                    .anyExchange().denyAll()
              )
              .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
              .build();
    }

    @Bean
    ServerWebExchangeFirewall allowUrlEncodedUsernameFirewall() {
        StrictServerWebExchangeFirewall firewall = new StrictServerWebExchangeFirewall();
        firewall.setAllowUrlEncodedPercent(true);
        return firewall;
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withIssuerLocation(issuer).build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAzp = jwt -> {
            String azp = jwt.getClaimAsString("azp");
            return expectedAudience.equals(azp)
                  ? OAuth2TokenValidatorResult.success()
                  : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_TOKEN,
                        "Missing/invalid azp. Expected: " + expectedAudience + ", azp: " + azp,
                        null
                  ));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAzp));
        return decoder;
    }

    @Bean
    CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    @Bean
    WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
