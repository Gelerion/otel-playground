# OpenTelemetry Playground: Manual Instrumentation

Welcome to our hands-on guide to understanding OpenTelemetry through manual instrumentation of a Java application. We'll explore the core concepts of observability—traces, metrics, and logs—without the "magic" of auto-instrumentation. If you've ever wanted to understand how OpenTelemetry *really* works under the hood, this is the project for you.

We provide a fully-functional example demonstrating how to configure the OpenTelemetry SDK, create and manage signals, and correlate them to get a complete picture of your application's behavior. We'll also cover best practices, semantic conventions, and some of the quirks and less-known details I've discovered along the way.

## Tutorial Path (Hands-on Labs)

This repository doubles as a tutorial. Follow these labs in order. Each lab has a goal, steps, verification, and an optional challenge.

### Branching Model for Labs

- **Lab 0 (End-state Demo):** Run on `main` branch to see the complete, fully-instrumented application with all OpenTelemetry features enabled.
- **Labs 1+ (Incremental Build-up):** Switch to `labs-branch` for a minimal baseline. You'll progressively add tracing, metrics, logging, and remote calls, building your understanding step-by-step.

### Lab 0 – End-state Demo (on `main` branch)

- **Goal:** Experience the fully-instrumented application with all signals (traces, metrics, logs) flowing to the observability stack.
- **Branch:** `main`
- **Steps:**
    1. Switch to main: `git checkout main`
    2. Start everything: `make up`
    3. Send a request: `make send-request` or `curl http://localhost:8080/v1/hello/gelerion`
    4. Explore Grafana at [http://localhost:3000](http://localhost:3000) (admin/admin123)
- **Verify:**
    - **Traces:** Grafana → Explore → Tempo: find a trace for service `otel-playground` with span `GET /v1/hello/:name`, including nested CLIENT spans for HTTP and DB calls.
    - **Metrics:** Grafana → Explore → Prometheus (or [http://localhost:9090](http://localhost:9090)): check metric series exist (see [docs/queries.md](docs/queries.md)). Look for exemplars linking metrics to traces.
    - **Logs:** Grafana → Explore → Loki: logs contain `trace_id`/`span_id` and structured fields.
- **Challenge:** Run `make load` (or `make load mode=high-latency`) and watch dashboards, p95/p99 latencies, and error rates evolve in real-time.

### Lab 1 – Logs-only Baseline (on `labs-branch`)

- **Goal:** Run the minimal app and inspect structured logs; no OpenTelemetry SDK or Collector needed.
- **Branch:** `labs-branch`
- **Where it is:**
    - Minimal endpoint: `src/main/java/com/gelerion/otel/playground/controller/HelloWorldController.java`
    - Server bootstrap (no OTel init): `src/main/java/com/gelerion/otel/playground/Server.java`
- **Enable it:**
    1. `git checkout labs-branch`
    2. `make up`
    3. `make send-request`
    4. Tail logs locally: `make logs` (reads `server.log`)
- **Verify:**
    - HTTP 200 response
    - JSON logs in `server.log` with request info
    - No traces/metrics exported yet
- **Challenge:** Add a custom field (e.g., `user_id`) in controller logs and confirm it appears in `server.log`.

**Key concepts**
- Start with logs; you’ll add tracing/metrics in later labs.

### Lab 2 – Introduce OpenTelemetry SDK + SERVER span (Exporter OFF)

- **Goal:** Initialize the OpenTelemetry SDK, create a SERVER span, and inject `trace_id`/`span_id` into logs—without sending traces to the Collector yet.
- **Branch:** `labs-branch` (continue from Lab 1)
- **Where it is:**
    - SDK wiring: `config/otel/SdkOtelConfig.java`
    - SERVER span filter: `filters/before/OtelContextPropagationBeforeFilter.java`
    - MDC trace context: `filters/before/LoggingTraceContextSetterBeforeFilter.java`
    - Cleanup: `Server.java` (`afterAfter`)
- **Enable it:**
    1. Call `SdkOtelConfig.init()` from `Server.main`
    2. Register `OtelContextPropagationBeforeFilter` and `LoggingTraceContextSetterBeforeFilter`
    3. Add `afterAfter` cleanup as shown in `Server.java`
    4. `make up` → `make send-request` → `make logs`
- **Verify:**
    - Logs include `trace_id` and `span_id`
    - No traces in Tempo yet (exporter off by default)
- **Verify:**
    - Logs now contain `trace_id` and `span_id` fields.
    - No traces appear in Tempo yet (exporter is off).
- **Learn more:** [docs/tracing.md](docs/tracing.md) (Context, Scope, Span lifecycle)
- **Challenge:** Add an event to the active span in [`HelloWorldController.java`](src/main/java/com/gelerion/otel/playground/controller/HelloWorldController.java) using `Span.current().addEvent(...)` and confirm it appears in logs (when exporter is later enabled, it will show in Tempo).

**Key concepts**
- A `Span` represents a unit of work; a `Scope` makes it current for this thread.
- Always close the `Scope` and end the span to avoid leaks.
- MDC injection allows traditional log aggregators to correlate logs with traces.

<details>
<summary>Code excerpts (view sources linked)</summary>

```java
// Extract incoming context (or start a new trace)
Context extracted = propagator.extract(Context.current(), request, REQUEST_HEADERS_GETTER);

var serverSpan = tracer().spanBuilder(request.requestMethod() + " " + request.pathInfo())
        .setSpanKind(SpanKind.SERVER)
        .setParent(extracted)
        .startSpan();

Scope scope = Context.current().with(serverSpan).makeCurrent();
request.attribute(OTEL_SCOPE_ATTR, scope);
request.attribute(OTEL_SERVER_SPAN_ATTR, serverSpan);
```

View source: [`OtelContextPropagationBeforeFilter.java`](src/main/java/com/gelerion/otel/playground/filters/before/OtelContextPropagationBeforeFilter.java)

```java
// Inject trace context into MDC for logging
SpanContext sc = Span.current().getSpanContext();
if (sc.isValid()) {
    ThreadContext.put("trace_id", sc.getTraceId());
    ThreadContext.put("span_id", sc.getSpanId());
}
```

View source: [`LoggingTraceContextSetterBeforeFilter.java`](src/main/java/com/gelerion/otel/playground/filters/before/LoggingTraceContextSetterBeforeFilter.java)

</details>

### Lab 3 – Enable Exporter and Collector (Traces)

- **Goal:** Turn on the OTLP exporter to send traces to the OpenTelemetry Collector, and verify traces appear in Tempo.
- **Branch:** `labs-branch` (continue from Lab 2)
- **Where it is:** `config/otel/SdkTracerProviderConfig.java`
- **Enable it:**
    1. Replace logging exporter with OTLP exporter:
       `.addSpanProcessor(batchSpanProcessor(otlpHttpSpanExporter(HTTP_COLLECTOR_URL + "/v1/traces")))`
    2. `make down && make up`
    3. `make send-request`, then open Grafana → Explore → Tempo
- **Verify:**
    - Find a trace for service `otel-playground` with a SERVER span `GET /v1/hello/:name`.
    - SpanKind is `SERVER`; attributes include `http.request.method`, `http.route`, `http.response.status_code`.
    - Logs still contain `trace_id`/`span_id` matching the trace in Tempo.
- **Learn more:** [docs/tracing.md](docs/tracing.md) (Exporters, BatchSpanProcessor)
- **Challenge:** Add an event in [`HelloWorldController.java`](src/main/java/com/gelerion/otel/playground/controller/HelloWorldController.java) using `Span.current().addEvent("Request received", Attributes.of(stringKey("param"), name))` and confirm it appears in Tempo under the "Events" tab.

**Key concepts**
- The OTLP exporter sends telemetry to the Collector via HTTP or gRPC.
- `BatchSpanProcessor` batches spans before export for efficiency.
- Traces are now visible end-to-end: created in the app, exported to Collector, stored in Tempo, queried in Grafana.

### Lab 4 – Metrics: Histogram + Views + Exemplars

- **Goal:** Record request latency using histograms with custom buckets, and see exemplars linking metrics to traces.
- **Branch:** `labs-branch` (continue from Lab 3)
- **Where it is:**
    - Buckets/view + exporter: `config/otel/SdkMeterProviderConfig.java`
    - Recording: `filters/after/MetricsRecorderAfterFilter.java`
- **Enable it:**
    1. Keep default logging exporter during Lab 2/3
    2. Switch to OTLP exporter:
       `.registerMetricReader(periodicMetricReader(otlpHttpMetricExporter(HTTP_COLLECTOR_URL + "/v1/metrics")))`
    3. `make down && make up`
    4. `make load` (optional `mode=high-latency`)
    5. Query in Grafana → Explore → Prometheus
- **Verify:**
    - PromQL p99 query returns values (see [docs/queries.md](docs/queries.md)).
    - Exemplars link metrics to traces in Grafana (click on an exemplar to jump to the corresponding trace in Tempo).
- **Learn more:** [docs/metrics.md](docs/metrics.md)
- **Challenge:** Adjust bucket boundaries in [`SdkMeterProviderConfig.java`](src/main/java/com/gelerion/otel/playground/config/otel/SdkMeterProviderConfig.java), restart, then run `make load mode=high-latency`; observe how p95/p99 shift with different buckets.

**Key concepts**
- Use `View`s to define explicit histogram buckets in seconds (not milliseconds).
- Push metrics via `PeriodicMetricReader` to preserve exemplars (pull-based scraping often drops them).
- Record metrics while a span is current to increase exemplar linkage.

<details>
<summary>Code excerpts (view sources linked)</summary>

```java
List<Double> buckets = List.of(0.1, 0.2, 0.3, 0.5, 0.75, 1d, 1.5, 2d, 3d, 5d, 7d);

View finerBucketsView = View.builder()
        .setAggregation(Aggregation.explicitBucketHistogram(buckets))
        .build();

SdkMeterProviderBuilder builder = SdkMeterProvider.builder()
        .registerView(InstrumentSelector.builder().setType(InstrumentType.HISTOGRAM).build(), finerBucketsView);
```

View source: [`SdkMeterProviderConfig.java`](src/main/java/com/gelerion/otel/playground/config/otel/SdkMeterProviderConfig.java)

```java
double seconds = durationsSeconds(request.attribute("__startNanos"));

Attributes attributes = Attributes.builder()
        .put(HttpAttributes.HTTP_REQUEST_METHOD, request.requestMethod())
        .put(HttpAttributes.HTTP_ROUTE, request.pathInfo())
        .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, response.status())
        .build();

metricsProvider.serverRequestDurationHistogram().record(seconds, attributes);
```

View source: [`MetricsRecorderAfterFilter.java`](src/main/java/com/gelerion/otel/playground/filters/after/MetricsRecorderAfterFilter.java)

</details>

### Lab 5 – HTTP CLIENT Span (Propagation)

- **Goal:** Create an outbound HTTP CLIENT span and propagate W3C trace context to downstream services.
- **Branch:** `labs-branch` (continue from Lab 4)
- **Steps:**
    1. Implement [`RemoteClient.java`](src/main/java/com/gelerion/otel/playground/clients/RemoteClient.java) with a CLIENT span and W3C header injection.
    2. Call the remote client from [`HelloWorldController.java`](src/main/java/com/gelerion/otel/playground/controller/HelloWorldController.java).
    3. Send a request: `make send-request`
    4. Open Grafana → Explore → Tempo and find the trace
- **Verify:**
    - The trace shows a nested CLIENT span under the SERVER span.
    - SpanKind is `CLIENT`; attributes include `http.request.method`, `server.address`, `server.port`.
    - W3C headers (`traceparent`, `tracestate`) are injected into the outbound HTTP request.
- **Learn more:** [docs/tracing.md#propagation](docs/tracing.md#propagation)
- **Challenge:** Simulate an error path (e.g., invalid URL or timeout), then ensure `recordException` and `setStatus(ERROR, ...)` are both called. Verify in Tempo: Status=ERROR, Exception event present.

**Key concepts**
- CLIENT spans are children of the current SERVER span.
- W3C `traceparent` header propagates trace context to downstream services.
- Mark failures with both `recordException` and `setStatus(ERROR, ...)` for proper error tracking.

<details>
<summary>Code excerpts (view source linked)</summary>

```java
Span span = OtelContextPropagationBeforeFilter.tracer()
        .spanBuilder("HTTP POST /api/v1/recommend")
        .setSpanKind(SpanKind.CLIENT)
        .startSpan();

try (Scope __ = span.makeCurrent()) {
    GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .inject(Context.current(), builder, SETTER);
    // ... perform HTTP call
} catch (Exception ex) {
    span.recordException(ex);
    span.setStatus(StatusCode.ERROR, ex.getMessage());
    throw ex;
} finally {
    span.end();
}
```

View source: [`RemoteClient.java`](src/main/java/com/gelerion/otel/playground/clients/RemoteClient.java)

</details>

### Lab 6 – DB CLIENT Span

- **Goal:** Create a DB CLIENT span with semantic convention attributes for database operations.
- **Branch:** `labs-branch` (continue from Lab 5)
- **Steps:**
    1. Implement [`DbOperations.java`](src/main/java/com/gelerion/otel/playground/repository/DbOperations.java) with a CLIENT span for DB queries.
    2. Add semantic convention attributes: `db.system`, `db.operation.name`, `db.query.text`, etc.
    3. Call the DB operations from [`HelloWorldController.java`](src/main/java/com/gelerion/otel/playground/controller/HelloWorldController.java).
    4. Send a request: `make send-request`
    5. Open Grafana → Explore → Tempo and find the trace
- **Verify:**
    - The trace shows a nested CLIENT span for the DB operation under the SERVER span.
    - SpanKind is `CLIENT`; attributes include `db.system`, `db.operation.name`, `db.query.text`.
- **Learn more:** [docs/tracing.md](docs/tracing.md), [docs/semconv-schema.md](docs/semconv-schema.md)
- **Challenge:** Add error handling for DB operations; ensure `recordException` and `setStatus(ERROR, ...)` are called on failures. Verify in Tempo.

**Key concepts**
- Use semantic conventions for DB spans (`db.*` attributes) to ensure consistency.
- CLIENT spans for DB operations help visualize query latency and failures.

### Lab 7 – Semantic Conventions and Schema URLs

- **Goal:** Apply semantic conventions consistently and declare schema version for all instrumentation.
- **Branch:** `labs-branch` (continue from Lab 6)
- **Steps:**
    1. Review instrumentation version and schema URL in [`OtelContextPropagationBeforeFilter.java`](src/main/java/com/gelerion/otel/playground/filters/before/OtelContextPropagationBeforeFilter.java#L75-L81).
    2. Ensure all attributes use canonical names from semconv: `http.*`, `db.*`, `client.*`, `server.*`.
    3. Set schema URL for tracers, meters, and loggers.
    4. Send a request: `make send-request`
    5. Verify attributes in Tempo and Prometheus
- **Verify:**
    - Attributes follow canonical semconv naming.
    - Schema URL is set on tracers/meters/loggers (check resource attributes in Tempo).
- **Learn more:** [docs/semconv-schema.md](docs/semconv-schema.md)
- **Challenge:** Bump schema version (e.g., from `V1_37_0` to a newer version) and observe how the backend normalizes attributes. Check Grafana for any migration warnings.

**Key concepts**
- Semantic conventions ensure consistency across services and teams.
- Schema URLs enable backends to normalize attributes when conventions evolve.

<details>
<summary>Code excerpts</summary>

```java
// filters/before/OtelContextPropagationBeforeFilter.java
return GlobalOpenTelemetry.tracerBuilder("com.gelerion.otel.playground.http")
        .setInstrumentationVersion("1.0.0")
        .setSchemaUrl(SchemaUrls.V1_37_0)
        .build();
```

</details>

### Lab 8 – Span Links and Async (Optional Advanced)

- **Goal:** Model causality outside parent/child trees using Span Links.
- **Branch:** `labs-branch` (continue from Lab 7)
- **Steps:**
    1. Review the Span Links example in [docs/tracing.md#span-links](docs/tracing.md#span-links).
    2. Create an `INTERNAL` span with a `SpanLink` to a prior trace or to the incoming `SERVER` span.
    3. Send a request: `make send-request`
    4. Open Grafana → Explore → Tempo and find the trace
- **Verify:**
    - Linked span metadata appears in the Tempo UI (support varies by backend).
    - Links show causality between spans that aren't in a parent/child relationship.
- **Learn more:** [docs/tracing.md#span-links](docs/tracing.md#span-links), [docs/async.md](docs/async.md)
- **Challenge:** Model an async batch job that processes multiple requests: create spans for each batch item with links back to the original request spans. Verify in Tempo.

**Key concepts**
- Span Links model causality when parent/child relationships don't apply (e.g., async work, batch processing, fan-out/fan-in).
- Links are created at span start time and cannot be added later.