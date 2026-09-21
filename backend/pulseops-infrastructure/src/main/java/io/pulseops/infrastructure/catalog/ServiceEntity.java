package io.pulseops.infrastructure.catalog;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "services")
public class ServiceEntity {

    @Id
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "owner", nullable = false, length = 150)
    private String owner;

    @Column(name = "repository", nullable = false, length = 255)
    private String repository;

    /** Path of this service inside the repository; drives selective source retrieval. */
    @Column(name = "source_path", length = 255)
    private String sourcePath;

    @Column(name = "environment", nullable = false, length = 50)
    private String environment;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ServiceEntity() {
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getOwner() {
        return owner;
    }

    public String getRepository() {
        return repository;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getVersion() {
        return version;
    }

    public String getTier() {
        return tier;
    }

    public String getDescription() {
        return description;
    }
}
