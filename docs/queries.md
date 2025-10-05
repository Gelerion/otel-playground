# Query Cheat Sheet

## PromQL

- p99 latency from histogram (HTTP server):
```
histogram_quantile(0.99, sum(rate(http_server_request_duration_bucket[5m])) by (le))
```
- p50/p90 by route:
```
histogram_quantile(0.9, sum by (le, http_route) (rate(http_server_request_duration_bucket[5m])))
```
- Error rate (HTTP 5xx):
```
sum(rate(http_server_requests_total{http_response_status_code=~"5.."}[5m]))
/
sum(rate(http_server_requests_total[5m]))
```
- In-flight requests:
```
sum(http_server_active_requests)
```

## LogQL (Loki)

- JSON logs with trace correlation:
```
{app="otel-playground"} | json | trace_id != ""
```
- Filter by route and status:
```
{app="otel-playground"} | json | http.route="/v1/hello/:name" | http.response.status_code=200
```
- Join from trace to logs (UI feature): ensure `tracesToLogs` is configured in [`grafana-datasources.yaml`](../lgtm/grafana-datasources.yaml).

## Tempo (Traces)

- Search by service `otel-playground` and span name `GET /v1/hello/:name`.
- Use trace ID from Grafana panels to open in Tempo. 
