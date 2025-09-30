package com.gelerion.otel.playground.filters.before;

import com.gelerion.otel.playground.utils.RequestCtxParams;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.apache.logging.log4j.ThreadContext;
import spark.Filter;
import spark.Request;
import spark.Response;

public class LoggingTraceContextSetterBeforeFilter implements Filter {

    @Override
    public void handle(Request request, Response response) {
        Context context = Context.current();

        var sc = Span.fromContext(context).getSpanContext();
        if (sc.isValid()) {
            ThreadContext.put(RequestCtxParams.TRACE_ID, sc.getTraceId());
            ThreadContext.put(RequestCtxParams.SPAN_ID,  sc.getSpanId());
            ThreadContext.put("trace_flags", sc.getTraceFlags().asHex());
        }

        // custom headers
        var baggage = Baggage.fromContext(context);
        var requestId = baggage.getEntryValue(RequestCtxParams.REQUEST_ID);
        ThreadContext.put(RequestCtxParams.REQUEST_ID, requestId);
    }
}
