package io.pulseops.infrastructure.pullrequest;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pull_requests")
public class PullRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "number", nullable = false)
    private int number;

    @Column(name = "repository", nullable = false, length = 150)
    private String repository;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "author", length = 150)
    private String author;

    @Column(name = "commit_sha", nullable = false, length = 64)
    private String commitSha;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "url", length = 255)
    private String url;

    protected PullRequestEntity() {
    }

    public Long getId() {
        return id;
    }

    public int getNumber() {
        return number;
    }

    public String getRepository() {
        return repository;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public Instant getMergedAt() {
        return mergedAt;
    }

    public String getUrl() {
        return url;
    }
}
