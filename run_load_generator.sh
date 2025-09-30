#!/bin/bash
# This script compiles and runs the load generator application.

echo "Starting the load generator..."
./mvnw compile exec:java -Dexec.mainClass="com.gelerion.otel.playground.LoadGenerator"
