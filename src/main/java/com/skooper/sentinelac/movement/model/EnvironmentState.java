package com.skooper.sentinelac.movement.model;

/**
 * Environmental and block state context surrounding the player for a simulation tick.
 */
public final class EnvironmentState {

    private final MovementMedium medium;
    private final BlockFriction blockFriction;
    private final boolean onGround;
    private final boolean inCobweb;
    private final boolean gliding;
    private final int jumpBoostLevel;
    private final int speedEffectLevel;

    public EnvironmentState(MovementMedium medium, BlockFriction blockFriction, boolean onGround,
                            boolean inCobweb, boolean gliding, int jumpBoostLevel, int speedEffectLevel) {
        this.medium = medium;
        this.blockFriction = blockFriction;
        this.onGround = onGround;
        this.inCobweb = inCobweb;
        this.gliding = gliding;
        this.jumpBoostLevel = jumpBoostLevel;
        this.speedEffectLevel = speedEffectLevel;
    }

    public static EnvironmentState air(boolean onGround) {
        return new EnvironmentState(MovementMedium.AIR, BlockFriction.NORMAL, onGround, false, false, 0, 0);
    }

    public static EnvironmentState ground(BlockFriction friction) {
        return new EnvironmentState(MovementMedium.AIR, friction, true, false, false, 0, 0);
    }

    public static EnvironmentState water() {
        return new EnvironmentState(MovementMedium.WATER, BlockFriction.NORMAL, false, false, false, 0, 0);
    }

    public static EnvironmentState lava() {
        return new EnvironmentState(MovementMedium.LAVA, BlockFriction.NORMAL, false, false, false, 0, 0);
    }

    public static EnvironmentState cobweb() {
        return new EnvironmentState(MovementMedium.COBWEB, BlockFriction.NORMAL, false, true, false, 0, 0);
    }

    public static EnvironmentState elytra() {
        return new EnvironmentState(MovementMedium.ELYTRA, BlockFriction.NORMAL, false, false, true, 0, 0);
    }

    public MovementMedium getMedium() {
        return medium;
    }

    public BlockFriction getBlockFriction() {
        return blockFriction;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public boolean isInCobweb() {
        return inCobweb;
    }

    public boolean isGliding() {
        return gliding;
    }

    public int getJumpBoostLevel() {
        return jumpBoostLevel;
    }

    public int getSpeedEffectLevel() {
        return speedEffectLevel;
    }
}
