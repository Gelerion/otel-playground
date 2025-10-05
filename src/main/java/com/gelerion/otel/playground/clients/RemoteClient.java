package com.gelerion.otel.playground.clients;

import com.gelerion.otel.playground.filters.before.OtelContextPropagationBeforeFilter;
import com.gelerion.otel.playground.metrics.MetricsProvider;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("all")
public class RemoteClient {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // Injects trace headers into a HttpRequest.Builder.
    static final TextMapSetter<HttpRequest.Builder> SETTER = (carrier, key, value) ->
            carrier.header(key, value);

    private final MetricsProvider metricsProvider;

    public RemoteClient(MetricsProvider metricsProvider) {
        this.metricsProvider = metricsProvider;
    }

    public String callRecommendations(String userName) {
        long start = System.nanoTime();
        // Creates a CLIENT span, which is a child of the current server span.
        Span span = OtelContextPropagationBeforeFilter.tracer()
                .spanBuilder("HTTP POST /api/v1/recommend")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();

        // Sets low-cardinality, semconv-friendly attributes.
        span.setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "POST");
        span.setAttribute(ClientAttributes.CLIENT_ADDRESS, "recommendations.internal");
        span.setAttribute(ClientAttributes.CLIENT_PORT, 443);

        try (Scope __ = span.makeCurrent()) {
            URI uri = URI.create("https://recommendations.internal/api/v1/recommend?userName=" + userName);
            logger.atInfo().addKeyValue("uri", uri).log("Calling recommendations service");
            // Builds the request.
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .POST(HttpRequest.BodyPublishers.noBody());

            // Injects W3C trace headers (traceparent, tracestate) so the downstream service can link your span.
            GlobalOpenTelemetry.getPropagators()
                    .getTextMapPropagator()
                    .inject(Context.current(), builder, SETTER);

            HttpRequest request = builder.build();
            logger.atInfo().log("Injected headers: {}", request.headers());

            //HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            Response resp = send(request);

            // Sets outcome attributes.
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, resp.statusCode());

            if (resp.statusCode() >= 500) {
                span.setStatus(StatusCode.ERROR, "HTTP " + resp.statusCode());
            } else {
                span.setStatus(StatusCode.OK);
            }

            // Records metrics.
            double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
            String outcome = resp.statusCode() >= 500 ? "error" : "success";
            metricsProvider.clientRequestDurationHistogram().record(seconds, Attributes.builder()
                    .put("component", "http.client")
                    .put("outcome", outcome)
                    .put(ClientAttributes.CLIENT_ADDRESS, "recommendations")
                    .put(HttpAttributes.HTTP_REQUEST_METHOD, "POST")
                    .build());

            if (resp.statusCode() >= 500) {
                Span.current().addEvent("Recommendations service returned 500 error");
                return "Error response";
            }

            return "Learn deeper!";
        } catch (Exception ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, ex.getMessage() != null ? ex.getMessage() : "client error");

            double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
            metricsProvider.clientRequestDurationHistogram().record(seconds, Attributes.builder()
                    .put("component", "http.client")
                    .put(ClientAttributes.CLIENT_ADDRESS, "recommendations")
                    .put(HttpAttributes.HTTP_REQUEST_METHOD, "POST")
                    .put("outcome", "error")
                    .build());

            throw ex;
        } finally {
            span.end();
        }
    }

    private Response send(HttpRequest request) {
        sleepQuietly();

        int errorThreshold = Integer.parseInt(System.getenv().getOrDefault("CLIENT_ERROR_THRESHOLD", "11"));
        if (ThreadLocalRandom.current().nextInt(0, 15) > errorThreshold) {
            return new Response(500); // Recommendations not found
        }

        return new Response(200);
    }

    private record Response(int statusCode) {}

    private void sleepQuietly() {
        try {
            int minMs = Integer.parseInt(System.getenv().getOrDefault("CLIENT_LATENCY_MIN_MS", "200"));
            int maxMs = Integer.parseInt(System.getenv().getOrDefault("CLIENT_LATENCY_MAX_MS", "1300"));
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs));
        } catch (InterruptedException ignore) {}
    }
}
