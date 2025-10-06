package com.gelerion.otel.playground;

import static spark.Spark.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelerion.otel.playground.controller.HelloWorldController;
import com.gelerion.otel.playground.feature.flags.FeatureFlag;
import com.gelerion.otel.playground.filters.before.FeatureFlagBeforeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;

import java.lang.invoke.MethodHandles;

public class Server {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    public static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) {
        // Lab 1: No OpenTelemetry SDK initialization yet
        log.atInfo().log("Starting server on port 8080");
        start();
    }

    public static void start() {
        port(8080);

        // Lab 1: Only feature flags for now
        before(new FeatureFlagBeforeFilter());

        var helloWorldController = new HelloWorldController();
        get("/v1/hello/:name", helloWorldController::hello);

        afterAfter(cleanupContext());
    }

    private static Filter cleanupContext() {
        return (req, resp) -> {
            resp.type("application/json;charset=utf-8");
            FeatureFlag.clear();
        };
    }
}
