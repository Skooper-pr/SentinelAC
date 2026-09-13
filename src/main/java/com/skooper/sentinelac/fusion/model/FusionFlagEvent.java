package com.skooper.sentinelac.fusion.model;

import java.util.UUID;

/**
 * Event triggered when a player's accumulated evidence crosses the upper Wald boundary
 * or a deterministic proof shortcuts straight to a flag verdict.
 */
public final class FusionFlagEvent {

    private final UUID playerId;
    private final CheatCategory category;
    private final double lambdaAtFlag;
    private final double threshold;
    private final boolean deterministic;
    private final String summary;
    private final long timestamp;

    public FusionFlagEvent(UUID playerId, CheatCategory category, double lambdaAtFlag, double threshold,
                           boolean deterministic, String summary, long timestamp) {
        this.playerId = playerId;
        this.category = category;
        this.lambdaAtFlag = lambdaAtFlag;
        this.threshold = threshold;
        this.deterministic = deterministic;
        this.summary = summary;
        this.timestamp = timestamp;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public CheatCategory getCategory() {
        return category;
    }

    public double getLambdaAtFlag() {
        return lambdaAtFlag;
    }

    public double getThreshold() {
        return threshold;
    }

    public boolean isDeterministic() {
        return deterministic;
    }

    public String getSummary() {
        return summary;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("FusionFlagEvent[player=%s, category=%s, lambda=%.3f, threshold=%.3f, deterministic=%s, summary=%s]",
                playerId, category, lambdaAtFlag, threshold, deterministic, summary);
    }
}
