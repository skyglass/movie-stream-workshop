package com.ivanfranchin.capabilitiesui.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CapabilityTreeService {

    private static final Logger log = LoggerFactory.getLogger(CapabilityTreeService.class);
    private static final Pattern SCENARIO_LINE = Pattern.compile("^\\s*Scenario(?: Outline)?\\s*:\\s*(.+?)\\s*$");
    private static final Pattern GITHUB_REPOSITORY =
          Pattern.compile("^https?://github\\.com/([^/]+)/([^/#?]+?)(?:\\.git)?/?$");

    private final ConfigurableEnvironment environment;
    private final CapabilitiesArchiveClient archiveClient;
    private final Path cacheRoot;
    private final Path mergedRoot;

    public CapabilityTreeService(ConfigurableEnvironment environment,
                                 CapabilitiesArchiveClient archiveClient,
                                 @Value("${capabilities.cache-root:}") String cacheRoot) {
        this.environment = environment;
        this.archiveClient = archiveClient;
        String root = cacheRoot == null || cacheRoot.isBlank()
              ? System.getProperty("java.io.tmpdir") + "/movie-capabilities-ui"
              : cacheRoot;
        this.cacheRoot = Path.of(root).toAbsolutePath().normalize();
        this.mergedRoot = this.cacheRoot.resolve("merged").resolve("docs").resolve("capabilities");
    }

    public CapabilityTreeResponse tree() {
        List<SupportedRepository> repositories = githubRepositoriesFromEnvironment();
        if (repositories.isEmpty()) {
            throw badRequest("No GITHUB_REPO_* environment variables are configured");
        }

        List<RepositorySummary> summaries = new ArrayList<>();
        Path mergedCapabilitiesRoot = refreshMergedCapabilities(repositories, summaries);
        DocNode capabilities = buildNode(mergedCapabilitiesRoot, mergedCapabilitiesRoot, "merged", "");
        List<DocNode> capabilityNodes = new ArrayList<>(capabilities.children());
        capabilityNodes.sort(Comparator.comparing(DocNode::name).thenComparing(DocNode::repositoryName));

        List<UseCaseDetails> useCases = new ArrayList<>();
        capabilityNodes.forEach(node -> collectUseCases(node, useCases));

        DocNode root = new DocNode("root:/", "Use Case Map", "root", "",
              "", "", capabilityNodes, null);
        return new CapabilityTreeResponse(root, useCases, summaries);
    }

    private List<SupportedRepository> githubRepositoriesFromEnvironment() {
        Set<String> names = new LinkedHashSet<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                for (String name : enumerablePropertySource.getPropertyNames()) {
                    if (name.startsWith("GITHUB_REPO_")) {
                        names.add(name);
                    }
                }
            }
        }
        return names.stream()
              .sorted()
              .map(environment::getProperty)
              .filter(value -> value != null && !value.isBlank())
              .map(String::trim)
              .map(this::supportedRepository)
              .toList();
    }

    private SupportedRepository supportedRepository(String url) {
        Matcher matcher = GITHUB_REPOSITORY.matcher(normalizeGithubUrl(url));
        if (!matcher.matches()) {
            throw badRequest("Unsupported GitHub repository URL in GITHUB_REPO_*: " + url);
        }
        String owner = matcher.group(1);
        String repository = matcher.group(2);
        return new SupportedRepository(owner + "/" + repository, "https://github.com/" + owner + "/" + repository,
              owner, repository);
    }

    private String normalizeGithubUrl(String url) {
        return url.replace("https:/github.com/", "https://github.com/")
              .replace("http:/github.com/", "http://github.com/");
    }

    private Path refreshMergedCapabilities(List<SupportedRepository> repositories, List<RepositorySummary> summaries) {
        try {
            recreateDirectory(cacheRoot.resolve("merged"));
            Files.createDirectories(mergedRoot);
            for (SupportedRepository repository : repositories) {
                try {
                    Optional<Path> capabilitiesRoot = repositoryCapabilities(repository);
                    if (capabilitiesRoot.isPresent()) {
                        mergeCapabilities(repository, capabilitiesRoot.get());
                        summaries.add(repositorySummary(repository, "github archive"));
                    } else {
                        log.info("Repository {} has no docs/capabilities folder; treating it as empty", repository.url());
                        summaries.add(repositorySummary(repository, "empty: no docs/capabilities folder"));
                    }
                } catch (ResponseStatusException exception) {
                    log.warn("Skipping capabilities repository {}: {}", repository.url(), exception.getReason());
                    summaries.add(repositorySummary(repository, "unavailable: " + exception.getReason()));
                } catch (IOException exception) {
                    log.warn("Skipping capabilities repository {}: {}", repository.url(), exception.getMessage());
                    summaries.add(repositorySummary(repository, "unavailable: " + exception.getMessage()));
                }
            }
            return mergedRoot;
        } catch (IOException exception) {
            throw internalError("Unable to merge repository capabilities", exception);
        }
    }

    private RepositorySummary repositorySummary(SupportedRepository repository, String source) {
        return new RepositorySummary(repository.name(), repository.url(), checkoutRoot(repository).toString(), source);
    }

    private Optional<Path> repositoryCapabilities(SupportedRepository repository) {
        Path checkout = checkoutRoot(repository);
        Path capabilities = checkout.resolve("docs").resolve("capabilities");
        try {
            recreateDirectory(checkout);
            archiveClient.downloadCapabilities(repository, checkout);
            if (!Files.isDirectory(capabilities)) {
                return Optional.empty();
            }
            return Optional.of(capabilities);
        } catch (IOException exception) {
            throw internalError("Unable to download " + repository.url(), exception);
        }
    }

    private Path checkoutRoot(SupportedRepository repository) {
        return cacheRoot.resolve("repositories")
              .resolve(repository.owner())
              .resolve(repository.repository())
              .toAbsolutePath()
              .normalize();
    }

    private void mergeCapabilities(SupportedRepository repository, Path capabilitiesRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(capabilitiesRoot)) {
            for (Path source : stream.filter(Files::isRegularFile).toList()) {
                Path relative = capabilitiesRoot.relativize(source);
                Path target = mergedRoot.resolve(relative).normalize();
                if (!target.startsWith(mergedRoot)) {
                    throw new IOException("Merged target escapes root: " + target);
                }
                if (Files.exists(target)) {
                    Path parent = relative.getParent();
                    String fileName = source.getFileName().toString();
                    String scopedName = repository.repository() + "__" + fileName;
                    target = parent == null ? mergedRoot.resolve(scopedName) : mergedRoot.resolve(parent).resolve(scopedName);
                }
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
        }
    }

    private DocNode buildNode(Path directory, Path capabilitiesRoot, String repositoryName, String repositoryUrl) {
        String relativePath = relativePath(directory, capabilitiesRoot);
        String name = directory.equals(capabilitiesRoot) ? "docs/capabilities" : directory.getFileName().toString();
        String type = nodeType(directory, capabilitiesRoot);
        UseCaseDetails useCase = hasUseCaseFeature(directory)
              ? readUseCase(directory, capabilitiesRoot, repositoryName, repositoryUrl)
              : null;
        List<DocNode> children = childDirectories(directory).stream()
              .map(child -> buildNode(child, capabilitiesRoot, repositoryName, repositoryUrl))
              .toList();
        return new DocNode(nodeId(repositoryName, type, relativePath), name, type, relativePath,
              repositoryName, repositoryUrl, children, useCase);
    }

    private List<Path> childDirectories(Path directory) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(Files::isDirectory)
                  .filter(path -> !path.getFileName().toString().startsWith("."))
                  .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                  .toList();
        } catch (IOException exception) {
            throw internalError("Unable to list " + directory, exception);
        }
    }

    private String nodeType(Path directory, Path capabilitiesRoot) {
        if (directory.equals(capabilitiesRoot)) {
            return "root";
        }
        if (hasUseCaseFeature(directory)) {
            return "use-case";
        }
        Path relative = capabilitiesRoot.relativize(directory);
        if (relative.getNameCount() == 1) {
            return "capability";
        }
        if (isActivityDirectory(relative)) {
            return "activity";
        }
        return "folder";
    }

    private boolean isActivityDirectory(Path relative) {
        if (relative.getNameCount() < 2) {
            return false;
        }
        String lastSegment = relative.getFileName().toString();
        if ("activities".equals(lastSegment) || "use-cases".equals(lastSegment)) {
            return false;
        }
        boolean insideActivities = false;
        for (int index = 0; index < relative.getNameCount(); index++) {
            String segment = relative.getName(index).toString();
            if ("use-cases".equals(segment)) {
                return false;
            }
            if ("activities".equals(segment)) {
                insideActivities = true;
            }
        }
        return insideActivities;
    }

    private boolean hasUseCaseFeature(Path directory) {
        return Files.isRegularFile(directory.resolve("uc.feature"));
    }

    private UseCaseDetails readUseCase(Path useCasePath, Path capabilitiesRoot, String repositoryName, String repositoryUrl) {
        String featureText = readString(useCasePath.resolve("uc.feature"));
        ParsedFeature parsedFeature = parseFeature(featureText);
        String capabilityId = capabilityId(useCasePath, capabilitiesRoot);
        List<String> activityIds = activityIds(useCasePath, capabilitiesRoot);
        String useCaseId = useCasePath.getFileName().toString();
        String useCasePathText = useCasePathText(capabilityId, activityIds, useCaseId);
        return new UseCaseDetails(
              repositoryName,
              repositoryUrl,
              capabilityId,
              String.join("/", activityIds),
              activityIds,
              useCaseId,
              useCasePathText,
              relativePath(useCasePath, capabilitiesRoot),
              optionalReadString(useCasePath.resolve("uc.md")).orElse(""),
              featureText,
              optionalReadString(useCasePath.resolve("uc.puml")).orElse(""),
              parsedFeature.scenarios());
    }

    private void collectUseCases(DocNode node, List<UseCaseDetails> useCases) {
        if (node.useCase() != null) {
            useCases.add(node.useCase());
        }
        node.children().forEach(child -> collectUseCases(child, useCases));
    }

    private ParsedFeature parseFeature(String featureText) {
        String normalized = normalizeLineEndings(featureText);
        List<String> lines = normalized.isEmpty()
              ? List.of()
              : new ArrayList<>(List.of(normalized.split("\n", -1)));
        List<Integer> scenarioLines = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            if (SCENARIO_LINE.matcher(lines.get(index)).matches()) {
                scenarioLines.add(index);
            }
        }

        int firstBlockStart = scenarioLines.isEmpty()
              ? lines.size()
              : scenarioBlockStart(lines, scenarioLines.getFirst(), 0);
        String prefix = joinLines(lines.subList(0, firstBlockStart));
        List<ScenarioBlock> scenarios = new ArrayList<>();
        List<String> seenIds = new ArrayList<>();
        for (int scenarioIndex = 0; scenarioIndex < scenarioLines.size(); scenarioIndex++) {
            int scenarioLine = scenarioLines.get(scenarioIndex);
            int lowerBound = scenarioIndex == 0 ? 0 : scenarioLines.get(scenarioIndex - 1) + 1;
            int blockStart = scenarioBlockStart(lines, scenarioLine, lowerBound);
            int blockEnd = scenarioIndex + 1 >= scenarioLines.size()
                  ? lines.size()
                  : scenarioBlockStart(lines, scenarioLines.get(scenarioIndex + 1), scenarioLine + 1);
            String text = joinLines(lines.subList(blockStart, blockEnd)).stripTrailing();
            String name = scenarioName(lines.get(scenarioLine));
            String baseId = slugify(name);
            int count = 1;
            String id = baseId;
            while (seenIds.contains(id)) {
                count++;
                id = baseId + "-" + count;
            }
            seenIds.add(id);
            scenarios.add(new ScenarioBlock(id, name, text));
        }

        return new ParsedFeature(featureName(prefix), prefix, scenarios);
    }

    private int scenarioBlockStart(List<String> lines, int scenarioLine, int lowerBound) {
        int start = scenarioLine;
        while (start > lowerBound) {
            String previous = lines.get(start - 1).strip();
            if (previous.isBlank() || previous.startsWith("@") || previous.startsWith("#")) {
                start--;
            } else {
                break;
            }
        }
        return start;
    }

    private String capabilityId(Path useCasePath, Path capabilitiesRoot) {
        Path relative = capabilitiesRoot.relativize(useCasePath);
        return relative.getNameCount() > 0 ? relative.getName(0).toString() : "";
    }

    private List<String> activityIds(Path useCasePath, Path capabilitiesRoot) {
        Path relative = capabilitiesRoot.relativize(useCasePath);
        List<String> activities = new ArrayList<>();
        for (int index = 1; index < relative.getNameCount(); index++) {
            String segment = relative.getName(index).toString();
            if ("use-cases".equals(segment)) {
                break;
            }
            if (!"activities".equals(segment)) {
                activities.add(segment);
            }
        }
        return activities;
    }

    private String useCasePathText(String capabilityId, List<String> activityIds, String useCaseId) {
        List<String> path = new ArrayList<>();
        path.add(capabilityId);
        path.addAll(activityIds);
        path.add(useCaseId);
        return String.join(" -> ", path);
    }

    private String nodeId(String repositoryName, String type, String relativePath) {
        return repositoryName + ":" + type + ":" + (relativePath.isBlank() ? "/" : relativePath);
    }

    private String relativePath(Path path, Path capabilitiesRoot) {
        if (path.equals(capabilitiesRoot)) {
            return "";
        }
        return capabilitiesRoot.relativize(path).toString().replace('\\', '/');
    }

    private String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw internalError("Unable to read " + path, exception);
        }
    }

    private Optional<String> optionalReadString(Path path) {
        return Files.isRegularFile(path) ? Optional.of(readString(path)) : Optional.empty();
    }

    private void recreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> stream = Files.walk(directory)) {
                for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(directory);
    }

    private String normalizeLineEndings(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String joinLines(List<String> lines) {
        return String.join("\n", lines);
    }

    private String scenarioName(String scenarioLine) {
        Matcher matcher = SCENARIO_LINE.matcher(scenarioLine);
        return matcher.matches() ? matcher.group(1) : scenarioLine.strip();
    }

    private String featureName(String prefix) {
        Matcher matcher = Pattern.compile("(?m)^\\s*Feature\\s*:\\s*(.+?)\\s*$").matcher(prefix);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String slugify(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
              .replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "scenario" : slug;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException internalError(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }

    public record CapabilityTreeResponse(
          DocNode root,
          List<UseCaseDetails> useCases,
          List<RepositorySummary> repositories) {
    }

    public record RepositorySummary(
          String name,
          String url,
          String checkoutPath,
          String source) {
    }

    public record DocNode(
          String id,
          String name,
          String type,
          String relativePath,
          String repositoryName,
          String repositoryUrl,
          List<DocNode> children,
          UseCaseDetails useCase) {
    }

    public record UseCaseDetails(
          String repositoryName,
          String repositoryUrl,
          String capabilityId,
          String activityPath,
          List<String> activityIds,
          String useCaseId,
          String useCasePath,
          String relativePath,
          String ucMarkdown,
          String featureText,
          String plantUmlText,
          List<ScenarioBlock> scenarios) {
    }

    private record ParsedFeature(String featureName, String prefix, List<ScenarioBlock> scenarios) {
    }

    public record ScenarioBlock(String id, String name, String text) {
    }
}
