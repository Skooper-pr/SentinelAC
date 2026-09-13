package com.skooper.sentinelac.behavior.model;

/**
 * Record of player rotational aim state and target tracking angle per tick.
 */
public final class AimSample {

    private final long timestampMs;
    private final float yawDelta;
    private final float pitchDelta;
    private final double targetAngleError;
    private final boolean targetVisible;

    public AimSample(long timestampMs, float yawDelta, float pitchDelta, double targetAngleError, boolean targetVisible) {
        this.timestampMs = timestampMs;
        this.yawDelta = yawDelta;
        this.pitchDelta = pitchDelta;
        this.targetAngleError = targetAngleError;
        this.targetVisible = targetVisible;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public float getYawDelta() {
        return yawDelta;
    }

    public float getPitchDelta() {
        return pitchDelta;
    }

    public double getTargetAngleError() {
        return targetAngleError;
    }

    public boolean isTargetVisible() {
        return targetVisible;
    }
}
