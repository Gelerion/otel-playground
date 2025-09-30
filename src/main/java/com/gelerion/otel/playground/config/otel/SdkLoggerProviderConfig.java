package com.gelerion.otel.playground.config.otel;

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;

// This one is a bit different from others
public class SdkLoggerProviderConfig {

    public static SdkLoggerProvider create(Resource resource) {
        return SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(
                        BatchLogRecordProcessor.builder(
                                        OtlpHttpLogRecordExporter.builder()
                                                .setEndpoint(SdkOtelConfig.HTTP_COLLECTOR_URL + "/v1/logs")
                                                .build())
                                .build())
                .build();
    }
}
