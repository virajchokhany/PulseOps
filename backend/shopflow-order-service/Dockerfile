# Build context is the repository root.
#   docker build -f backend/shopflow-order-service/Dockerfile -t shopflow-order .

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
RUN mvn -B -pl backend/shopflow-order-service -am dependency:go-offline

COPY backend backend
RUN mvn -B -pl backend/shopflow-order-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S shopflow && adduser -S shopflow -G shopflow
COPY --from=build /build/backend/shopflow-order-service/target/shopflow-order-service-*.jar app.jar
USER shopflow

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
