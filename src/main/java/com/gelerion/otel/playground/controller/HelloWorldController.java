package com.gelerion.otel.playground.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gelerion.otel.playground.clients.RemoteClient;
import com.gelerion.otel.playground.feature.flags.FeatureFlag;
import com.gelerion.otel.playground.metrics.MetricsProvider;
import com.gelerion.otel.playground.repository.DbOperations;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.CodeAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.gelerion.otel.playground.Server.JSON;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

public class HelloWorldController {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final DbOperations dbOperations;
    private final RemoteClient recommendationsClient;

    public HelloWorldController(MetricsProvider metricsProvider) {
        this.dbOperations = new DbOperations(metricsProvider);
        this.recommendationsClient = new RemoteClient(metricsProvider);
    }

    // When the code reaches this method, it already:
    //  1. Has a valid active span with a trace context created by OtelContextPropagationFilter (either created or derived from propagators).
    //  2. Has an SLF4J MDC context set by LoggingTraceContextSetterFilter.
    //  3. Has metrics initialized and tracked by HttpMetricsRecorder.
    // Therefore, we only need to track outgoing interactions and internal flows for better traceability.
    public String hello(Request request, Response response) throws JsonProcessingException {
        // Adds an attribute example.
        Span.current().setAttribute(CodeAttributes.CODE_FUNCTION_NAME, "HelloWorldController/hello");

        String name = request.params(":name");
        // Adds an event example.
        Span.current().addEvent("Request received", Attributes.of(stringKey("param"), name));

        logger.atInfo().addKeyValue("user.name", name).log("Request received for user {}", name);
        var message = "Hello, " + name + "!";

        sleepQuietly();

        // Simulates a DB operation.
        logger.atInfo().addKeyValue("user.name", name).log("Find user by name");
        String user = dbOperations.findUserByName(name);
        logger.atInfo().log("{}", user);

        // Simulates an outgoing HTTP request.
        logger.atInfo().addKeyValue("user.name", name).log("Request recommendations for user");
        String recommendations = recommendationsClient.callRecommendations(name);
        logger.atInfo().log("Recommendations for user {}", recommendations);

        response.status(200);
        Span.current().setStatus(StatusCode.OK);

        return JSON.writeValueAsString(Map.of(
                "message", message,
                "user", user,
                "recommendations", recommendations));
    }

    private void sleepQuietly() {
        FeatureFlag flag = FeatureFlag.current();
        try {
            int delay = ThreadLocalRandom.current().nextInt(
                    flag.controllerMinLatency(), 
                    flag.controllerMaxLatency()
            );
            Thread.sleep(delay);
        } catch (InterruptedException ignore) {}
    }
}