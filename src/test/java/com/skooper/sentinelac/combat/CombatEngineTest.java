package com.skooper.sentinelac.combat;

import com.skooper.sentinelac.combat.engine.CombatEngine;
import com.skooper.sentinelac.combat.engine.LagCompensator;
import com.skooper.sentinelac.combat.model.BoundingBox3D;
import com.skooper.sentinelac.combat.model.CombatViolation;
import com.skooper.sentinelac.movement.model.Vector3D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatEngineTest {

    private CombatEngine combatEngine;
    private LagCompensator lagCompensator;

    @BeforeEach
    void setUp() {
        lagCompensator = new LagCompensator();
        combatEngine = new CombatEngine(lagCompensator);
        combatEngine.setSurvivalMaxReach(3.0);
        combatEngine.setCreativeMaxReach(5.0);
        combatEngine.setHitboxExpansion(0.0);
        combatEngine.setReachTolerance(0.0);
    }

    @Test
    @DisplayName("Synthetic Case 1: Clear hit directly looking at target within reach")
    void testClearHit() {
        Vector3D attackerEye = new Vector3D(0.0, 1.62, 0.0);
        // Target 2.5 blocks away along Z axis, width 0.6 (X in [-0.3, 0.3], Z in [2.2, 2.8])
        BoundingBox3D targetBox = BoundingBox3D.fromCenterAndDimensions(new Vector3D(0.0, 0.0, 2.5), 0.6, 1.8);

        // Yaw 0 (facing +Z), Pitch 0 (horizontal)
        CombatViolation result = combatEngine.validateHitGeometry(attackerEye, 0.0f, 0.0f, targetBox, false);

        assertFalse(result.isViolation(), "Clear hit must not be flagged");
        assertEquals(CombatViolation.ViolationType.NONE, result.getType());
        assertEquals(2.2, result.getMeasuredDistance(), 1.0E-4);
    }

    @Test
    @DisplayName("Synthetic Case 2: Clear miss looking 90 degrees away from target")
    void testClearMiss() {
        Vector3D attackerEye = new Vector3D(0.0, 1.62, 0.0);
        BoundingBox3D targetBox = BoundingBox3D.fromCenterAndDimensions(new Vector3D(0.0, 0.0, 2.5), 0.6, 1.8);

        // Yaw 90 (facing -X) -> target is along +Z
        CombatViolation result = combatEngine.validateHitGeometry(attackerEye, 90.0f, 0.0f, targetBox, false);

        assertTrue(result.isViolation(), "Attacking away from target must be flagged");
        assertEquals(CombatViolation.ViolationType.IMPOSSIBLE_HIT_NO_INTERSECTION, result.getType());
    }

    @Test
    @DisplayName("Synthetic Case 3: Edge-grazing hit barely inside vs barely outside hitbox")
    void testEdgeGrazingHit() {
        Vector3D attackerEye = new Vector3D(0.0, 1.62, 0.0);
        BoundingBox3D targetBox = new BoundingBox3D(-0.3, 0.0, 2.0, 0.3, 1.8, 2.6);

        // Front face is at Z = 2.0. Box X bounds are [-0.3, 0.3].
        // 1. Ray aiming at X = 0.290, Z = 2.0 (inside edge):
        // tan(theta) = 0.290 / 2.0 = 0.145 -> yaw = -atan(0.145) = -8.249 deg
        float hitYaw = (float) -Math.toDegrees(Math.atan2(0.290, 2.0));
        CombatViolation hitResult = combatEngine.validateHitGeometry(attackerEye, hitYaw, 0.0f, targetBox, false);
        assertFalse(hitResult.isViolation(), "Ray touching inside edge of hitbox should hit");

        // 2. Ray aiming at X = 0.350, Z = 2.0 (outside edge):
        float missYaw = (float) -Math.toDegrees(Math.atan2(0.350, 2.0));
        CombatViolation missResult = combatEngine.validateHitGeometry(attackerEye, missYaw, 0.0f, targetBox, false);
        assertTrue(missResult.isViolation(), "Ray passing outside edge must miss");
        assertEquals(CombatViolation.ViolationType.IMPOSSIBLE_HIT_NO_INTERSECTION, missResult.getType());
    }

    @Test
    @DisplayName("Synthetic Case 4: Hit at exact maximum legal reach distance")
    void testHitAtMaxLegalReach() {
        Vector3D attackerEye = new Vector3D(0.0, 1.62, 0.0);
        // Front face at Z = 3.00, survival max reach is 3.00
        BoundingBox3D targetBox = new BoundingBox3D(-0.3, 0.0, 3.00, 0.3, 1.8, 3.60);

        CombatViolation result = combatEngine.validateHitGeometry(attackerEye, 0.0f, 0.0f, targetBox, false);

        assertFalse(result.isViolation(), "Hit at exact legal reach limit should be allowed");
        assertEquals(3.00, result.getMeasuredDistance(), 1.0E-4);
    }

    @Test
    @DisplayName("Synthetic Case 5: Hit just beyond maximum legal reach distance")
    void testHitJustBeyondMaxReach() {
        Vector3D attackerEye = new Vector3D(0.0, 1.62, 0.0);
        // Front face at Z = 3.05 -> reach 3.05 exceeds survival limit of 3.00
        BoundingBox3D targetBox = new BoundingBox3D(-0.3, 0.0, 3.05, 0.3, 1.8, 3.65);

        CombatViolation result = combatEngine.validateHitGeometry(attackerEye, 0.0f, 0.0f, targetBox, false);

        assertTrue(result.isViolation(), "Hit at 3.05m must be flagged as reach violation");
        assertEquals(CombatViolation.ViolationType.REACH_EXCEEDED, result.getType());
        assertEquals(3.05, result.getMeasuredDistance(), 1.0E-4);

        // In Creative mode with 5.0m reach, 3.05m is valid
        CombatViolation creativeResult = combatEngine.validateHitGeometry(attackerEye, 0.0f, 0.0f, targetBox, true);
        assertFalse(creativeResult.isViolation(), "Hit at 3.05m is valid in creative mode");
    }

    @Test
    @DisplayName("Lag Compensation: Successfully rewinds entity hitbox based on latency")
    void testLagCompensationRewind() {
        UUID targetId = UUID.randomUUID();
        // At tick 100, target was at Z = 2.0 (in reach)
        BoundingBox3D boxTick100 = new BoundingBox3D(-0.3, 0.0, 2.0, 0.3, 1.8, 2.6);
        // At tick 105, target moved to Z = 15.0 (far away)
        BoundingBox3D boxTick105 = new BoundingBox3D(-0.3, 0.0, 15.0, 0.3, 1.8, 15.6);

        lagCompensator.recordEntityState(targetId, 100L, boxTick100);
        lagCompensator.recordEntityState(targetId, 105L, boxTick105);

        // Attacker has 250ms ping -> (250 / 50) = 5 ticks rewind
        // At server tick 105, target tick is 105 - 5 = 100
        Optional<BoundingBox3D> compensated = lagCompensator.getCompensatedHitbox(targetId, 105L, 250);

        assertTrue(compensated.isPresent());
        assertEquals(2.0, compensated.get().getMinZ(), 1.0E-4);

        // Validate hit with rewound box
        Vector3D attackerEye = new Vector3D(0.0, 1.62, 0.0);
        CombatViolation result = combatEngine.validateHitGeometry(attackerEye, 0.0f, 0.0f, compensated.get(), false);
        assertFalse(result.isViolation(), "Attack matching lag-compensated position must pass");
    }
}
