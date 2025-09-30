package com.gelerion.otel.playground.metrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.*;
import io.opentelemetry.semconv.SchemaUrls;

public class MetricsProvider {

    // Prefers seconds for duration units (OTel semconv).
    private final DoubleHistogram reqDurationSec = meter()
            .histogramBuilder("http.server.request.duration")
            .setDescription("HTTP server request duration")
            .setUnit("s")
            .build();

    // Use this for outbound HTTP calls.
    // Attrs: http.request.method, server.address, server.port, http.response.status_code, optional url.scheme
    private final DoubleHistogram clientReqDurationSec = meter()
            .histogramBuilder("http.client.request.duration")
            .setDescription("HTTP client request duration")
            .setUnit("s")
            .build();

    // Use this for DB calls.
    // Attrs: db.system (postgresql/mysql/etc.), db.name, db.operation (SELECT/INSERT/...), db.sql.table (bounded), optional sanitized db.statement
    private final DoubleHistogram dbReqDurationSec = meter()
            .histogramBuilder("db.client.operation.duration")
            .setDescription("DB client request duration")
            .setUnit("s")
            .build();


    private final LongCounter reqTotal = meter()
            .counterBuilder("http.server.requests")
            .setDescription("Total HTTP requests")
            .setUnit("1")
            .build();

    // Tracks in-flight requests.
    private final LongUpDownCounter reqActive = meter()
            .upDownCounterBuilder("http.server.active_requests")
            .setDescription("Active HTTP requests")
            .setUnit("1")
            .build();

    public DoubleHistogram serverRequestDurationHistogram() {
        return reqDurationSec;
    }

    public DoubleHistogram clientRequestDurationHistogram() {
        return clientReqDurationSec;
    }

    public DoubleHistogram dbRequestDurationHistogram() {
        return dbReqDurationSec;
    }

    public LongCounter totalRequestsCounter() {
        return reqTotal;
    }

    public LongUpDownCounter activeRequestsCounter() {
        return reqActive;
    }

    public Meter meter() {
        return GlobalOpenTelemetry.meterBuilder("com.gelerion.otel.playground.http")
                .setSchemaUrl(SchemaUrls.V1_37_0) // See the README for more details.
                .setInstrumentationVersion("1.0.0")
                .build();
    }

}
