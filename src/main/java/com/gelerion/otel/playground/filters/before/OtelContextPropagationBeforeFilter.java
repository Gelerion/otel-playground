package com.gelerion.otel.playground.filters.before;

import com.gelerion.otel.playground.utils.RequestCtxParams;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.SchemaUrls;
import spark.Filter;
import spark.Request;
import spark.Response;

import java.util.UUID;

// In order for this to work, we must ensure that OTEL is initialized and the propagators are configured.
public class OtelContextPropagationBeforeFilter implements Filter {
    public static final String OTEL_SCOPE_ATTR = "otel.scope";
    public static final String OTEL_SERVER_SPAN_ATTR = "otel.server.span";

    private static final TextMapGetter<Request> REQUEST_HEADERS_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Request carrier) {
            return carrier.headers();
        }

        @Override
        public String get(Request carrier, String key) {
            return carrier.headers(key);
        }
    };

    @Override
    public void handle(Request request, Response response) {
        var propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

        // 1) Extracts the upstream trace context and baggage from the HTTP headers.
        Context extracted = propagator.extract(Context.current(), request, REQUEST_HEADERS_GETTER);

        // 2) Optionally, if we need to update existing values or include new ones.
        Baggage baggage = Baggage.fromContext(extracted).toBuilder()
                .put(RequestCtxParams.REQUEST_ID, UUID.randomUUID().toString())
                .build();

        // Continues the upstream trace if it is present; otherwise, it starts a new root.
        var serverSpan = tracer().spanBuilder(request.requestMethod() + " " + request.pathInfo())
                .setSpanKind(SpanKind.SERVER)
                .setParent(extracted)
                // Semantic conventions
                .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.requestMethod())
                .setAttribute(HttpAttributes.HTTP_ROUTE, request.pathInfo())
                .startSpan();

        // Activates the context on the current thread, making the context (with its current span and baggage) visible to downstream code.
        Scope scope = Context.current()
                .with(serverSpan)
                .with(baggage) // Makes the baggage current for the rest of the request handling.
                .makeCurrent(); // Activation

        // Stores the scope so we can close it after the route finishes (see afterAfter).
        request.attribute(OTEL_SCOPE_ATTR, scope);
        request.attribute(OTEL_SERVER_SPAN_ATTR, serverSpan);
    }

    public static Tracer tracer() {
        return GlobalOpenTelemetry
                .tracerBuilder("com.gelerion.otel.playground.http")
                .setInstrumentationVersion("1.0.0")
                .setSchemaUrl(SchemaUrls.V1_37_0)
                .build();
    }
}
