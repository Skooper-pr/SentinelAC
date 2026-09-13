package com.skooper.sentinelac.combat.model;

/**
 * Historical snapshot of an entity's hitbox at a specific server tick for lag compensation.
 */
public final class HistoricalEntityState {

    private final long tick;
    private final long timestamp;
    private final BoundingBox3D boundingBox;

    public HistoricalEntityState(long tick, long timestamp, BoundingBox3D boundingBox) {
        this.tick = tick;
        this.timestamp = timestamp;
        this.boundingBox = boundingBox;
    }

    public long getTick() {
        return tick;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public BoundingBox3D getBoundingBox() {
        return boundingBox;
    }
}
