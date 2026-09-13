package com.skooper.sentinelac.storage;

import java.util.UUID;

/**
 * Persistent record of a player detection flag.
 */
public final class FlagRecord {

    private final int id;
    private final UUID playerUuid;
    private final String playerName;
    private final String category;
    private final long timestamp;
    private final double lambdaValue;
    private final String evidenceSummary;

    public FlagRecord(int id, UUID playerUuid, String playerName, String category, long timestamp, double lambdaValue, String evidenceSummary) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.category = category;
        this.timestamp = timestamp;
        this.lambdaValue = lambdaValue;
        this.evidenceSummary = evidenceSummary;
    }

    public int getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getCategory() {
        return category;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getLambdaValue() {
        return lambdaValue;
    }

    public String getEvidenceSummary() {
        return evidenceSummary;
    }
}
