# Makefile for OpenTelemetry Playground

.PHONY: up down clean load status help logs

# Default target
help:
	@echo "OpenTelemetry Playground - Available Commands:"
	@echo ""
	@echo "  make up     - Start the application and observability stack"
	@echo "  make down   - Stop and remove the observability stack"
	@echo "  make load   - Run the load generator"
	@echo "  make logs   - View server logs (tail -f server.log)"
	@echo "  make status - Show status of all services"
	@echo "  make clean  - Clean Maven build artifacts"
	@echo "  make help   - Show this help message"
	@echo ""

# Start the application and the observability stack
up:
	@echo "🚀 Starting OpenTelemetry Playground..."
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
		echo "      - GET /v1/hello/:name"; \
		echo "  • Grafana Dashboard:    http://localhost:3000 (admin/admin)"; \
		echo "  • Prometheus:           http://localhost:9090"; \
		echo "  • Tempo (Traces):       http://localhost:3200"; \
		echo "  • Loki (Logs):          http://localhost:3100"; \
		echo "  • OTEL Collector:       http://localhost:4318 (HTTP) / localhost:4317 (gRPC)"; \
		echo ""; \
		echo "🧪 Test the application:"; \
		echo "  curl http://localhost:8080/v1/hello/Your-Name"; \
		echo ""; \
		echo "📈 Run load generator:"; \
		echo "  make load"; \
		echo ""; \
		echo "📋 View server logs:"; \
		echo "  tail -f server.log | jq ."; \
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

# Show status of all services
status:
	@echo "📊 OpenTelemetry Playground Status:"
	@echo ""
	@echo "Docker Services:"
	@docker-compose ps
	@echo ""
	@echo "Service Health Checks:"
	@echo -n "  • Java Application:     "
	@curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/v1/hello/health 2>/dev/null || echo "DOWN"
	@echo -n "  • Grafana:              "
	@curl -s -o /dev/null -w "%{http_code}" http://localhost:3000 2>/dev/null || echo "DOWN"
	@echo -n "  • Prometheus:           "
	@curl -s -o /dev/null -w "%{http_code}" http://localhost:9090 2>/dev/null || echo "DOWN"
	@echo -n "  • Tempo:                "
	@curl -s -o /dev/null -w "%{http_code}" http://localhost:3200 2>/dev/null || echo "DOWN"
	@echo -n "  • Loki:                 "
	@curl -s -o /dev/null -w "%{http_code}" http://localhost:3100 2>/dev/null || echo "DOWN"
	@echo ""

# Clean Maven build artifacts
clean:
	@echo "🧹 Cleaning Maven project..."
	@./mvnw clean
	@rm -f server.log server.pid
	@echo "✅ Clean completed!"
