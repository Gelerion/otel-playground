# Metrics Deep Dive

This guide expands the metrics topics from the labs: instruments, readers, exporters, views, buckets, and exemplars.

## Core Components

- Instruments: Counter, UpDownCounter, Histogram, ObservableGauge/Counter/UpDownCounter.
- Reader: bridges SDK to exporters. This project uses `PeriodicMetricReader` (push).
- Exporter: OTLP HTTP to the Collector; alternatives include gRPC and logging.
- Views: customize aggregation, attributes, and cardinality limits; define histogram buckets.

## Choosing instruments

- Request counts → Counter
- In-flight requests → UpDownCounter
- Latency → Histogram (seconds)
- Utilization/point-in-time → ObservableGauge

Example:
```java
Meter meter = GlobalOpenTelemetry.getMeter("otel-playground");

Histogram serverLatency = meter.histogramBuilder("http.server.request.duration")
  .setUnit("s")
  .setDescription("Inbound HTTP latency")
  .build();
```

## Views and buckets (seconds)

Default buckets are millisecond-oriented. Define explicit buckets suitable for seconds via a View.
```java
List<Double> buckets = List.of(0.1, 0.2, 0.3, 0.5, 0.75, 1d, 1.5, 2d, 3d, 5d, 7d);

View finerBuckets = View.builder()
        .setAggregation(Aggregation.explicitBucketHistogram(buckets))
        .build();

InstrumentSelector hist = InstrumentSelector.builder()
        .setType(InstrumentType.HISTOGRAM)
        .build();

SdkMeterProvider.builder().registerView(hist, finerBuckets);
```

## Push vs Pull

- Pull (Prometheus scrape) is robust, but does not preserve exemplars.
- Push (remote_write) preserves exemplars and enables trace-to-metric linking.

## Exemplars

Exemplars attach `trace_id` and `span_id` to metric points when a span is current.

Tips:
- Record within the active span’s scope to improve exemplar linkage.
- Backends must support exemplars to visualize them.

## Attribute cardinality

- Keep labels bounded (e.g., `http.route`, not raw paths).
- Avoid user IDs, UUIDs, stack traces.
- Consider views with `AttributeFilter`s (when available) or pre-normalize labels.

## Recording patterns

```java
Attributes attrs = Attributes.builder()
  .put(HttpAttributes.HTTP_REQUEST_METHOD, request.requestMethod())
  .put(HttpAttributes.HTTP_ROUTE, request.pathInfo())
  .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, response.status())
  .build();

serverLatency.record(seconds, attrs);
```

## Dashboards and queries

- p99 lat: see [docs/queries.md](queries.md) for `histogram_quantile`.
- Error rate and saturation: include counters for `total` and `errors`.

## Pitfalls

- Recording durations in seconds but using ms-oriented buckets → flat histograms.
- Exploding cardinality from dynamic labels → memory/CPU pressure.
- Scrape path with exemplars expectation → won’t work; use push.

## Resources

- OTel Metrics: https://opentelemetry.io/docs/specs/otel/metrics/
- Semconv (metrics): https://opentelemetry.io/docs/specs/semconv/
