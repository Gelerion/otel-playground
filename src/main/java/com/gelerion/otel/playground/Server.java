package com.gelerion.otel.playground;

import static spark.Spark.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelerion.otel.playground.config.otel.SdkOtelConfig;
import com.gelerion.otel.playground.controller.HelloWorldController;
import com.gelerion.otel.playground.featureflags.FeatureFlag;
import com.gelerion.otel.playground.filters.after.MetricsRecorderAfterFilter;
import com.gelerion.otel.playground.filters.before.FeatureFlagBeforeFilter;
import com.gelerion.otel.playground.filters.before.LoggingTraceContextSetterBeforeFilter;
import com.gelerion.otel.playground.filters.before.MetricsRecorderBeforeFilter;
import com.gelerion.otel.playground.filters.before.OtelContextPropagationBeforeFilter;
import com.gelerion.otel.playground.metrics.MetricsProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

public class Server {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    public static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) {
        // Manually init OTEL
        SdkOtelConfig.init();

        log.atInfo().log("Starting server on port 8080");
        start();
    }

    public static void start() {
        port(8080);

        MetricsProvider metricsProvider = new MetricsProvider();

        // setup Span, MDC context, and feature flags
        before(new FeatureFlagBeforeFilter(),
               new OtelContextPropagationBeforeFilter(),
               new LoggingTraceContextSetterBeforeFilter(),
               new MetricsRecorderBeforeFilter(metricsProvider));

        var helloWorldController = new HelloWorldController(metricsProvider);
        get("/v1/hello/:name", helloWorldController::hello);

        // record metrics
        after(new MetricsRecorderAfterFilter(metricsProvider));
        exception(Exception.class, new MetricsRecorderAfterFilter(metricsProvider));

        afterAfter(cleanupContext());
    }

    private static Filter cleanupContext() {
        return (req, resp) -> {
            resp.type("application/json;charset=utf-8");

            Optional.ofNullable(req.<Scope>attribute(OtelContextPropagationBeforeFilter.OTEL_SCOPE_ATTR))
                    .ifPresent(Scope::close);
            Optional.ofNullable(req.<Span>attribute(OtelContextPropagationBeforeFilter.OTEL_SERVER_SPAN_ATTR))
                    .ifPresent(Span::end);
            ThreadContext.clearAll();
            FeatureFlag.clear();
        };
    }
}
