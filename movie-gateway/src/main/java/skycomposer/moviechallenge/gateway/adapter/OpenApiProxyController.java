package skycomposer.moviechallenge.gateway.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
public class OpenApiProxyController {

    private static final String BACKEND_API_PREFIX = "/api";
    private static final String PUBLIC_MOVIES_API_PREFIX = "/api/movies";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String moviesApiDocsUrl;

    public OpenApiProxyController(
          WebClient.Builder webClientBuilder,
          ObjectMapper objectMapper,
          @Value("${MOVIES_API_URL:http://movies-api:8080}") String moviesApiUrl) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.moviesApiDocsUrl = moviesApiUrl + "/v3/api-docs";
    }

    @GetMapping("/movies")
    public Mono<ResponseEntity<String>> moviesDocs() {
        return webClient.get()
              .uri(moviesApiDocsUrl)
              .retrieve()
              .bodyToMono(String.class)
              .map(this::rewrite)
              .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private String rewrite(String body) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(body);
            var pathsNode = root.get("paths");
            if (pathsNode instanceof ObjectNode paths) {
                ObjectNode rewrittenPaths = objectMapper.createObjectNode();
                var fields = paths.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    rewrittenPaths.set(rewritePath(entry.getKey()), entry.getValue());
                }
                root.set("paths", rewrittenPaths);
            }

            ArrayNode servers = objectMapper.createArrayNode();
            servers.addObject().put("url", "");
            root.set("servers", servers);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rewrite OpenAPI document", e);
        }
    }

    private static String rewritePath(String path) {
        if (path.equals(BACKEND_API_PREFIX) || path.startsWith(BACKEND_API_PREFIX + "/")) {
            return PUBLIC_MOVIES_API_PREFIX + path.substring(BACKEND_API_PREFIX.length());
        }
        return path;
    }
}
