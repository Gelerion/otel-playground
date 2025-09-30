package com.gelerion.otel.playground.filters.after;

import com.gelerion.otel.playground.metrics.MetricsProvider;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.HttpAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.ExceptionHandler;
import spark.Filter;
import spark.Request;
import spark.Response;

import java.lang.invoke.MethodHandles;

public class MetricsRecorderAfterFilter implements Filter, ExceptionHandler<Exception> {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final MetricsProvider metricsProvider;

    public MetricsRecorderAfterFilter(MetricsProvider metricsProvider) {
        this.metricsProvider = metricsProvider;
    }

    // A happy path.
    @Override
    public void handle(Request request, Response response) throws Exception {
        recordMetrics(request, response);
    }

    // An exceptional path.
    @Override
    public void handle(Exception exception, Request request, Response response) {
        logger.error("Exception", exception);
        recordMetrics(request, response);

        // A bit dirty, but also marks the span as an error.
        Span span = Span.current();
        span.recordException(exception);
        span.setStatus(StatusCode.ERROR);
    }

    private void recordMetrics(Request request, Response response) {
        double seconds = durationsSeconds(request.attribute("__startNanos"));
        Attributes attributes = attributes(request, response);

        metricsProvider.serverRequestDurationHistogram().record(seconds, attributes);
        metricsProvider.totalRequestsCounter().add(1, attributes);
        metricsProvider.activeRequestsCounter().add(-1, attributes);
    }

    private double durationsSeconds(Long startNanos) {
        long durNanos = (startNanos == null) ? 0 : (System.nanoTime() - startNanos);
        double seconds = durNanos / 1_000_000_000.0;
        return seconds;
    }

    private Attributes attributes(Request request, Response response) {
        return Attributes.builder()
                .put(HttpAttributes.HTTP_REQUEST_METHOD, request.requestMethod())
                .put(HttpAttributes.HTTP_ROUTE, request.pathInfo()) // Never label by raw dynamic values.
                .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, response.status())
                .build();
    }
}
