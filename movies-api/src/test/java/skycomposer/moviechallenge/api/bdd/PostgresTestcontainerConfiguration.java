package skycomposer.moviechallenge.api.bdd;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Same major version as production (see docker-compose.yml) -- real PostgreSQL runs the genuine PL/pgSQL
// migrations (e.g. get_or_create_category), which H2's PostgreSQL compatibility mode cannot execute.
@Configuration(proxyBeanMethods = false)
public class PostgresTestcontainerConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"));
    }
}
