#!/bin/bash

# Usage check
if [ -z "$1" ]; then
    echo "Usage: $0 <number_of_chargers>"
    echo "Example: $0 5"
    exit 1
fi

NUM_CHARGERS=$1

# Validate number
if ! [[ "$NUM_CHARGERS" =~ ^[0-9]+$ ]] || [ "$NUM_CHARGERS" -le 0 ]; then
    echo "Error: Number of chargers must be a positive integer."
    exit 1
fi

# Base ports
BASE_MONITOR_PORT=5050
BASE_ENGINE_PORT=50000

# Base charger ID
BASE_CHARGER_ID=1000

# Network-wide variables (adjust to your environment)
CENTRAL_IP="100.83.66.30"
CENTRAL_PORT=5500
KAFKA_BROKER="100.83.66.30:9092"

MONITOR_HOST="100.123.78.116"
ENGINE_HOST="100.123.78.116"

echo "Starting $NUM_CHARGERS charger instances..."

for i in $(seq 1 $NUM_CHARGERS); do
    # Calculate ports and IDs
    MONITOR_HOST_PORT=$((BASE_MONITOR_PORT + i - 1))
    ENGINE_HOST_PORT=$((BASE_ENGINE_PORT + i - 1))
    CURRENT_CHARGER_ID=$((BASE_CHARGER_ID + i - 1))
    CHARGER_APP_NAME="$CURRENT_CHARGER_ID"
    PROJECT_NAME="cp$i"

    echo "--- Setting up Charger instance $i (Project: $PROJECT_NAME) ---"
    echo "  CHARGER_ID: $CURRENT_CHARGER_ID"
    echo "  Monitor Port: $MONITOR_HOST_PORT:5050"
    echo "  Engine Port: $ENGINE_HOST_PORT:8080"
    echo "  Application Name: $CHARGER_APP_NAME"

    # Start containers with docker-compose using inline environment variables
    CHARGER_ID=$CURRENT_CHARGER_ID \
    CHARGER_APP_NAME=$CHARGER_APP_NAME \
    MONITOR_HOST_PORT=$MONITOR_HOST_PORT \
    ENGINE_HOST_PORT=$ENGINE_HOST_PORT \
    CENTRAL_IP=$CENTRAL_IP \
    CENTRAL_PORT=$CENTRAL_PORT \
    KAFKA_BROKER=$KAFKA_BROKER \
    MONITOR_HOST=$MONITOR_HOST \
    ENGINE_HOST=$ENGINE_HOST \
    CHARGER_LOCATION="University of Waikato $i" \
    docker compose -p "$PROJECT_NAME" up -d --build --force-recreate

    if [ $? -eq 0 ]; then
        echo "Charger instance $i started successfully."
    else
        echo "Error starting Charger instance $i. Exiting."
        exit 1
    fi

    echo "" # newline for readability
done

echo "All $NUM_CHARGERS charger instances have been launched."
echo "Check with 'docker ps' or 'docker compose -p cp1 ps', 'docker compose -p cp2 ps', etc."