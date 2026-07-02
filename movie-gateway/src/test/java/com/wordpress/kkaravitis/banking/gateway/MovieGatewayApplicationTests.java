package com.wordpress.kkaravitis.banking.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MovieGatewayApplicationTests {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void contextLoads() {
        // smoke test
    }

    @Test
    void publicMovieCatalogRouteDoesNotRequireAuthentication() {
        webTestClient.get()
                .uri("/api/movies/movies?page=1&pageSize=5")
                .exchange()
                .expectStatus().value(status -> assertNotEquals(401, status));
    }

    @Test
    void movieDetailsRouteStillRequiresAuthentication() {
        webTestClient.get()
                .uri("/api/movies/movies/tt0083658")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
