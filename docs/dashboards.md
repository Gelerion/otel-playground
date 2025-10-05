# Dashboards — Panels, Metrics, and Queries

This repo auto-provisions a Grafana dashboard (`lgtm/dashboards/otel-playground.json`) following industry best practices for observability dashboards. The dashboard is organized into logical sections with beautiful visualizations, color-coded thresholds, and exemplar links to traces.

## How provisioning works

1. Dashboard JSON lives in [`lgtm/dashboards/otel-playground.json`](../lgtm/dashboards/otel-playground.json).
2. [`lgtm/dashboards.yaml`](../lgtm/dashboards.yaml) tells Grafana to load dashboards from `/etc/grafana/provisioning/dashboards`.
3. [`docker-compose.yaml`](../docker-compose.yaml) mounts both files into the Grafana container.
4. On startup, Grafana auto-imports the dashboard; no manual import needed.

## Dashboard Design Best Practices

### **1. Organization & Layout**
- **Row groups**: Dashboard is divided into collapsible sections (Overview, Latency, Errors, Dependencies)
- **Logical flow**: Top-to-bottom progression from high-level health → detailed metrics → dependencies
- **Grid alignment**: 24-column grid with consistent panel sizing (4, 6, 8, 12, or 24 units wide)
- **Golden signals**: Latency, Traffic, Errors, and Saturation prominently displayed

### **2. Overview Section (At-a-Glance KPIs)**
Use **Stat panels** for instant health assessment:
- **Requests/sec** - Current traffic rate
- **Error Rate** - Percentage of failures
- **Latency (p95)** - Response time SLO
- **Active Requests** - In-flight request count
- **Success Rate** - Inverse of error rate
- **Total Requests** - Volume over time window

**Why**: Enables 5-second incident triage. If dashboard turns red, you know where to look.

### **3. Color Coding & Thresholds**
Use **semantic colors** aligned with SLOs:
```
Green  → Good (< threshold)
Yellow → Warning (approaching threshold)
Orange → Degraded (exceeded threshold)
Red    → Critical (severely degraded)
```

**Examples**:
- Latency: green < 300ms, yellow < 500ms, orange < 1s, red ≥ 1s
- Error rate: green < 1%, yellow < 5%, red ≥ 5%
- Success rate: red < 95%, yellow < 99%, green ≥ 99%

**Tip**: Use `colorMode: "background"` for error/success panels to make alerts obvious.

### **4. Visualization Types**
Choose the right visualization for the data:
- **Stat panels**: Current values with thresholds (KPIs, counters)
- **Gauge panels**: Single metric with visual threshold bands (SLOs)
- **Timeseries (line)**: Trends over time (latency, rates)
- **Timeseries (stacked)**: Composition/breakdown (status codes)
- **Timeseries (bars)**: Discrete events or grouped comparison (route volume)

### **5. Legends & Calculations**
Always include:
- **calcs**: `["mean", "max", "last"]` - shows average, peak, and current
- **displayMode**: `"table"` - easier to read than list
- **placement**: `"bottom"` for wide panels, `"right"` for tall panels

**Why**: Users need context beyond the current value. "Is this spike normal?" → check mean/max.

### **6. Exemplars (Metrics → Traces)**
Enable exemplars on **all histogram queries**:
```json
{
  "expr": "histogram_quantile(0.95, ...)",
  "exemplar": true
}
```

**How it works**: Prometheus stores sampled trace IDs with histogram buckets. Grafana renders them as clickable diamonds on charts. Click → opens the trace in Tempo.

**Use cases**:
- Latency spike → click exemplar → see which service was slow
- Error rate increase → click exemplar → see exception stack trace

### **7. Annotations**
Add deployment or event markers:
```json
"annotations": {
  "list": [{
    "expr": "changes(http_server_requests_total[1m]) > 0",
    "name": "Deployment Events"
  }]
}
```

### **8. Variables & Filters**
Add template variables for:
- Percentile selection (p50, p95, p99)
- Environment (prod, staging)
- Service instance (pod-1, pod-2)

**Benefit**: One dashboard for all environments/scenarios.

### **9. Performance Optimization**
- Use appropriate time ranges: `[1m]` for fast-changing metrics, `[5m]` for smoothed trends
- Avoid `rate(...[1m])` over large time windows (expensive)
- Pre-aggregate with `sum by (label)` before `histogram_quantile`
- Set `max_samples` limits for high-cardinality queries

### **10. Accessibility & Descriptions**
- **Title**: Clear, concise (e.g., "HTTP Server Latency" not "Latency")
- **Description**: Explain what's shown and why it matters
- **Units**: Always set (seconds, reqps, percentunit)
- **Consistent naming**: Use same terminology as in code/docs

## Dashboard Structure

The dashboard is organized into four main sections:

### 📊 **Service Health Overview**
At-a-glance KPIs using stat panels with color-coded thresholds:
- Requests/sec, Error Rate, Latency (p95), Active Requests, Success Rate, Total Requests

### 🚀 **Request Latency**
Detailed latency analysis with multiple visualizations:
- All percentiles (p50, p90, p95, p99) with exemplars
- Gauge panel showing real-time p95 with SLO bands
- Latency breakdown by route

### ⚠️ **Errors & Traffic**
Error analysis and traffic patterns:
- Error rate over time with alerting thresholds
- Request rate breakdown by status code (stacked area)
- Request volume by route (bar chart)

### 🔌 **Dependencies (DB & HTTP Clients)**
External service performance:
- Database client latency with exemplars
- HTTP client latency with exemplars

## Advanced Features

### Exemplars (Metrics → Traces)
Exemplars connect metrics to traces, enabling you to click on a data point and see the actual trace that contributed to that metric.

**Configuration**:
1. Prometheus must have exemplar storage enabled: `--enable-feature=exemplar-storage`
2. Datasource must link to Tempo: `exemplarTraceIdDestinations` in [`grafana-datasources.yaml`](../lgtm/grafana-datasources.yaml)
3. Query must have `"exemplar": true` in the target definition

**How to use**:
1. Look for small diamond/dot markers on latency charts
2. Click a marker → opens the corresponding trace in Tempo
3. Investigate the trace to see why that request was slow

**Gotcha**: Exemplars are sampled (not every request). If you don't see markers, generate more load.

### Traces → Logs
Configured via `tracesToLogsV2` in [`grafana-datasources.yaml`](../lgtm/grafana-datasources.yaml). From a trace in Tempo:
1. Click "Logs" button on any span
2. Grafana queries Loki for logs with matching `trace_id`
3. See correlated logs in context

**Why it matters**: Logs often contain error messages, stack traces, and context that traces don't capture.

## Practical Tips & Gotchas

### Query Performance
- **Avoid high cardinality**: `sum by (http_route)` is fine; `sum by (trace_id)` will explode
- **Choose window wisely**: `[1m]` for real-time, `[5m]` for trends, `[15m]` for stability
- **Pre-aggregate**: `histogram_quantile(0.95, sum by (le) (...))` is better than aggregating after quantile
- **Limit series**: Use label filters to reduce data: `{http_route="/api/products"}`

### Histogram Buckets
- **Default buckets may not fit**: OTel SDK's default buckets (0.005s to 10s) might not match your latency profile
- **Customize via Views**: Define buckets that bracket your p50, p95, p99 (see `docs/metrics.md`)
- **Too few buckets**: Quantiles will be inaccurate (linear interpolation between wide gaps)
- **Too many buckets**: Higher cardinality, more storage

### Color & Threshold Best Practices
- **Align with SLOs**: If your SLO is 500ms at p95, set yellow threshold at 450ms (early warning)
- **Don't overdo red**: If everything is red, nothing is urgent
- **Use background color sparingly**: Only for error/success rate panels (too distracting elsewhere)
- **Test thresholds**: Generate load with `make scenario-latency` and verify colors match expectations

### Dashboard Maintenance
- **Version control**: Dashboard JSON should be in Git (already is in this repo)
- **Provisioning vs. Manual**: Changes made in Grafana UI are ephemeral; re-export JSON to persist
- **UID stability**: Set `uid` field to prevent dashboard duplication on re-provision
- **Schema version**: Grafana auto-upgrades dashboards; expect schema changes over time

### Common Issues

#### "No data" in panels
1. Check Prometheus is scraping: `curl http://localhost:9090/api/v1/targets`
2. Verify metric names: `curl http://localhost:9090/api/v1/label/__name__/values | grep http_server`
3. Check time range: Extend to "Last 1 hour" if data is sparse
4. Verify datasource UID in queries matches `grafana-datasources.yaml`

#### Exemplars not showing
1. Confirm Prometheus has `--enable-feature=exemplar-storage`
2. Check `http_server_request_duration_seconds_bucket` has trace IDs: `curl 'http://localhost:9090/api/v1/query?query=http_server_request_duration_seconds_bucket'` (look for `exemplars` field)
3. Generate more load (exemplars are sampled)
4. Verify Tempo datasource UID in `exemplarTraceIdDestinations`

#### Incorrect quantile values
1. **Bucket boundaries**: Ensure buckets cover your latency range (see histogram buckets above)
2. **Aggregation order**: Always `sum by (le)` *before* `histogram_quantile`, never after
3. **Missing le label**: If you see flat lines, `le` label might be dropped; check aggregation

## Dashboard Iteration Workflow

1. **Make changes in Grafana UI** (fast iteration)
2. **Test with different scenarios**: `make scenario-latency`, `make scenario-errors`
3. **Export JSON**: Settings → JSON Model → Copy
4. **Update [`lgtm/dashboards/otel-playground.json`](../lgtm/dashboards/otel-playground.json)**
5. **Commit to Git**: `git add lgtm/dashboards/ && git commit -m "Updated dashboard"`
6. **Restart Grafana**: `docker-compose restart grafana` (or `make down && make up`)

**Pro tip**: Use Grafana's "Save as..." feature to create experimental dashboards without breaking the main one.
great