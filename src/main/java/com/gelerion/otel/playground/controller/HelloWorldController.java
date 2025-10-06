package com.gelerion.otel.playground.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gelerion.otel.playground.feature.flags.FeatureFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.gelerion.otel.playground.Server.JSON;

public class HelloWorldController {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public HelloWorldController() {
    }

    // Lab 1: Minimal baseline with structured logging only.
    // No OpenTelemetry SDK, no RemoteClient, no DbOperations yet.
    public String hello(Request request, Response response) throws JsonProcessingException {
        String name = request.params(":name");

        logger.atInfo().addKeyValue("user.name", name).log("Request received for user {}", name);
        var message = "Hello, " + name + "!";

        sleepQuietly();

        logger.atInfo().addKeyValue("user.name", name).log("Processing request for user {}", name);

        response.status(200);

        return JSON.writeValueAsString(Map.of("message", message));
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