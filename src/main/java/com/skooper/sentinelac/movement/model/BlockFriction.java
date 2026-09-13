package com.skooper.sentinelac.movement.model;

/**
 * Surface friction and special interaction properties for blocks underneath the player.
 */
public enum BlockFriction {
    NORMAL(0.60, 1.0, false, false),
    ICE(0.98, 1.0, false, false),
    PACKED_ICE(0.98, 1.0, false, false),
    BLUE_ICE(0.989, 1.0, false, false),
    SLIME(0.80, 1.0, true, false),
    HONEY(0.60, 0.4, false, true),
    SOUL_SAND(0.60, 0.4, false, false);

    private final double slipperiness;
    private final double speedModifier;
    private final boolean bouncy;
    private final boolean honeyBehavior;

    BlockFriction(double slipperiness, double speedModifier, boolean bouncy, boolean honeyBehavior) {
        this.slipperiness = slipperiness;
        this.speedModifier = speedModifier;
        this.bouncy = bouncy;
        this.honeyBehavior = honeyBehavior;
    }

    public double getSlipperiness() {
        return slipperiness;
    }

    public double getSpeedModifier() {
        return speedModifier;
    }

    public boolean isBouncy() {
        return bouncy;
    }

    public boolean isHoneyBehavior() {
        return honeyBehavior;
    }
}
