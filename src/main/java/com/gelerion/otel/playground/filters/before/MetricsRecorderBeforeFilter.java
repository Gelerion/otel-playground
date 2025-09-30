package com.gelerion.otel.playground.filters.before;

import com.gelerion.otel.playground.metrics.MetricsProvider;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.semconv.HttpAttributes;
import spark.Filter;
import spark.Request;
import spark.Response;

public class MetricsRecorderBeforeFilter implements Filter {
    private final MetricsProvider metricsProvider;

    public MetricsRecorderBeforeFilter(MetricsProvider metricsProvider) {
        this.metricsProvider = metricsProvider;
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        request.attribute("__startNanos", System.nanoTime()); //to measure elapsed time

        Attributes attributes = Attributes.builder()
                .put(HttpAttributes.HTTP_REQUEST_METHOD, request.requestMethod())
                .put(HttpAttributes.HTTP_ROUTE, request.pathInfo()) //never label by raw dynamic values
                .build();

        metricsProvider.activeRequestsCounter().add(1, attributes);
    }
}
