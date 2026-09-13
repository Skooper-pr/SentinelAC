package com.skooper.sentinelac.combat.model;

/**
 * Result of a deterministic server-authoritative combat raycast check.
 */
public final class CombatViolation {

    public enum ViolationType {
        NONE,
        IMPOSSIBLE_HIT_NO_INTERSECTION,
        REACH_EXCEEDED
    }

    private final ViolationType type;
    private final double measuredDistance;
    private final double maxAllowedReach;
    private final String summary;

    public CombatViolation(ViolationType type, double measuredDistance, double maxAllowedReach, String summary) {
        this.type = type;
        this.measuredDistance = measuredDistance;
        this.maxAllowedReach = maxAllowedReach;
        this.summary = summary;
    }

    public static CombatViolation pass(double distance, double maxReach) {
        return new CombatViolation(ViolationType.NONE, distance, maxReach, "Hit geometrically valid");
    }

    public static CombatViolation noIntersection(double maxReach) {
        return new CombatViolation(
                ViolationType.IMPOSSIBLE_HIT_NO_INTERSECTION,
                Double.POSITIVE_INFINITY,
                maxReach,
                "Attack ray does not intersect target AABB hitbox (impossible hit / angle)"
        );
    }

    public static CombatViolation reachExceeded(double distance, double maxReach) {
        return new CombatViolation(
                ViolationType.REACH_EXCEEDED,
                distance,
                maxReach,
                String.format("Attack reach %.4f exceeds maximum allowed reach %.4f", distance, maxReach)
        );
    }

    public boolean isViolation() {
        return type != ViolationType.NONE;
    }

    public ViolationType getType() {
        return type;
    }

    public double getMeasuredDistance() {
        return measuredDistance;
    }

    public double getMaxAllowedReach() {
        return maxAllowedReach;
    }

    public String getSummary() {
        return summary;
    }

    @Override
    public String toString() {
        return String.format("CombatViolation[type=%s, dist=%.3f, max=%.3f, summary=%s]",
                type, measuredDistance, maxAllowedReach, summary);
    }
}
