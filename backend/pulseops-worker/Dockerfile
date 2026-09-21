# Build context is the repository root.
#   docker build -f backend/pulseops-worker/Dockerfile -t pulseops-worker .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY backend/pulseops-domain/pom.xml backend/pulseops-domain/
COPY backend/pulseops-infrastructure/pom.xml backend/pulseops-infrastructure/
COPY backend/pulseops-api/pom.xml backend/pulseops-api/
COPY backend/pulseops-worker/pom.xml backend/pulseops-worker/
COPY backend/shopflow-common/pom.xml backend/shopflow-common/
COPY backend/shopflow-order-service/pom.xml backend/shopflow-order-service/
COPY backend/shopflow-payment-service/pom.xml backend/shopflow-payment-service/
RUN mvn -B -pl backend/pulseops-worker -am dependency:go-offline

COPY backend backend
RUN mvn -B -pl backend/pulseops-worker -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S pulseops && adduser -S pulseops -G pulseops
COPY --from=build /build/backend/pulseops-worker/target/pulseops-worker-*.jar app.jar

# Code-snippet evidence reads real files, so the worker image carries a read-only copy of the
# sources it may cite. Without this the RCA still runs but cites no code.
COPY pom.xml /app/source/pom.xml
COPY backend /app/source/backend
RUN chown -R pulseops:pulseops /app/source

USER pulseops

# Absolute, because the relative default only resolves correctly outside a container.
ENV PULSEOPS_SOURCE_ROOT=/app/source

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
