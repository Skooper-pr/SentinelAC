package com.skooper.sentinelac.fusion.model;

/**
 * Record of an individual piece of evidence contributing to the player's SPRT score.
 */
public final class EvidenceRecord {

    private final long timestamp;
    private final CheatCategory category;
    private final double deltaLlr;
    private final boolean deterministic;
    private final String summary;

    public EvidenceRecord(long timestamp, CheatCategory category, double deltaLlr, boolean deterministic, String summary) {
        this.timestamp = timestamp;
        this.category = category;
        this.deltaLlr = deltaLlr;
        this.deterministic = deterministic;
        this.summary = summary;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public CheatCategory getCategory() {
        return category;
    }

    public double getDeltaLlr() {
        return deltaLlr;
    }

    public boolean isDeterministic() {
        return deterministic;
    }

    public String getSummary() {
        return summary;
    }
}
