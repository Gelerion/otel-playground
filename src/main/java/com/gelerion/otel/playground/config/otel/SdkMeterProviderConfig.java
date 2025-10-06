package com.gelerion.otel.playground.config.otel;

import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.metrics.*;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;

import java.time.Duration;
import java.util.List;

// Note: HTTP_COLLECTOR_URL is used in later labs when switching to OTLP

// Lab-focused notes:
// - Lab 4 moves metrics to OTLP; earlier labs log or keep disabled
// - Views configure histogram buckets (seconds)
public class SdkMeterProviderConfig {

    public static SdkMeterProvider create(Resource resource) {
        // Lab 4 default will switch to OTLP; for Lab 2/3 keep metrics logging (or disabled)
        MetricExporter otlJsonMetricExporter = OtlpJsonLoggingMetricExporter.create();

        // Reader: push metrics periodically to the configured exporter

        // Views: explicit histogram buckets in seconds
        List<Double> buckets = List.of(0.1, 0.2, 0.3, 0.5, 0.75, 1d, 1.5, 2d, 3d, 5d, 7d);
        Aggregation.explicitBucketHistogram(buckets);

        InstrumentSelector allHistogramMetrics = InstrumentSelector.builder()
                .setType(InstrumentType.HISTOGRAM)
                .build();
        View finerBucketsView = View.builder()
                .setAggregation(Aggregation.explicitBucketHistogram(buckets)).build();

        // Registers a reader, exporter, and views.
        SdkMeterProviderBuilder builder = SdkMeterProvider.builder()
                .addResource(resource)
                .registerView(allHistogramMetrics, finerBucketsView)
                // Lab 2/3: log metrics instead of exporting to Collector
                .registerMetricReader(periodicMetricReader(otlJsonMetricExporter));

        // For demo purposes, we enable exemplars for all metrics. The default is trace_based.
        //SdkMeterProviderUtil.setExemplarFilter(builder, ExemplarFilter.alwaysOn());

        return builder.build();

    }

    public static MetricReader periodicMetricReader(MetricExporter metricExporter) {
        return PeriodicMetricReader.builder(metricExporter).setInterval(Duration.ofSeconds(5)).build();
    }

    public static MetricExporter otlpHttpMetricExporter(String endpoint) {
        return OtlpHttpMetricExporter.builder()
                .setEndpoint(endpoint)
                //.addHeader("api-key", "value")
                .setTimeout(Duration.ofSeconds(10))
                .build();
    }
}
