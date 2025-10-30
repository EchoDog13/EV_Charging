#!/usr/bin/env bash
# Spawn multiple engine containers from the built ev_engine image with unique host port mappings
# Usage: ./run-multiple-engines.sh <count> <start_host_port>
# Example: ./run-multiple-engines.sh 3 5501  -> starts 3 engines mapping host ports 5501,5502,5503

set -euo pipefail

COUNT=${1:-2}
START_PORT=${2:-5501}
MONITOR_PORT=5050
IMAGE_NAME=ev_engine:latest

# Build the image using docker-compose (avoids duplication of Dockerfile context)
pushd cp >/dev/null
docker-compose -f docker-compose.yaml build --no-cache --parallel ev_cp_e || docker build -t $IMAGE_NAME ev_cp_e
popd >/dev/null

# Run containers
for ((i=0;i<COUNT;i++)); do
  HOST_PORT=$((START_PORT + i))
  HOST_MONITOR_PORT=$((5051 + i))
  CHARGER_NUM=$((101 + i))
  CHARGER_ID="CHG-${CHARGER_NUM}"
  NAME="ev_engine_${CHARGER_NUM}"

  docker run -d \
    --name "$NAME" \
    --network cp_alicante \ # assumes the 'alicante' external network exists and is called cp_alicante
    -e CHARGER_ID="$CHARGER_ID" \
    -e MONITOR_HOST=monitor \
    -e MONITOR_PORT=$MONITOR_PORT \
    -e SPRING_KAFKA_BOOTSTRAP_SERVERS=100.83.66.30:9092 \
    -p ${HOST_PORT}:5500 \
    -p ${HOST_MONITOR_PORT}:5050 \
    $IMAGE_NAME

  echo "Started $NAME (CHARGER_ID=$CHARGER_ID) -> host ports: ${HOST_PORT},${HOST_MONITOR_PORT}"

done

echo "Launched $COUNT engine(s)."
