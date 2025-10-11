#!/bin/bash
set -e

echo "Waiting for Kafka to be ready..."
cub kafka-ready -b localhost:9092 1 20

echo "Creating topics..."
kafka-topics --bootstrap-server localhost:9094 --create --topic cp.command --partitions 1 --replication-factor 1
kafka-topics --bootstrap-server localhost:9094 --create --topic charge.request --partitions 1 --replication-factor 1
kafka-topics --bootstrap-server localhost:9094 --create --topic charge.auth --partitions 1 --replication-factor 1
kafka-topics --bootstrap-server localhost:9094 --create --topic charge.session --partitions 1 --replication-factor 1
kafka-topics --bootstrap-server localhost:9094 --create --topic billing.ticket --partitions 1 --replication-factor 1

echo "Topics created successfully."
exec "$@"