package io.pulseops.infrastructure.sourcecontrol;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulseops.source-control")
public class SourceControlProperties {

    /** Root of the local checkout that PulseOps is allowed to read. */
    private String rootPath = ".";

    /** Upper bound on files read per request; keeps the evidence package small. */
    private int maxFiles = 6;

    private int maxLinesPerSnippet = 60;

    /** Lines kept above a keyword hit so the snippet has usable context. */
    private int contextLines = 12;

    private long maxFileSizeBytes = 512 * 1024;

    /**
     * Only these extensions may be read. Prevents an unexpected path from pulling in binaries,
     * archives, or anything resembling a credential file.
     */
    private List<String> allowedExtensions =
            List.of(".java", ".yml", ".yaml", ".xml", ".properties", ".ts", ".html", ".sql");

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public int getMaxLinesPerSnippet() {
        return maxLinesPerSnippet;
    }

    public void setMaxLinesPerSnippet(int maxLinesPerSnippet) {
        this.maxLinesPerSnippet = maxLinesPerSnippet;
    }

    public int getContextLines() {
        return contextLines;
    }

    public void setContextLines(int contextLines) {
        this.contextLines = contextLines;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }
}
