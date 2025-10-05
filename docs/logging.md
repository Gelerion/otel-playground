# Logging Deep Dive

Connect logs to traces using the OTel Log4j2 appender and/or MDC.

## OpenTelemetry Appender

- Add `<OpenTelemetry>` appender in [`log4j2.xml`](../src/main/resources/log4j2.xml).
- It captures logs, enriches with `trace_id`/`span_id` if an active span exists, and sends to the Collector.
- `captureContextDataAttributes="*"` preserves MDC keys; enable `captureCodeAttributes` for source info.

Example snippet:
```xml
<OpenTelemetry name="OpenTelemetryAppender"
               captureContextDataAttributes="*"
               captureMapMessageAttributes="true"
               captureMarkerAttribute="true"
               captureCodeAttributes="true"/>
```

## MDC (ThreadContext)

- Inject `trace_id` and `span_id` for non-OTel appenders and local debugging.
- Clear MDC in request cleanup to avoid cross-request leakage.

Example:
```java
SpanContext sc = Span.current().getSpanContext();
if (sc.isValid()) {
  ThreadContext.put("trace_id", sc.getTraceId());
  ThreadContext.put("span_id", sc.getSpanId());
}
```

## Pipeline notes

- Collector should receive logs over OTLP; verify the logs pipeline is enabled.
- Align tenant/labels between logs/metrics/traces to enable cross-linking in Grafana.

## Pitfalls

- Missing MDC cleanup → wrong IDs on subsequent requests.
- Logging sensitive data → sanitize fields and restrict log levels.
- Excessive message cardinality → hard to aggregate; prefer structured fields.

## Resources

- OTel Logs: https://opentelemetry.io/docs/specs/otel/logs/
