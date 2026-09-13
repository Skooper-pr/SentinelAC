package com.skooper.sentinelac.movement.check;

import com.skooper.sentinelac.movement.model.Vector3D;

/**
 * Result of a movement verification against simulated ground truth physics.
 */
public final class MovementViolation {

    private final boolean violation;
    private final double delta;
    private final Vector3D expectedPosition;
    private final Vector3D actualPosition;
    private final String mediumDescription;
    private final boolean gracePeriodActive;

    public MovementViolation(boolean violation, double delta, Vector3D expectedPosition,
                             Vector3D actualPosition, String mediumDescription, boolean gracePeriodActive) {
        this.violation = violation;
        this.delta = delta;
        this.expectedPosition = expectedPosition;
        this.actualPosition = actualPosition;
        this.mediumDescription = mediumDescription;
        this.gracePeriodActive = gracePeriodActive;
    }

    public static MovementViolation pass(double delta, Vector3D expected, Vector3D actual, String medium) {
        return new MovementViolation(false, delta, expected, actual, medium, false);
    }

    public static MovementViolation grace(double delta, Vector3D expected, Vector3D actual, String medium) {
        return new MovementViolation(false, delta, expected, actual, medium, true);
    }

    public static MovementViolation fail(double delta, Vector3D expected, Vector3D actual, String medium) {
        return new MovementViolation(true, delta, expected, actual, medium, false);
    }

    public boolean isViolation() {
        return violation;
    }

    public double getDelta() {
        return delta;
    }

    public Vector3D getExpectedPosition() {
        return expectedPosition;
    }

    public Vector3D getActualPosition() {
        return actualPosition;
    }

    public String getMediumDescription() {
        return mediumDescription;
    }

    public boolean isGracePeriodActive() {
        return gracePeriodActive;
    }

    public String getSummary() {
        if (!violation) {
            return gracePeriodActive ? "Movement in grace window (delta=" + String.format("%.5f", delta) + ")" : "Movement valid";
        }
        return String.format("Movement delta=%.5f exceeds epsilon in %s (expected=%s, actual=%s)",
                delta, mediumDescription, expectedPosition, actualPosition);
    }
}
