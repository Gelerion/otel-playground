# Semantic Conventions, Schema URLs & Design Principles

Use standard attribute names, declare schema versions, and design telemetry for long-term maintainability.

## Resource attributes

- Always set `service.name`, `service.version`, `service.namespace` (if applicable).
- Consider `deployment.environment`, `cloud.region`, `host.name` where relevant.

## Semantic Conventions (spans/metrics/logs)

- HTTP: `http.request.method`, `http.route`, `http.response.status_code`.
- DB: `db.system`, `db.operation`, `db.namespace`, `db.collection.name`.
- Client: `client.address`, `client.port`.

Do:
- Use low-cardinality values for names; IDs in attributes.
- Prefer canonical attribute keys; avoid custom synonyms.

Don't:
- Put PII/secrets into attributes.
- Use raw paths or query strings as labels.

## Schema URLs

- Declare the schema version (e.g., `SchemaUrls.V1_37_0`) on instrumentation.
- Backends can normalize attributes across versions.

## Examples

```java
tracerBuilder.setSchemaUrl(SchemaUrls.V1_37_0).setInstrumentationVersion("1.0.0");
span.setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "GET");
span.setAttribute(DbAttributes.DB_OPERATION, "SELECT");
```

## Design Principles

### Names
- Span names: low-cardinality logical ops (e.g., `GET /users/{id}`).
- Metric names: semantic, units in base SI (`s`, `By`).

### Cardinality
- Bound labels; avoid IDs in names.
- Prefer events for high-detail breadcrumbs.

### Pre-deployment Checklist
- [ ] Names low-cardinality
- [ ] Schema URL set
- [ ] Metrics in seconds
- [ ] Attributes follow semconv
- [ ] Dashboards use bounded labels

## Resources

- Semconv: https://opentelemetry.io/docs/specs/semconv/
- Schemas: https://opentelemetry.io/docs/specs/otel/schemas/
