# Bakes the topic bootstrap script into the Kafka image so it can run as a sidecar
# in Azure Container Apps, where there is no bind mount.
#   docker build -f infrastructure/kafka/Dockerfile -t pulseops-kafka-init infrastructure/kafka

FROM apache/kafka:3.8.0

COPY init-topics.sh /scripts/init-topics.sh
