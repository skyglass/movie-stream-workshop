package com.ivanfranchin.capabilitiesui;

import com.ivanfranchin.capabilitiesui.service.CapabilitiesArchiveClient;
import com.ivanfranchin.capabilitiesui.service.SupportedRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
      "server.servlet.context-path=",
      "capabilities.cache-root=target/capabilities-ui-smoke",
      "GITHUB_REPO_1=https:/github.com/skyglass/use-case-bdd-workshop",
      "GITHUB_REPO_2=https:/github.com/skyglass/movie-stream-workshop",
      "GITHUB_REPO_3=https:/github.com/skyglass/missing-capabilities"
})
@AutoConfigureMockMvc
@Import(CapabilitiesUiSmokeTests.FakeArchiveConfiguration.class)
class CapabilitiesUiSmokeTests {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanCache() {
        deleteDirectory(Path.of("target/capabilities-ui-smoke"));
    }

    @Test
    void anyoneCanReadMergedCapabilityTree() throws Exception {
        mockMvc.perform(get("/api/capabilities/tree"))
              .andExpect(status().isOk())
              .andExpect(content().string(containsString("owner-care")))
              .andExpect(content().string(containsString("movie-catalog")))
              .andExpect(content().string(containsString("missing-capabilities")))
              .andExpect(content().string(containsString("empty: no docs/capabilities folder")));
    }

    private void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to clean capabilities smoke cache", exception);
        }
    }

    @TestConfiguration
    static class FakeArchiveConfiguration {

        private static final Map<String, String> CAPABILITIES_BY_REPOSITORY = Map.of(
              "use-case-bdd-workshop", "owner-care",
              "movie-stream-workshop", "movie-catalog");

        @Bean
        @Primary
        CapabilitiesArchiveClient fakeCapabilitiesArchiveClient() {
            return this::downloadCapabilities;
        }

        private void downloadCapabilities(SupportedRepository repository, Path checkout) throws IOException {
            if ("missing-capabilities".equals(repository.repository())) {
                return;
            }
            String capabilityId = CAPABILITIES_BY_REPOSITORY.getOrDefault(repository.repository(), "owner-care");
            Path capabilityRoot = checkout.resolve("docs").resolve("capabilities").resolve(capabilityId);
            writeText(capabilityRoot.resolve("entity_model.md"), "# " + capabilityId + " Entity Model\n");
            writeText(capabilityRoot.resolve("glossary.md"), "# " + capabilityId + " Glossary\n");

            Path useCaseRoot = capabilityRoot.resolve("activities").resolve("capability-discovery")
                  .resolve("use-cases").resolve("view-capability");
            writeText(useCaseRoot.resolve("uc.feature"), """
                  Feature: view-capability

                    Scenario: Capability is visible
                      Given capability "%s" exists
                      When the capability map is generated
                      Then capability "%s" is visible
                  """.formatted(capabilityId, capabilityId));
            writeText(useCaseRoot.resolve("uc.puml"), """
                  @startuml
                  title view-capability
                  actor "Admin" as admin
                  usecase "view-capability" as UC
                  admin --> UC
                  @enduml
                  """);
        }

        private void writeText(Path path, String text) throws IOException {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8);
        }
    }
}
