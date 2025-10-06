# Makefile for OpenTelemetry Playground

.PHONY: up down clean load help logs send-request

# Default target
help:
	@echo "OpenTelemetry Playground - Available Commands:"
	@echo ""
	@echo "  make up                        - Start the application and observability stack"
	@echo "  make down                      - Stop and remove the observability stack"
	@echo "  make logs                      - View server logs (tail -f server.log)"
	@echo "  make clean                     - Clean Maven build artifacts"
	@echo "  make send-request              - Send a single test request"
	@echo "  make load [mode=high-latency]  - Run continuous load generator"
	@echo "  make help                      - Show this help message"
	@echo ""

# Start the application and the observability stack
up:
	@echo "🚀 Starting OpenTelemetry Playground..."
	@echo ""
	@echo "ℹ️  Using default behavior (semi-low latency, 10% error rate)"
	@echo "   Switch behavior using X-Feature-Flag header:"
	@echo "     • default            - semi-low latency, 10% errors"
	@echo "     • high-latency       - high latency spikes, 10% errors"
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
		echo "  • Grafana Dashboard:    http://localhost:3000 (admin/admin123)"; \
		echo "  • Prometheus:           http://localhost:9090"; \
		echo ""; \
		echo "🧪 Test the application:"; \
		echo "  make send-request       # Single request"; \
		echo ""; \
		echo "📈 Run load generator:"; \
		echo "  make load                    # Default mode"; \
		echo "  make load mode=high-latency  # High-latency mode"; \
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

# Run the load generator with optional mode
load:
	@if [ -n "$(mode)" ]; then \
		echo "📈 Starting load generator (mode: $(mode))..."; \
	else \
		echo "📈 Starting load generator (default mode)..."; \
	fi
	@echo "Press Ctrl+C to stop the load generator."
	@echo ""
	@if [ -n "$(mode)" ]; then \
		./mvnw compile -q exec:java -Dexec.mainClass="com.gelerion.otel.playground.LoadGenerator" -Dexec.args="$(mode)"; \
	else \
		./mvnw compile -q exec:java -Dexec.mainClass="com.gelerion.otel.playground.LoadGenerator"; \
	fi

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

# Send a single request
send-request:
	@echo "🧪 Sending request..."
	@curl http://localhost:8080/v1/hello/john
