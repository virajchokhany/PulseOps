package io.pulseops.infrastructure.sourcecontrol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import io.pulseops.domain.evidence.CodeSnippet;

/**
 * Reads targeted snippets from the local checkout.
 *
 * <p>It reads the <em>working tree</em>, not Git object history. The MVP's deployment and pull
 * request metadata is seeded with synthetic commit SHAs that exist in no real history, so resolving
 * a blob at a SHA would fail for every record. Commit identity is therefore attached from the
 * deployment metadata rather than read from Git. Swapping in a real Git or GitHub client is an
 * implementation change behind {@link SourceControlProvider} and touches nothing else.
 *
 * <p>File access is constrained on purpose. Paths originate in the database, so they are treated as
 * untrusted input: every path is normalised and must resolve inside the configured root, and only
 * allow-listed extensions are readable. Without those checks a path such as
 * {@code ../../../../.ssh/id_rsa} would be read and handed to an external LLM.
 */
@Component
@EnableConfigurationProperties(SourceControlProperties.class)
public class LocalGitProvider implements SourceControlProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalGitProvider.class);

    private final SourceControlProperties properties;
    private final Path root;

    public LocalGitProvider(SourceControlProperties properties) {
        this.properties = properties;
        this.root = resolveRoot(properties.getRootPath());
    }

    /**
     * A relative source root resolves against whatever directory the process was launched from.
     * That silently produced an empty evidence package when the same jar was started from the
     * repository root rather than the module directory: no code snippets, no error, and an RCA that
     * cited no files. A relative root is therefore verified to land on a checkout and searched for
     * upward if it does not. An absolute root is an explicit choice and is always honoured.
     */
    private static Path resolveRoot(String configured) {
        Path candidate = Path.of(configured).toAbsolutePath().normalize();
        if (Path.of(configured).isAbsolute() || looksLikeRepository(candidate)) {
            log.info("Local source provider rooted at {}", candidate);
            return candidate;
        }

        for (Path dir = Path.of("").toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
            if (looksLikeRepository(dir)) {
                log.warn("Relative source root '{}' resolved to {}, which is not a PulseOps checkout; "
                        + "using {} instead. Set PULSEOPS_SOURCE_ROOT to an absolute path to silence this.",
                        configured, candidate, dir);
                return dir;
            }
        }

        log.warn("No PulseOps checkout found at {} or above {}. Investigations will run without code "
                + "evidence. Set PULSEOPS_SOURCE_ROOT to the repository root.",
                candidate, Path.of("").toAbsolutePath());
        return candidate;
    }

    private static boolean looksLikeRepository(Path dir) {
        return Files.isDirectory(dir.resolve("backend")) && Files.isRegularFile(dir.resolve("pom.xml"));
    }

    @Override
    public List<CodeSnippet> fetchSnippets(SnippetRequest request) {
        if (request.filePaths() == null || request.filePaths().isEmpty()) {
            return List.of();
        }

        List<CodeSnippet> snippets = new ArrayList<>();
        for (String filePath : request.filePaths()) {
            if (snippets.size() >= properties.getMaxFiles()) {
                log.debug("Reached maxFiles={} for service={}", properties.getMaxFiles(), request.service());
                break;
            }
            resolveReadable(filePath, request.sourcePath())
                    .flatMap(path -> extract(path, filePath, request))
                    .ifPresent(snippets::add);
        }
        return snippets;
    }

    /**
     * Resolves a repository-relative path to a real file inside the root, or empty when the path is
     * unsafe, unsupported, missing or too large.
     */
    private Optional<Path> resolveReadable(String filePath, String sourcePath) {
        if (filePath == null || filePath.isBlank()) {
            return Optional.empty();
        }
        if (!hasAllowedExtension(filePath)) {
            log.debug("Skipping {}: extension not allow-listed", filePath);
            return Optional.empty();
        }

        Optional<Path> direct = safeResolve(filePath);
        if (direct.isPresent()) {
            return direct;
        }
        // Catalog paths are sometimes relative to the service rather than the repository root.
        return sourcePath == null || sourcePath.isBlank()
                ? Optional.empty()
                : safeResolve(sourcePath + "/" + filePath);
    }

    private Optional<Path> safeResolve(String candidate) {
        try {
            Path resolved = root.resolve(candidate).normalize();

            // The containment check is what actually stops traversal; normalising alone does not.
            if (!resolved.startsWith(root)) {
                log.warn("Rejected path outside source root: {}", candidate);
                return Optional.empty();
            }
            if (!Files.isRegularFile(resolved)) {
                return Optional.empty();
            }
            if (Files.size(resolved) > properties.getMaxFileSizeBytes()) {
                log.debug("Skipping {}: larger than {} bytes", candidate, properties.getMaxFileSizeBytes());
                return Optional.empty();
            }
            return Optional.of(resolved);
        } catch (InvalidPathException | IOException e) {
            log.debug("Unreadable path {}: {}", candidate, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<CodeSnippet> extract(Path path, String declaredPath, SnippetRequest request) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", path, e.getMessage());
            return Optional.empty();
        }
        if (lines.isEmpty()) {
            return Optional.empty();
        }

        int anchor = findKeywordLine(lines, request.keywords());
        int start = anchor < 0 ? 0 : Math.max(0, anchor - properties.getContextLines());
        int end = Math.min(lines.size(), start + properties.getMaxLinesPerSnippet());

        StringBuilder content = new StringBuilder();
        for (int i = start; i < end; i++) {
            content.append(i + 1).append(": ").append(lines.get(i)).append('\n');
        }

        String reason = anchor >= 0
                ? "%s; matched keyword at line %d".formatted(request.reason(), anchor + 1)
                : request.reason();

        return Optional.of(new CodeSnippet(
                request.service(), request.repository(), declaredPath, request.commitSha(),
                start + 1, end, content.toString(), reason));
    }

    /** @return index of the first line mentioning any keyword, or -1 when none match. */
    private int findKeywordLine(List<String> lines, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank() && line.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean hasAllowedExtension(String filePath) {
        String lower = filePath.toLowerCase(Locale.ROOT);
        return properties.getAllowedExtensions().stream().anyMatch(lower::endsWith);
    }
}
