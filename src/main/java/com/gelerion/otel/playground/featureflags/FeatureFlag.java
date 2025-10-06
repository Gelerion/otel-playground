package com.gelerion.otel.playground.featureflags;

import spark.Request;

public class FeatureFlag {
    private static final ThreadLocal<FeatureFlag> CURRENT = new ThreadLocal<>();
    
    private final boolean highLatency;

    private FeatureFlag(boolean highLatency) {
        this.highLatency = highLatency;
    }

    public static void initializeFromRequest(Request request) {
        String header = request.headers("X-Feature-Flag");
        boolean isHighLatency = "high-latency".equalsIgnoreCase(header);
        CURRENT.set(new FeatureFlag(isHighLatency));
    }

    public static FeatureFlag current() {
        FeatureFlag flag = CURRENT.get();
        return flag != null ? flag : baseline();
    }

    public static void clear() {
        CURRENT.remove();
    }

    private static FeatureFlag baseline() {
        return new FeatureFlag(false);
    }

    // Controller latency
    public int controllerMinLatency() {
        return highLatency ? 300 : 50;
    }

    public int controllerMaxLatency() {
        return highLatency ? 1000 : 150;
    }

    // DB config
    public int dbMinLatency() {
        return highLatency ? 1000 : 200;
    }

    public int dbMaxLatency() {
        return highLatency ? 3000 : 800;
    }

    public double dbErrorRate() {
        return 0.10; // 10% for both modes
    }

    // Client config
    public int clientMinLatency() {
        return highLatency ? 1000 : 200;
    }

    public int clientMaxLatency() {
        return highLatency ? 2000 : 800;
    }

    public double clientErrorRate() {
        return 0.10; // 10% for both modes
    }
}
