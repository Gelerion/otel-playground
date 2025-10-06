package com.gelerion.otel.playground.config.otel;

import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingLogRecordExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;

public class SdkLoggerProviderConfig {

    public static SdkLoggerProvider create(Resource resource) {
        return SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(
                        // Lab 2 default: log records to console; Lab 3: switch to OTLP
                        BatchLogRecordProcessor.builder(
                                        OtlpJsonLoggingLogRecordExporter.create())
                                .build())
                .build();
    }
}
