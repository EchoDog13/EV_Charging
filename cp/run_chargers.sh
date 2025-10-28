#!/bin/bash

# Check if the number of chargers is provided as an argument
if [ -z "$1" ]; then
    echo "Usage: $0 <number_of_chargers>"
    echo "Example: $0 5"
    exit 1
fi

NUM_CHARGERS=$1 # Get the number of chargers from the first argument

# Validate that NUM_CHARGERS is a positive integer
if ! [[ "$NUM_CHARGERS" =~ ^[0-9]+$ ]] || [ "$NUM_CHARGERS" -le 0 ]; then
    echo "Error: Number of chargers must be a positive integer."
    exit 1
fi

# Define base ports for monitor and engine
# The script will add an increment to these for each charger instance.
BASE_MONITOR_PORT=7030
BASE_ENGINE_PORT=8030

# Define a base CHARGER_ID, each instance will get a unique ID
BASE_CHARGER_ID=1000

# Other global environment variables (e.g., for CENTRAL_IP, KAFKA_BROKER)
# These are loaded from your .env file or the shell environment.
# If you need to override them per instance, you can add them to the
# environment variables passed to docker-compose below.


echo "Starting $NUM_CHARGERS charger instances..."

# Loop to create and start each charger instance
for i in $(seq 1 $NUM_CHARGERS); do
    # Calculate unique ports for the current instance
    MONITOR_HOST_PORT=$((BASE_MONITOR_PORT + i - 1))
    ENGINE_HOST_PORT=$((BASE_ENGINE_PORT + i - 1))

    # Calculate unique CHARGER_ID
    CURRENT_CHARGER_ID=$((BASE_CHARGER_ID + i - 1))

    # Define a unique project name for this charger instance
    # This keeps their containers separate.
    PROJECT_NAME="cp${i}"

    echo "--- Setting up Charger instance $i (Project: $PROJECT_NAME) ---"
    echo "  CHARGER_ID: $CURRENT_CHARGER_ID"
    echo "  Monitor Port: $MONITOR_HOST_PORT:5050"
    echo "  Engine Port: $ENGINE_HOST_PORT:8080"

    # Use docker-compose -p to specify a project name.
    # We pass the calculated environment variables directly to docker-compose.
    # CENTRAL_IP, CENTRAL_PORT, KAFKA_BROKER will be picked up from your .env file.
    CHARGER_ID=$CURRENT_CHARGER_ID \
    MONITOR_HOST_PORT=$MONITOR_HOST_PORT \
    ENGINE_HOST_PORT=$ENGINE_HOST_PORT \
    docker-compose -p "$PROJECT_NAME" up -d --build --force-recreate

    if [ $? -eq 0 ]; then
        echo "Charger instance $i started successfully."
    else
        echo "Error starting Charger instance $i. Exiting."
        exit 1
    fi

    echo "" # Add a newline for readability
done

echo "All $NUM_CHARGERS charger instances have been launched."
echo "You can check them with 'docker ps' or 'docker-compose -p cp1 ps', 'docker-compose -p cp2 ps', etc."