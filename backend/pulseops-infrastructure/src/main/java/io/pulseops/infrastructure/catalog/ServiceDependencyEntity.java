package io.pulseops.infrastructure.catalog;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One directed edge of the dependency graph: {@code serviceName} calls {@code dependsOnName}. */
@Entity
@Table(name = "service_dependencies")
public class ServiceDependencyEntity {

    @EmbeddedId
    private Key key;

    @Column(name = "description", length = 255)
    private String description;

    protected ServiceDependencyEntity() {
    }

    public Key getKey() {
        return key;
    }

    public String getServiceName() {
        return key.getServiceName();
    }

    public String getDependsOnName() {
        return key.getDependsOnName();
    }

    public String getDescription() {
        return description;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "service_name", nullable = false, length = 100)
        private String serviceName;

        @Column(name = "depends_on_name", nullable = false, length = 100)
        private String dependsOnName;

        protected Key() {
        }

        public Key(String serviceName, String dependsOnName) {
            this.serviceName = serviceName;
            this.dependsOnName = dependsOnName;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getDependsOnName() {
            return dependsOnName;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key that)) {
                return false;
            }
            return Objects.equals(serviceName, that.serviceName)
                    && Objects.equals(dependsOnName, that.dependsOnName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceName, dependsOnName);
        }
    }
}
