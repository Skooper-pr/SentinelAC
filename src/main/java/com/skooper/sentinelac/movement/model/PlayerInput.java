package com.skooper.sentinelac.movement.model;

/**
 * Encapsulates the player's intentional control inputs for a single tick.
 */
public final class PlayerInput {

    private final double forward;
    private final double strafe;
    private final boolean sprinting;
    private final boolean sneaking;
    private final boolean jumping;
    private final float yaw;
    private final float pitch;

    public PlayerInput(double forward, double strafe, boolean sprinting, boolean sneaking, boolean jumping, float yaw, float pitch) {
        this.forward = Math.max(-1.0, Math.min(1.0, forward));
        this.strafe = Math.max(-1.0, Math.min(1.0, strafe));
        this.sprinting = sprinting;
        this.sneaking = sneaking;
        this.jumping = jumping;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static PlayerInput idle(float yaw, float pitch) {
        return new PlayerInput(0.0, 0.0, false, false, false, yaw, pitch);
    }

    public static PlayerInput walking(double forward, double strafe, float yaw, float pitch) {
        return new PlayerInput(forward, strafe, false, false, false, yaw, pitch);
    }

    public static PlayerInput sprinting(double forward, double strafe, float yaw, float pitch) {
        return new PlayerInput(forward, strafe, true, false, false, yaw, pitch);
    }

    public static PlayerInput sprintJumping(double forward, double strafe, float yaw, float pitch) {
        return new PlayerInput(forward, strafe, true, false, true, yaw, pitch);
    }

    public static PlayerInput jumping(double forward, double strafe, float yaw, float pitch) {
        return new PlayerInput(forward, strafe, false, false, true, yaw, pitch);
    }

    public double getForward() {
        return forward;
    }

    public double getStrafe() {
        return strafe;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public boolean isJumping() {
        return jumping;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
