#!/bin/bash
# build-and-run.sh
# A script to build the project and restart Docker Compose

set -e  # Exit immediately if a command exits with a non-zero status

echo "=== Cleaning and packaging project with Maven ==="
./mvnw clean package -DskipTests -e

echo "=== Stopping running Docker containers ==="
docker-compose down

echo "=== Building Docker images ==="
docker-compose build

echo "=== Starting Docker containers ==="
docker-compose up