package com.gelerion.otel.playground.config.otel;

import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.metrics.*;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.metrics.internal.SdkMeterProviderUtil;
import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarFilter;
import io.opentelemetry.sdk.resources.Resource;

import java.time.Duration;
import java.util.List;

import static com.gelerion.otel.playground.config.otel.SdkOtelConfig.HTTP_COLLECTOR_URL;

/*
SdkMeterProvider is the SDK implementation of MeterProvider and is responsible for handling metric telemetry produced by the API.
opentelemetry-sdk-metrics

SdkMeterProvider is configured by the application owner and consists of:
    - Resource: The resource with which metrics are associated.
    - MetricReader: Reads the aggregated state of metrics.
        Optionally, with CardinalityLimitSelector for overriding the cardinality limit by instrument kind.
        If unset, each instrument is limited to 2000 unique combinations of attributes per collection cycle.
        Cardinality limits are also configurable for individual instruments via views. See cardinality limits for more details.
    - MetricExporter: Exports metrics out of process (in conjunction with an associated MetricReader).
    - Views: Configures metric streams, including dropping unused metrics.
 */
public class SdkMeterProviderConfig {

    public static SdkMeterProvider create(Resource resource) {
        // A logging exporter
        MetricExporter otlJsonMetricExporter = OtlpJsonLoggingMetricExporter.create();

        // A MetricReader is a plugin extension interface responsible for reading aggregated metrics.
        // They are often paired with MetricExporters to export metrics out of process, but may also be used to serve
        // metrics to external scrapers in pull-based protocols.

        // Core SDK readers
        //  - PeriodicMetricReader (push): On a fixed interval, it reads metrics from the SDK and hands them to a configured MetricExporter.
        //  - PrometheusHttpServer (pull): Starts an HTTP server that exposes /metrics in Prometheus text format.

        // Views
        // There is a discussion on how to measure durations: in seconds or milliseconds.
        // OTEL & Prometheus suggest seconds, but not all tools are aligned.
        // Moreover, the default buckets for an explicit bucket histogram are aligned to milliseconds for http.server.duration,
        // which renders the default buckets useless.
        // See more at https://github.com/open-telemetry/opentelemetry-specification/issues/2977
        List<Double> buckets = List.of(0.1, 0.2, 0.3, 0.5, 0.75, 1d, 1.5, 2d, 3d, 5d, 7d);
        Aggregation.explicitBucketHistogram(buckets);

        InstrumentSelector allHistogramMetrics = InstrumentSelector.builder()
                .setType(InstrumentType.HISTOGRAM)
                .build();
        View finerBucketsView = View.builder()
                .setAggregation(Aggregation.explicitBucketHistogram(buckets)).build();

        // Registers a reader, exporter, and views.
        SdkMeterProviderBuilder builder = SdkMeterProvider.builder()
                .setResource(resource)
                .registerView(allHistogramMetrics, finerBucketsView)
                // Prints to the log (a bit verbose).
                //.registerMetricReader(periodicMetricReader(otlJsonMetricExporter))
                // Here is where we send trace data to the collector (make sure Docker Compose and Prometheus are running).
                .registerMetricReader(periodicMetricReader(
                        otlpHttpMetricExporter(HTTP_COLLECTOR_URL + "/v1/metrics"))
                );

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
