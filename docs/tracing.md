# Tracing Deep Dive

This guide expands on the tracing topics referenced in the labs. It covers Context, Span, Scope, Span Kinds, propagation, links, async patterns, and practical tips.

## Context, Span, and Scope

- Span: Timed unit of work. Add attributes/events; always end spans.
- Context: Immutable container for the current span and baggage.
- Scope: Activates a span in the current thread; use try-with-resources.

Why it matters: Incorrect context/scope leads to broken parent/child relationships.

### Minimal pattern
```java
Span span = tracer.spanBuilder("operation").startSpan();
try (Scope __ = span.makeCurrent()) {
  // work
} catch (Exception e) {
  span.recordException(e);
  span.setStatus(StatusCode.ERROR);
  throw e;
} finally {
  span.end();
}
```

## Span Kinds and Naming

- Use `SERVER` for inbound, `CLIENT` for outbound, `INTERNAL` for local work, `PRODUCER`/`CONSUMER` for messaging.
- Span names must be low-cardinality (e.g., `GET /users/{id}`), put IDs into attributes.

Gotchas:
- Naming spans with raw IDs creates high-cardinality time series and noisy UIs.
- Using the wrong kind breaks service maps and latency attribution.

## Propagation (W3C Trace Context)

- Extract on server; inject on client.
- Headers: `traceparent`, `tracestate`; optionally B3 for legacy interop.

Server extract:
```java
Context extracted = propagator.extract(Context.current(), request, REQUEST_HEADERS_GETTER);

Span server = tracer.spanBuilder("GET /v1/hello/:name")
        .setSpanKind(SpanKind.SERVER)
        .setParent(extracted)
        .startSpan();

Scope scope = Context.current().with(server).makeCurrent();
```

Client inject:
```java
GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
  .inject(Context.current(), httpRequestBuilder, (b,k,v) -> b.header(k,v));
```

## Async and Threads

Context does not flow across threads automatically.

### CompletableFuture
```java
Span parent = Span.current();
CompletableFuture.supplyAsync(() -> {
  try (Scope __ = parent.makeCurrent()) {
    // create child span inside async
    Span child = tracer.spanBuilder("async-work").setSpanKind(SpanKind.INTERNAL).startSpan();
    try (Scope ___ = child.makeCurrent()) {
      // work
    } finally {
      child.end();
    }
    return result;
  }
});
```

### Thread pools and executors
- Wrap runnables/callables with a Context-capturing wrapper when submitting to executors.
- Many frameworks offer helpers (e.g., `Context.current().wrap(runnable)`).

## Span Links

Links connect causally related spans outside direct parent/child trees (batch, async). Supported by SDK; visualization varies.

Example:
```java
Span consumer = tracer.spanBuilder("consume-message")
  .addLink(producerSpanContext)
  .setSpanKind(SpanKind.CONSUMER)
  .startSpan();
```

## Attributes vs Events vs Status

- Attributes: facts about the entire span.
- Events: timestamped milestones; good for debugging.
- Status: set to ERROR on failures (in addition to `recordException`).

Checklist:
- Use low-cardinality names; IDs in attributes.
- Always end spans; close scopes.
- On error: recordException + setStatus(ERROR).
- Propagate context on all I/O boundaries.
- Use links for batch/async causality.

## Baggage

- Key/value metadata propagated with the context for app-level concerns (e.g., `tenant.id`).
- Do not put secrets; keep values bounded.

## Resources

- OpenTelemetry Trace API: https://opentelemetry.io/docs/specs/otel/trace/api/
- Semantic Conventions (traces): https://opentelemetry.io/docs/specs/semconv/

