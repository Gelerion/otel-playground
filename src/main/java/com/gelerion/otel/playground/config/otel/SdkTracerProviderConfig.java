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

import static com.gelerion.otel.playground.config.otel.SdkOtelConfig.HTTP_COLLECTOR_URL;

public class SdkTracerProviderConfig {

    /*
    SdkTracerProvider is configured by the application owner and consists of:
     - Resource: The resource with which spans are associated.
     - Sampler: Configures which spans are recorded and sampled.
     - SpanProcessors: Processes spans when they start and end.
     - SpanExporters: Exports spans out of process (in conjunction with associated SpanProcessors).
     - SpanLimits: Controls the limits of data associated with spans.
     */
    public static SdkTracerProvider create(Resource resource) {
        return SdkTracerProvider.builder()
                .addResource(resource)
                // Sends trace data to the logging exporter and prints it to the console in JSON format.
                //.addSpanProcessor(SimpleSpanProcessor.create(otlpJsonLoggingSpanExporter()))
                // Here is where we send trace data to the collector (make sure Docker Compose and Grafana Tempo are running).
                .addSpanProcessor(batchSpanProcessor(otlpHttpSpanExporter(HTTP_COLLECTOR_URL + "/v1/traces")))
                .build();
    }

    // A batch span processor is used to batch spans before exporting them.
    private static SpanProcessor batchSpanProcessor(SpanExporter spanExporter) {
        return BatchSpanProcessor.builder(spanExporter)
                .setMaxQueueSize(2048)
                .setExporterTimeout(Duration.ofSeconds(30))
                .setScheduleDelay(Duration.ofSeconds(5))
                .build();
    }

    // From opentelemetry-exporter-* libs.
    private static SpanExporter otlpHttpSpanExporter(String endpoint) {
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint)
                //.addHeader("api-key", "value")
                .setTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static SpanExporter otlpGrpcSpanExporter(String endpoint) {
        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                //.addHeader("api-key", "value")
                .setTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static SpanExporter otlpJsonLoggingSpanExporter() {
        return OtlpJsonLoggingSpanExporter.create();
    }
}
