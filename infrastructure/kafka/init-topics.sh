#!/bin/sh
# Creates every PulseOps topic so a fresh checkout only needs `docker compose up -d`.
# Runs as a one-shot Compose service; safe to re-run because of --if-not-exists.
set -eu

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
PARTITIONS="${TOPIC_PARTITIONS:-3}"
REPLICATION="${TOPIC_REPLICATION_FACTOR:-1}"

PRIMARY_TOPICS="telemetry alerts incidents"
DLQ_TOPICS="telemetry.dlq alerts.dlq incidents.dlq"

echo "[init-topics] waiting for Kafka at ${BOOTSTRAP} ..."
until /opt/kafka/bin/kafka-topics.sh --bootstrap-server "${BOOTSTRAP}" --list >/dev/null 2>&1; do
  sleep 2
done
echo "[init-topics] Kafka is reachable."

for topic in ${PRIMARY_TOPICS}; do
  echo "[init-topics] creating ${topic} (partitions=${PARTITIONS})"
  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "${topic}" \
    --partitions "${PARTITIONS}" \
    --replication-factor "${REPLICATION}"
done

# DLQs stay single-partition: ordering matters more than throughput when a
# human is replaying poison messages.
for topic in ${DLQ_TOPICS}; do
  echo "[init-topics] creating ${topic} (partitions=1)"
  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "${topic}" \
    --partitions 1 \
    --replication-factor "${REPLICATION}"
done

echo "[init-topics] topics now present:"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "${BOOTSTRAP}" --list
echo "[init-topics] done."
