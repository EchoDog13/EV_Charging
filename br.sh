#!/bin/bash
# build-and-run.sh
# A script to build the project and restart Docker Compose

set -e  # Exit immediately if a command exits with a non-zero status

echo "=== Cleaning and packaging project with Maven ==="
./mvnw clean package -DskipTests -e
echo "=== Recreating Docker network ==="
docker network rm ev_network 2>/dev/null || true
docker network create ev_network
cd central

echo "=== Stopping running Docker containers ==="
docker-compose down

echo "=== Building Docker images ==="
docker-compose build

echo "=== Starting Docker containers ==="
docker-compose up