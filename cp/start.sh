#!/bin/bash
./mvnw clean package -DskipTests -e

if [ $# -lt 2 ]; then
    echo "Usage: $0 <instance-name> \"<full-monitor-command>\""
    exit 1
fi

INSTANCE_NAME=$1
MONITOR_COMMAND=$2

# Add --no-cache to ensure fresh build
MONITOR_COMMAND="$MONITOR_COMMAND" docker-compose -p $INSTANCE_NAME build --no-cache
MONITOR_COMMAND="$MONITOR_COMMAND" docker-compose -p $INSTANCE_NAME up -d

echo "Started instance set: $INSTANCE_NAME"
echo "Monitor command: $MONITOR_COMMAND"