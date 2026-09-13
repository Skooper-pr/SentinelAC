package com.skooper.sentinelac.combat.engine;

import com.skooper.sentinelac.combat.model.BoundingBox3D;
import com.skooper.sentinelac.combat.model.CombatViolation;
import com.skooper.sentinelac.combat.model.Ray3D;
import com.skooper.sentinelac.movement.model.Vector3D;

import java.util.OptionalDouble;

/**
 * Server-authoritative geometric combat validation engine.
 * Performs deterministic 3D ray-AABB intersection tests and enforces mode-specific reach limits.
 */
public final class CombatEngine {

    public static final double DEFAULT_SURVIVAL_REACH = 3.0;
    public static final double DEFAULT_CREATIVE_REACH = 5.0;
    public static final double DEFAULT_HITBOX_EXPANSION = 0.10;
    public static final double DEFAULT_REACH_TOLERANCE = 0.05;

    private volatile double survivalMaxReach = DEFAULT_SURVIVAL_REACH;
    private volatile double creativeMaxReach = DEFAULT_CREATIVE_REACH;
    private volatile double hitboxExpansion = DEFAULT_HITBOX_EXPANSION;
    private volatile double reachTolerance = DEFAULT_REACH_TOLERANCE;

    private final LagCompensator lagCompensator;

    public CombatEngine() {
        this.lagCompensator = new LagCompensator();
    }

    public CombatEngine(LagCompensator lagCompensator) {
        this.lagCompensator = lagCompensator;
    }

    public LagCompensator getLagCompensator() {
        return lagCompensator;
    }

    public void setSurvivalMaxReach(double survivalMaxReach) {
        this.survivalMaxReach = Math.max(1.0, survivalMaxReach);
    }

    public double getSurvivalMaxReach() {
        return survivalMaxReach;
    }

    public void setCreativeMaxReach(double creativeMaxReach) {
        this.creativeMaxReach = Math.max(1.0, creativeMaxReach);
    }

    public double getCreativeMaxReach() {
        return creativeMaxReach;
    }

    public void setHitboxExpansion(double hitboxExpansion) {
        this.hitboxExpansion = Math.max(0.0, hitboxExpansion);
    }

    public double getHitboxExpansion() {
        return hitboxExpansion;
    }

    public void setReachTolerance(double reachTolerance) {
        this.reachTolerance = Math.max(0.0, reachTolerance);
    }

    public double getReachTolerance() {
        return reachTolerance;
    }

    /**
     * Directly evaluates the geometry of an attack ray against a target bounding box.
     *
     * @param attackerEye   Eye position of the attacker at attack tick.
     * @param attackerYaw   Attacker's yaw rotation.
     * @param attackerPitch Attacker's pitch rotation.
     * @param targetBox     Target's AABB (already lag-compensated).
     * @param isCreative    Whether the attacker is in Creative mode.
     * @return Deterministic CombatViolation verdict.
     */
    public CombatViolation validateHitGeometry(Vector3D attackerEye, float attackerYaw, float attackerPitch,
                                              BoundingBox3D targetBox, boolean isCreative) {
        double maxAllowed = (isCreative ? creativeMaxReach : survivalMaxReach) + reachTolerance;

        // Expand target hitbox by configured network latency / ping tolerance
        BoundingBox3D testBox = hitboxExpansion > 0.0 ? targetBox.expand(hitboxExpansion) : targetBox;

        Ray3D attackRay = Ray3D.fromEyeAndRotations(attackerEye, attackerYaw, attackerPitch);
        OptionalDouble hit = testBox.rayIntersection(attackRay);

        if (hit.isEmpty()) {
            return CombatViolation.noIntersection(maxAllowed);
        }

        double distance = hit.getAsDouble();
        if (distance > maxAllowed) {
            return CombatViolation.reachExceeded(distance, maxAllowed);
        }

        return CombatViolation.pass(distance, maxAllowed);
    }
}
