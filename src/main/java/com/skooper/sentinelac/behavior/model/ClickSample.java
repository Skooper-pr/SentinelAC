package com.skooper.sentinelac.behavior.model;

/**
 * Record of a player click event with timestamp.
 */
public final class ClickSample {

    private final long timestampMs;
    private final double intervalMs;

    public ClickSample(long timestampMs, double intervalMs) {
        this.timestampMs = timestampMs;
        this.intervalMs = intervalMs;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public double getIntervalMs() {
        return intervalMs;
    }
}
