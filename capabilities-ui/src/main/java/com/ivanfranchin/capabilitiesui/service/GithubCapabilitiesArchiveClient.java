package com.ivanfranchin.capabilitiesui.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

@Component
public class GithubCapabilitiesArchiveClient implements CapabilitiesArchiveClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(20))
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

    @Override
    public void downloadCapabilities(SupportedRepository repository, Path checkout) throws IOException {
        IOException lastException = null;
        for (String branch : List.of("main", "master")) {
            URI archiveUri = URI.create(repository.url() + "/archive/refs/heads/" + branch + ".zip");
            HttpRequest request = HttpRequest.newBuilder(archiveUri)
                  .timeout(Duration.ofSeconds(90))
                  .GET()
                  .build();
            try {
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    extractCapabilitiesOnly(response.body(), checkout);
                    return;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while downloading " + repository.url(), exception);
            } catch (IOException exception) {
                lastException = exception;
            }
        }
        throw new IOException("Unable to download main or master archive for " + repository.url(), lastException);
    }

    private void extractCapabilitiesOnly(InputStream archiveStream, Path checkout) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(archiveStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                int docsIndex = name.indexOf("/docs/capabilities/");
                if (docsIndex < 0) {
                    continue;
                }
                String relative = name.substring(docsIndex + 1);
                Path target = checkout.resolve(relative).normalize();
                if (!target.startsWith(checkout)) {
                    throw new IOException("Archive entry escapes checkout: " + name);
                }
                Files.createDirectories(target.getParent());
                Files.copy(zip, target);
            }
        }
    }
}
