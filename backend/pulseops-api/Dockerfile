# Build context is the repository root: this module needs the parent pom and its sibling modules.
#   docker build -f backend/pulseops-api/Dockerfile -t pulseops-api .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Poms first: dependency resolution is cached unless a pom actually changes.
COPY pom.xml .
COPY backend/pulseops-domain/pom.xml backend/pulseops-domain/
COPY backend/pulseops-infrastructure/pom.xml backend/pulseops-infrastructure/
COPY backend/pulseops-api/pom.xml backend/pulseops-api/
COPY backend/pulseops-worker/pom.xml backend/pulseops-worker/
COPY backend/shopflow-common/pom.xml backend/shopflow-common/
COPY backend/shopflow-order-service/pom.xml backend/shopflow-order-service/
COPY backend/shopflow-payment-service/pom.xml backend/shopflow-payment-service/
RUN mvn -B -pl backend/pulseops-api -am dependency:go-offline

COPY backend backend
RUN mvn -B -pl backend/pulseops-api -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Never run as root: a container escape should not land on a privileged account.
RUN addgroup -S pulseops && adduser -S pulseops -G pulseops
COPY --from=build /build/backend/pulseops-api/target/pulseops-api-*.jar app.jar
USER pulseops

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
