# Sampling Strategies — Keeping the Signal, Cutting the Noise

## SDK samplers
- AlwaysOn, AlwaysOff
- ParentBased(TraceIdRatioBased(p)) — common head sampling
- TraceIdRatioBased(p) — head sampling without parent-based logic

Example:
```java
SdkTracerProvider.builder()
    .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(0.1)))
    .build();
```

Trade-offs:
- Head samplers are cheap but may miss interesting tails.
- Parent-based preserves parent decisions across boundaries.

## Tail sampling (Collector)

Use Collector’s `tailsampling` processor to decide after spans complete.

Example (conceptual):
```yaml
processors:
  tailsampling:
    decision_wait: 10s
    policies:
      - name: error-spans
        type: status_code
        status_code:
          status_codes: [ ERROR ]
      - name: health-checks-low-rate
        type: span_name
        span_name:
          match_type: regexp
          services: [ otel-playground ]
          rules: [ "GET /health" ]
```

## Guidelines
- Sample at the edge (ingress) to reduce cost; keep important flows via tail rules.
- Preserve exemplars: consider higher sample for latency outliers.
- Keep config simple; complex rules are hard to reason about.

## Gotchas
- Head+tail mixed strategies can be tricky; document precedence.
- Unsampled spans won’t produce metrics exemplars; plan dashboards accordingly.
- Ensure baggage/attributes used in policies are present before export.

## Resources
- Collector tail sampling: https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/tailsamplingprocessor
