package com.wordpress.kkaravitis.banking.gateway.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
public class AuthTokenController {

    private final WebClient webClient;
    private final String tokenUri;

    public AuthTokenController(WebClient.Builder webClientBuilder,
          @Value("${keycloak.token-uri}") String tokenUri) {
        this.webClient = webClientBuilder.build();
        this.tokenUri = tokenUri;
    }

    @PostMapping(
          path = "/auth/token",
          produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<String>> token(ServerWebExchange exchange) {
        return exchange.getFormData()
              .flatMap(form -> {
                  String clientId = first(form, "client_id", "movies-ui");
                  String username = first(form, "username", "");
                  String password = first(form, "password", "");
                  String scope = form.getFirst("scope");
                  String clientSecret = first(form, "client_secret", null);

                  MultiValueMap<String, String> keycloakForm = new LinkedMultiValueMap<>();
                  keycloakForm.add("grant_type", "password");
                  keycloakForm.add("client_id", clientId);
                  keycloakForm.add("username", username);
                  keycloakForm.add("password", password);
                  if (scope != null && !scope.isBlank()) {
                      keycloakForm.add("scope", scope);
                  }
                  if (clientSecret != null && !clientSecret.isBlank()) {
                      keycloakForm.add("client_secret", clientSecret);
                  }

                  return webClient.post()
                        .uri(tokenUri)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData(keycloakForm))
                        .exchangeToMono(response -> response.bodyToMono(String.class)
                              .defaultIfEmpty("")
                              .map(body -> ResponseEntity
                                    .status(response.statusCode())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(body)));
              });
    }

    private static String first(MultiValueMap<String, String> form, String key, String fallback) {
        String value = form.getFirst(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
