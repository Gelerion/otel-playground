# Makefile for OpenTelemetry Playground

.PHONY: up down clean load help logs scenario-basics scenario-errors scenario-latency

# Default target
help:
	@echo "OpenTelemetry Playground - Available Commands:"
	@echo ""
	@echo "  make up                - Start the application and observability stack"
	@echo "  make down              - Stop and remove the observability stack"
	@echo "  make load              - Run the load generator"
	@echo "  make logs              - View server logs (tail -f server.log)"
	@echo "  make clean             - Clean Maven build artifacts"
	@echo "  make scenario-basics   - Run with low latency & no errors"
	@echo "  make scenario-errors   - Run with high error rates"
	@echo "  make scenario-latency  - Run with high latency"
	@echo "  make help              - Show this help message"
	@echo ""

# Start the application and the observability stack
# Uses hardcoded defaults in Java code (~20-30% error rate, moderate latency)
# For specific scenarios, use: make scenario-basics, make scenario-errors, or make scenario-latency
up:
	@echo "🚀 Starting OpenTelemetry Playground..."
	@echo ""
	@echo "ℹ️  Using default behavior (code defaults: ~20-30% error rate, 200-3000ms latency)"
	@echo "   For specific scenarios: make scenario-basics, make scenario-errors, or make scenario-latency"
	@echo ""
	@echo "Starting Docker containers for the observability stack..."
	@docker-compose up -d
	@echo ""
	@echo "Compiling Java application..."
	@if ./mvnw compile -q; then \
		echo "✅ Compilation successful!"; \
		echo ""; \
		echo "Starting the Java application..."; \
		./mvnw exec:java -q > server.log 2>&1 & \
		echo $$! > server.pid; \
		sleep 3; \
		echo ""; \
		echo "✅ OpenTelemetry Playground is now running!"; \
		echo ""; \
		echo "📊 Service URLs:"; \
		echo "  • Java Application:     http://localhost:8080"; \
		echo "    Available endpoints:"; \
		echo "      - GET http://localhost:8080/v1/hello/:name"; \
		echo "  • Grafana Dashboard:    http://localhost:3000 (admin/admin)"; \
		echo "  • Prometheus:           http://localhost:9090"; \
		echo ""; \
		echo "🧪 Test the application:"; \
		echo "  curl http://localhost:8080/v1/hello/Your-Name"; \
		echo ""; \
		echo "📈 Run load generator:"; \
		echo "  make load"; \
		echo ""; \
		echo "📋 View server logs:"; \
		echo "  make logs"; \
		echo ""; \
		echo "🛑 Stop everything:"; \
		echo "  make down"; \
	else \
		echo "❌ Compilation failed! Showing errors:"; \
		./mvnw compile; \
		exit 1; \
	fi

# Stop and remove the observability stack
down:
	@echo "🛑 Stopping OpenTelemetry Playground..."
	@if [ -f server.pid ]; then \
		echo "Stopping Java application..."; \
		kill $$(cat server.pid) 2>/dev/null || true; \
		rm -f server.pid; \
		rm -f server.log; \
	fi
	@echo "Stopping and removing Docker containers..."
	@docker-compose down
	@echo "✅ All services stopped!"

# Run the load generator
load:
	@echo "📈 Starting load generator..."
	@echo "This will generate traffic to test the observability stack."
	@echo "Press Ctrl+C to stop the load generator."
	@echo ""
	@./mvnw compile -q exec:java -Dexec.mainClass="com.gelerion.otel.playground.LoadGenerator"

# View server logs
logs:
	@if [ -f server.log ]; then \
		echo "📋 Viewing server logs (Press Ctrl+C to exit):"; \
		echo ""; \
		if command -v jq >/dev/null 2>&1; then \
			tail -f server.log | jq .; \
		else \
			echo "💡 Tip: Install 'jq' for beautiful JSON formatting: brew install jq"; \
			echo ""; \
			tail -f server.log; \
		fi; \
	else \
		echo "❌ No server logs found. Is the server running?"; \
		echo "Run 'make up' to start the server."; \
	fi

# Clean Maven build artifacts
clean:
	@echo "🧹 Cleaning Maven project..."
	@./mvnw clean
	@rm -f server.log server.pid
	@echo "✅ Clean completed!"

# Scenario: low latency, no errors (ideal for basics labs)
scenario-basics:
	@echo "🎯 Starting scenario: Basics (low latency, no errors)"
	@if [ -f server.pid ]; then \
		echo "⚠️  Server is already running. Run 'make down' first to switch scenarios."; \
		exit 1; \
	fi
	@export CLIENT_LATENCY_MIN_MS=50 CLIENT_LATENCY_MAX_MS=150 CLIENT_ERROR_THRESHOLD=100 \
	       DB_LATENCY_MIN_MS=50 DB_LATENCY_MAX_MS=200 DB_ERROR_THRESHOLD=100 && \
	$(MAKE) up

# Scenario: high error rate (for error tracking labs)
scenario-errors:
	@echo "🎯 Starting scenario: Errors (high failure rate)"
	@if [ -f server.pid ]; then \
		echo "⚠️  Server is already running. Run 'make down' first to switch scenarios."; \
		exit 1; \
	fi
	@export CLIENT_LATENCY_MIN_MS=200 CLIENT_LATENCY_MAX_MS=800 CLIENT_ERROR_THRESHOLD=5 \
	       DB_LATENCY_MIN_MS=200 DB_LATENCY_MAX_MS=1000 DB_ERROR_THRESHOLD=3 && \
	$(MAKE) up

# Scenario: high latency (for performance debugging labs)
scenario-latency:
	@echo "🎯 Starting scenario: Latency (slow operations)"
	@if [ -f server.pid ]; then \
		echo "⚠️  Server is already running. Run 'make down' first to switch scenarios."; \
		exit 1; \
	fi
	@export CLIENT_LATENCY_MIN_MS=1000 CLIENT_LATENCY_MAX_MS=3000 CLIENT_ERROR_THRESHOLD=11 \
	       DB_LATENCY_MIN_MS=2000 DB_LATENCY_MAX_MS=5000 DB_ERROR_THRESHOLD=7 && \
	$(MAKE) up
