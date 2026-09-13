package com.skooper.sentinelac.movement.model;

/**
 * Snapshot of the player's physical kinematic state.
 */
public final class PlayerPhysicsState {

    private final Vector3D position;
    private final Vector3D velocity;
    private final float yaw;
    private final float pitch;
    private final boolean onGround;
    private final long tick;
    private final float fallDistance;

    public PlayerPhysicsState(Vector3D position, Vector3D velocity, float yaw, float pitch,
                              boolean onGround, long tick, float fallDistance) {
        this.position = position;
        this.velocity = velocity;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
        this.tick = tick;
        this.fallDistance = fallDistance;
    }

    public static PlayerPhysicsState initial(Vector3D position, float yaw, float pitch, boolean onGround) {
        return new PlayerPhysicsState(position, Vector3D.ZERO, yaw, pitch, onGround, 0L, 0.0f);
    }

    public Vector3D getPosition() {
        return position;
    }

    public Vector3D getVelocity() {
        return velocity;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public long getTick() {
        return tick;
    }

    public float getFallDistance() {
        return fallDistance;
    }

    public PlayerPhysicsState withPosition(Vector3D newPosition) {
        return new PlayerPhysicsState(newPosition, velocity, yaw, pitch, onGround, tick + 1, fallDistance);
    }

    public PlayerPhysicsState withVelocity(Vector3D newVelocity) {
        return new PlayerPhysicsState(position, newVelocity, yaw, pitch, onGround, tick, fallDistance);
    }
}
