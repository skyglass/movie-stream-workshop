package com.wordpress.kkaravitis.banking.gateway.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
public class OpenApiProxyController {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Map<String, ServiceDoc> services;

    public OpenApiProxyController(
          WebClient.Builder webClientBuilder,
          ObjectMapper objectMapper,
          @Value("${MOVIES_API_URL:http://movies-api:8080}") String moviesApiUrl) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.services = Map.of(
              "movies", new ServiceDoc(moviesApiUrl + "/v3/api-docs", Map.of())
        );
    }

    @GetMapping("/{serviceName}")
    public Mono<ResponseEntity<String>> serviceDocs(@PathVariable String serviceName) {
        ServiceDoc service = services.get(serviceName);
        if (service == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        return webClient.get()
              .uri(service.url())
              .retrieve()
              .bodyToMono(String.class)
              .map(body -> rewrite(body, service))
              .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private String rewrite(String body, ServiceDoc service) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(body);
            var pathsNode = root.get("paths");
            if (pathsNode instanceof ObjectNode paths) {
                ObjectNode rewrittenPaths = objectMapper.createObjectNode();
                var fields = paths.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    rewrittenPaths.set(rewritePath(entry.getKey(), service.rewrites()), entry.getValue());
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

    private static String rewritePath(String path, Map<String, String> rewrites) {
        for (Map.Entry<String, String> rewrite : rewrites.entrySet()) {
            if (path.equals(rewrite.getKey()) || path.startsWith(rewrite.getKey() + "/")) {
                return rewrite.getValue() + path.substring(rewrite.getKey().length());
            }
        }
        return path;
    }

    private record ServiceDoc(String url, Map<String, String> rewrites) {}
}
