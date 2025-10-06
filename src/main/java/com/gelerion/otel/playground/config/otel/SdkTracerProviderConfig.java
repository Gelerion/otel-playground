package com.gelerion.otel.playground.config.otel;

import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.time.Duration;

public class SdkTracerProviderConfig {

    // Lab 2 default: log spans to console; Lab 3: switch to OTLP by replacing exporter
    public static SdkTracerProvider create(Resource resource) {
        return SdkTracerProvider.builder()
                .addResource(resource)
                .addSpanProcessor(batchSpanProcessor(otlpJsonLoggingSpanExporter()))
                .build();
    }

    // Lab 3: replace exporter with otlpHttpSpanExporter(HTTP_COLLECTOR_URL + "/v1/traces")
    private static SpanProcessor batchSpanProcessor(SpanExporter spanExporter) {
        return BatchSpanProcessor.builder(spanExporter)
                .setMaxQueueSize(2048)
                .setExporterTimeout(Duration.ofSeconds(30))
                .setScheduleDelay(Duration.ofSeconds(5))
                .build();
    }

    private static SpanExporter otlpHttpSpanExporter(String endpoint) {
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint)
                .setTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static SpanExporter otlpGrpcSpanExporter(String endpoint) {
        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .setTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static SpanExporter otlpJsonLoggingSpanExporter() {
        return OtlpJsonLoggingSpanExporter.create();
    }
}
