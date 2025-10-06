package com.gelerion.otel.playground.filters.before;

import com.gelerion.otel.playground.featureflags.FeatureFlag;
import spark.Filter;
import spark.Request;
import spark.Response;

public class FeatureFlagBeforeFilter implements Filter {
    @Override
    public void handle(Request request, Response response) {
        FeatureFlag.initializeFromRequest(request);
    }
}

