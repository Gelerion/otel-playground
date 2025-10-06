package com.gelerion.otel.playground.repository;

import com.gelerion.otel.playground.feature.flags.FeatureFlag;
import com.gelerion.otel.playground.filters.before.OtelContextPropagationBeforeFilter;
import com.gelerion.otel.playground.metrics.MetricsProvider;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.CodeAttributes;
import io.opentelemetry.semconv.DbAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ThreadLocalRandom;

// Most of this is done automatically by the agent, but we are doing it manually for the demo.
public class DbOperations {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final MetricsProvider metricsProvider;

    public DbOperations(MetricsProvider metricsProvider) {
        this.metricsProvider = metricsProvider;
    }

    // Best practices for such operations:
    //  - Create a child span around the DB operation and attach DB semantic attributes.
    //  - Record the duration (optional: your histogram) and mark errors on exceptions.
    public String findUserByName(String name) {
        long startTime = System.nanoTime();
        // Creates a new span. See the README for span naming best practices.
        Span span = OtelContextPropagationBeforeFilter.tracer().spanBuilder("DB SELECT users")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan(); // Creates and starts a span now, with a parent chosen from either setParent(...) or Context.current().

        // Low-cardinality attributes
        Attributes commonAttributes = Attributes.builder()
                .put(DbAttributes.DB_SYSTEM_NAME, "postgresql")
                .put(DbAttributes.DB_NAMESPACE, "appdb")         // The database/schema
                .put(DbAttributes.DB_OPERATION_NAME, "SELECT")   // SELECT/INSERT/UPDATE/DELETE
                .put(DbAttributes.DB_COLLECTION_NAME, "users")  // If known (a bounded value)
                .build();

        span.setAllAttributes(commonAttributes);
        // Span-specific attributes
        span.setAttribute(CodeAttributes.CODE_FUNCTION_NAME, "DbOperations/findUserByName");
        span.setAttribute(DbAttributes.DB_QUERY_TEXT, "SELECT * FROM users WHERE name = ?"); // A sanitized query
        span.setAttribute(NetworkAttributes.NETWORK_PEER_ADDRESS, "db01.internal");
        span.setAttribute(NetworkAttributes.NETWORK_PEER_PORT, 9999);

        // Metric attributes
        AttributesBuilder metricAttrs = Attributes.builder().putAll(commonAttributes);

        // Remember: The scope controls what the "current" context is on this thread. Closing it restores the previous context.
        // It does not end the span.
        try (Scope __ = span.makeCurrent()) {
            // Inside the try block, Span.current() is your DB span; logs and any child spans will naturally attach to it.
            logger.atInfo().addKeyValue("user.name", name).log("Fetch user details from DB");

            // Simulates a DB call or an exception.
            randomWaitOrThrow();
            // Optional: records the row count as an attribute (a small integer).
            //span.setAttribute("db.rows_affected", 1);

            // Records metrics.
            double seconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
            metricsProvider.dbRequestDurationHistogram().record(seconds, metricAttrs
                    .put("component", "db")
                    .put("outcome", "success").build());

            return "Found user: " + name;
        } catch (Exception e) {
            logger.atError().setCause(e).addKeyValue("user.name", name).log("Failed to fetch user details from DB");
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);

            // Records metrics while the span is current to capture exemplars.
            double seconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
            metricsProvider.dbRequestDurationHistogram().record(seconds, metricAttrs
                    .put("component", "db")
                    .put("outcome", "error")
                    .put("exceptionType", "RuntimeException")
                    .build());

            if (e instanceof RuntimeException ex) throw ex;
            else throw new RuntimeException(e);
        } finally {
            // Records metrics.
            double seconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
            metricsProvider.dbRequestDurationHistogram().record(seconds, metricAttrs.put("component", "db").build());

            // IMPORTANT: Ends the span so it can be processed and exported.
            span.end();
        }
    }

    private void randomWaitOrThrow() {
        FeatureFlag flag = FeatureFlag.current();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        int waitMillis = rnd.nextInt(flag.dbMinLatency(), flag.dbMaxLatency());
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (rnd.nextDouble() < flag.dbErrorRate()) {
            throw new RuntimeException("Database not reachable");
        }
    }
}
