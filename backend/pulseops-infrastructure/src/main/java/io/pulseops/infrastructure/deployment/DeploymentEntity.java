package io.pulseops.infrastructure.deployment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "deployments")
public class DeploymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    /** Joins a deployment to the pull requests that shipped in it. */
    @Column(name = "commit_sha", nullable = false, length = 64)
    private String commitSha;

    @Column(name = "environment", nullable = false, length = 50)
    private String environment;

    @Column(name = "deployed_by", length = 150)
    private String deployedBy;

    @Column(name = "deployed_at", nullable = false)
    private Instant deployedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "notes")
    private String notes;

    protected DeploymentEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getVersion() {
        return version;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getDeployedBy() {
        return deployedBy;
    }

    public Instant getDeployedAt() {
        return deployedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}
