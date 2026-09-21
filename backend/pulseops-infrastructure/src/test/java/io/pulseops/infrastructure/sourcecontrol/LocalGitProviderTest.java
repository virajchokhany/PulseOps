package io.pulseops.infrastructure.sourcecontrol;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.pulseops.domain.evidence.CodeSnippet;
import io.pulseops.infrastructure.sourcecontrol.SourceControlProvider.SnippetRequest;

class LocalGitProviderTest {

    @TempDir
    Path tempDir;

    private Path root;
    private SourceControlProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectory(tempDir.resolve("repo"));
        properties = new SourceControlProperties();
        properties.setRootPath(root.toString());
    }

    private LocalGitProvider provider() {
        return new LocalGitProvider(properties);
    }

    private SnippetRequest request(List<String> filePaths, List<String> keywords) {
        return new SnippetRequest("payment-service", "shopflow", null, "abc123",
                filePaths, keywords, "changed by PR #482");
    }

    @Test
    void centresTheSnippetOnTheKeyword() throws IOException {
        List<String> lines = IntStream.rangeClosed(1, 200)
                .mapToObj(i -> i == 150 ? "    private Duration readTimeout = ofSeconds(5);" : "// filler " + i)
                .toList();
        Files.write(root.resolve("Client.java"), lines);

        List<CodeSnippet> snippets = provider().fetchSnippets(request(List.of("Client.java"), List.of("readTimeout")));

        assertThat(snippets).hasSize(1);
        CodeSnippet snippet = snippets.getFirst();
        assertThat(snippet.content()).contains("readTimeout");
        assertThat(snippet.startLine()).isEqualTo(150 - properties.getContextLines());
        assertThat(snippet.reason()).contains("matched keyword at line 150");
        assertThat(snippet.commitSha()).isEqualTo("abc123");
    }

    @Test
    void neverReturnsMoreThanTheConfiguredSnippetLength() throws IOException {
        List<String> lines = IntStream.rangeClosed(1, 500).mapToObj(i -> "line " + i).toList();
        Files.write(root.resolve("Big.java"), lines);

        List<CodeSnippet> snippets = provider().fetchSnippets(request(List.of("Big.java"), List.of()));

        assertThat(snippets).hasSize(1);
        long returnedLines = snippets.getFirst().content().lines().count();
        assertThat(returnedLines).isEqualTo(properties.getMaxLinesPerSnippet());
    }

    @Test
    void refusesToEscapeTheSourceRoot() throws IOException {
        Files.writeString(tempDir.resolve("secret.java"), "private static final String KEY = \"leak-me\";");

        List<CodeSnippet> snippets = provider()
                .fetchSnippets(request(List.of("../secret.java"), List.of("KEY")));

        assertThat(snippets).isEmpty();
    }

    @Test
    void refusesExtensionsThatAreNotAllowListed() throws IOException {
        Files.writeString(root.resolve("id_rsa.pem"), "-----BEGIN PRIVATE KEY-----");

        assertThat(provider().fetchSnippets(request(List.of("id_rsa.pem"), List.of()))).isEmpty();
    }

    @Test
    void ignoresMissingFilesWithoutFailing() {
        assertThat(provider().fetchSnippets(request(List.of("NoSuchFile.java"), List.of()))).isEmpty();
    }

    @Test
    void capsTheNumberOfFilesRead() throws IOException {
        for (int i = 0; i < 20; i++) {
            Files.writeString(root.resolve("File" + i + ".java"), "class File" + i + " {}");
        }
        List<String> paths = IntStream.range(0, 20).mapToObj(i -> "File" + i + ".java").toList();

        assertThat(provider().fetchSnippets(request(paths, List.of())))
                .hasSize(properties.getMaxFiles());
    }

    @Test
    void fallsBackToTheServiceRelativePath() throws IOException {
        Path nested = Files.createDirectories(root.resolve("backend/payment/src"));
        Files.writeString(nested.resolve("Payment.java"), "class Payment {}");

        SnippetRequest request = new SnippetRequest("payment-service", "shopflow",
                "backend/payment/src", "abc123", List.of("Payment.java"), List.of(), "changed by PR");

        assertThat(provider().fetchSnippets(request)).hasSize(1);
    }

    /**
     * The seeded pull request points at a real file in this repository. If that file moves without
     * the seed being updated, the AI worker silently loses its code evidence, so pin it here.
     */
    @Test
    void resolvesTheFilePathSeededForPullRequest482() {
        String seededPath =
                "backend/shopflow-payment-service/src/main/java/io/pulseops/shopflow/payment/client/PaymentProviderClient.java";

        SourceControlProperties repoProperties = new SourceControlProperties();
        repoProperties.setRootPath("../..");
        Assumptions.assumeTrue(Files.exists(Path.of("../..", seededPath)),
                "Only runs from the module directory inside the repository");

        List<CodeSnippet> snippets = new LocalGitProvider(repoProperties).fetchSnippets(
                new SnippetRequest("payment-service", "shopflow", null,
                        "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678",
                        List.of(seededPath), List.of("readTimeout"), "changed by PR #482"));

        assertThat(snippets).hasSize(1);
        assertThat(snippets.getFirst().content()).contains("readTimeout");
    }

    /**
     * A relative root resolves against the launch directory. Starting the jar from the repository
     * root instead of the module directory sent it to the desktop, and the only symptom was an RCA
     * with no code citations.
     */
    @Test
    void recoversWhenARelativeRootMissesTheCheckout() {
        String seededPath =
                "backend/shopflow-payment-service/src/main/java/io/pulseops/shopflow/payment/client/PaymentProviderClient.java";
        Assumptions.assumeTrue(Files.exists(Path.of("../..", seededPath)),
                "Only runs from the module directory inside the repository");

        SourceControlProperties strayProperties = new SourceControlProperties();
        strayProperties.setRootPath("../../../..");

        List<CodeSnippet> snippets = new LocalGitProvider(strayProperties).fetchSnippets(
                new SnippetRequest("payment-service", "shopflow", null, "abc123",
                        List.of(seededPath), List.of("readTimeout"), "changed by PR #482"));

        assertThat(snippets).hasSize(1);
    }

    /** An absolute root is an explicit choice, so it must never be second-guessed. */
    @Test
    void honoursAnAbsoluteRootEvenWhenItIsNotACheckout() throws IOException {
        Files.writeString(root.resolve("Only.java"), "class Only {}");

        assertThat(provider().fetchSnippets(request(List.of("Only.java"), List.of()))).hasSize(1);
        assertThat(provider().fetchSnippets(request(List.of("pom.xml"), List.of()))).isEmpty();
    }
}
