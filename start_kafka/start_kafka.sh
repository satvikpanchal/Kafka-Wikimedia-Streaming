#!/bin/bash

# Generate a random UUID
uuid=$(kafka-storage.sh random-uuid)

# Format storage using the generated UUID
~/kafka_2.13-3.5.0/bin/kafka-storage.sh format -t "$uuid" -c ~/kafka_2.13-3.5.0/config/kraft/server.properties

# Start the Kafka server
~/kafka_2.13-3.5.0/bin/kafka-server-start.sh ~/kafka_2.13-3.5.0/config/kraft/server.properties
