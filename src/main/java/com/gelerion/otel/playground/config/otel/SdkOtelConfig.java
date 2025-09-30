package com.gelerion.otel.playground.config.otel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.ServiceAttributes;

public class SdkOtelConfig {

    public static final String HTTP_COLLECTOR_URL = "http://localhost:4318";

    public static void init() {
        // Propagates context with the baggage header.
        ContextPropagators propagators = contextPropagators();

        // A resource describes the service identity.
        Resource resource = resourceIdentity();

        // All things tracing.
        SdkTracerProvider sdkTracerProvider = SdkTracerProviderConfig.create(resource);

        // All things metrics.
        SdkMeterProvider sdkMeterProvider = SdkMeterProviderConfig.create(resource);

        // All things logging.
        SdkLoggerProvider sdkLoggerProvider = SdkLoggerProviderConfig.create(resource);

        OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setMeterProvider(sdkMeterProvider)
                .setLoggerProvider(sdkLoggerProvider)
                .setPropagators(propagators)
                .build();

        GlobalOpenTelemetry.set(openTelemetrySdk);

        // This is only required for manual instrumentation.
        OpenTelemetryAppender.install(GlobalOpenTelemetry.get());

        Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));
    }

    private static Resource resourceIdentity() {
        return Resource.getDefault().merge(
                Resource.create(Attributes.builder()
                        .put(ServiceAttributes.SERVICE_NAME, "otel-playground")
                        .put(ServiceAttributes.SERVICE_VERSION, "1.0.0")
                        .put(ServerAttributes.SERVER_ADDRESS, "localhost")
                        .put(ServerAttributes.SERVER_PORT, 8080)
                        // The deployment environment, etc.
                        .build())
        );
    }

    private static ContextPropagators contextPropagators() {
        return ContextPropagators.create(
                TextMapPropagator.composite(
                        W3CTraceContextPropagator.getInstance(),  // <-- traceparent/tracestate
                        W3CBaggagePropagator.getInstance()        // <-- baggage
                )
        );
    }

}
