package com.skooper.sentinelac.combat.model;

import com.skooper.sentinelac.movement.model.Vector3D;

/**
 * 3D directional ray originating from attacker eye position.
 */
public final class Ray3D {

    private final Vector3D origin;
    private final Vector3D direction;

    public Ray3D(Vector3D origin, Vector3D direction) {
        this.origin = origin;
        this.direction = direction.normalize();
    }

    public static Ray3D fromEyeAndRotations(Vector3D eye, float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double cosPitch = Math.cos(pitchRad);
        double dirX = -Math.sin(yawRad) * cosPitch;
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * cosPitch;

        return new Ray3D(eye, new Vector3D(dirX, dirY, dirZ));
    }

    public Vector3D getOrigin() {
        return origin;
    }

    public Vector3D getDirection() {
        return direction;
    }

    public Vector3D getPointAt(double distance) {
        return origin.add(direction.multiply(distance));
    }
}
