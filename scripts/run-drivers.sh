#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<EOF
Usage: $0 <count> [base-port] [image-name] [network]

Starts <count> driver containers from the driver/EV_Driver Dockerfile (builds image if needed).
Each instance will map host <base-port> .. <base-port>+count-1 -> container 8080.

Arguments:
  count       Number of driver instances to start (required)
  base-port   Starting host port to map (default: 8083)
  image-name  Docker image name to use/build (default: ev_driver:dev)
  network     Docker network to attach containers to (default: ev-network)
  bind-addr   Optional host IP to bind published ports to (default: bind on all interfaces / localhost). Example: 192.168.100.50

Examples:
  # start 3 drivers on ports 8083,8084,8085
  ./scripts/run-drivers.sh 3

  # start 5 drivers starting at port 9000 using a custom image name and bind to 192.168.100.50
  ./scripts/run-drivers.sh 5 9000 mydriver:latest my-network 192.168.100.50
EOF
}

if [[ ${#@} -lt 1 ]]; then
  usage
  exit 1
fi

COUNT=$1
if ! [[ "$COUNT" =~ ^[0-9]+$ ]] || [[ "$COUNT" -lt 1 ]]; then
  echo "Error: <count> must be a positive integer" >&2
  usage
  exit 2
fi

BASE_PORT=${2:-8083}
IMAGE_NAME=${3:-ev_driver:dev}
NETWORK=${4:-ev-network}
# Optional bind address for host-side port publishing. If empty, docker -p HOSTPORT:CONTAINERPORT binds to all interfaces (0.0.0.0) which is commonly shown as localhost in messages.
BIND_ADDR=${5:-}

# helper to check command
command -v docker >/dev/null 2>&1 || { echo "docker CLI not found in PATH. Install Docker and try again." >&2; exit 3; }

# Always build image to ensure latest local changes are included
echo "Building Docker image '$IMAGE_NAME' from driver/EV_Driver (forced rebuild)..."
docker build -t "$IMAGE_NAME" driver/EV_Driver

# Ensure network exists
if ! docker network ls --format '{{.Name}}' | grep -wq "$NETWORK"; then
  echo "Docker network '$NETWORK' not found; creating it."
  docker network create "$NETWORK"
else
  echo "Using existing Docker network '$NETWORK'."
fi

echo "Starting $COUNT driver(s) on ports starting at $BASE_PORT using image $IMAGE_NAME..."

default_env=( -e JAVA_OPTS=-Xmx256m -e SPRING_PROFILES_ACTIVE=prod )

started=()
for i in $(seq 1 "$COUNT"); do
  port=$((BASE_PORT + i - 1))
  name="ev-driver-${i}"

  # If a container with that name exists, remove it to avoid conflicts
  if docker ps -a --format '{{.Names}}' | grep -wq "$name"; then
    echo "Container $name exists; removing..."
    docker rm -f "$name"
  fi

  if [[ -n "$BIND_ADDR" ]]; then
    # Bind to specific host IP address: use ip:hostPort:containerPort format
    publish_arg="${BIND_ADDR}:${port}:8080"
    echo "Running container $name -> http://${BIND_ADDR}:${port}/ (binding to ${BIND_ADDR})"
  else
    publish_arg="${port}:8080"
    echo "Running container $name -> http://localhost:${port}/"
  fi

  docker run -d --name "$name" -p "$publish_arg" --network "$NETWORK" "${default_env[@]}" "$IMAGE_NAME" >/dev/null
  started+=("$name:$port")
done

echo "Started ${#started[@]} driver container(s):"
for s in "${started[@]}"; do
  name=${s%%:*}
  port=${s##*:}
  if [[ -n "$BIND_ADDR" ]]; then
    host_display=$BIND_ADDR
  else
    host_display=localhost
  fi
  echo "  - $name  ->  http://${host_display}:${port}/"
done

echo "Done. To follow logs, run: docker logs -f <container-name>"
