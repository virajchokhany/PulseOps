package io.pulseops.infrastructure.pullrequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A file touched by a pull request. Drives which files are worth reading from source control. */
@Entity
@Table(name = "pull_request_files")
public class PullRequestFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "pull_request_id", nullable = false)
    private Long pullRequestId;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType;

    @Column(name = "additions", nullable = false)
    private int additions;

    @Column(name = "deletions", nullable = false)
    private int deletions;

    protected PullRequestFileEntity() {
    }

    public Long getId() {
        return id;
    }

    public Long getPullRequestId() {
        return pullRequestId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getChangeType() {
        return changeType;
    }

    public int getAdditions() {
        return additions;
    }

    public int getDeletions() {
        return deletions;
    }
}
