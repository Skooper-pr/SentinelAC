package com.skooper.sentinelac.fusion;

import com.skooper.sentinelac.behavior.model.BehaviorScore;
import com.skooper.sentinelac.behavior.model.ClickSample;
import com.skooper.sentinelac.behavior.model.PlayerEvidenceWindow;
import com.skooper.sentinelac.behavior.scorer.RuleBasedBehaviorScorer;
import com.skooper.sentinelac.combat.engine.CombatEngine;
import com.skooper.sentinelac.combat.model.BoundingBox3D;
import com.skooper.sentinelac.combat.model.CombatViolation;
import com.skooper.sentinelac.fusion.engine.FusionEngine;
import com.skooper.sentinelac.fusion.model.CheatCategory;
import com.skooper.sentinelac.fusion.model.FusionFlagEvent;
import com.skooper.sentinelac.fusion.model.SprtBoundary;
import com.skooper.sentinelac.movement.check.MovementCheck;
import com.skooper.sentinelac.movement.check.MovementViolation;
import com.skooper.sentinelac.movement.model.BlockFriction;
import com.skooper.sentinelac.movement.model.EnvironmentState;
import com.skooper.sentinelac.movement.model.PlayerInput;
import com.skooper.sentinelac.movement.model.Vector3D;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FusionEngineTest {

    private FusionEngine fusionEngine;
    private static final double EPSILON = 1.0E-5;

    @BeforeEach
    void setUp() {
        fusionEngine = new FusionEngine(0.001, 0.010);
    }

    @Test
    @DisplayName("Wald Boundary: Hand-calculate and verify SPRT boundary formulas across alpha/beta pairs")
    void testSprtBoundaryFormulas() {
        // Pair 1: alpha = 0.001, beta = 0.010
        // Upper A = ln((1 - 0.010) / 0.001) = ln(0.990 / 0.001) = ln(990) = 6.8977049...
        // Lower B = ln(0.010 / (1 - 0.001)) = ln(0.010 / 0.999) = ln(0.010010010...) = -4.6041699...
        SprtBoundary b1 = new SprtBoundary(0.001, 0.010);
        assertEquals(Math.log(990.0), b1.getUpperBound(), EPSILON);
        assertEquals(Math.log(0.010 / 0.999), b1.getLowerBound(), EPSILON);
        assertEquals(6.897705, b1.getUpperBound(), 1.0E-5);
        assertEquals(-4.604170, b1.getLowerBound(), 1.0E-5);

        // Pair 2: alpha = 0.010, beta = 0.050
        // Upper A = ln((1 - 0.050) / 0.010) = ln(0.950 / 0.010) = ln(95) = 4.5538768...
        // Lower B = ln(0.050 / (1 - 0.010)) = ln(0.050 / 0.990) = ln(0.05050505...) = -2.9856826...
        SprtBoundary b2 = new SprtBoundary(0.010, 0.050);
        assertEquals(Math.log(95.0), b2.getUpperBound(), EPSILON);
        assertEquals(Math.log(0.050 / 0.990), b2.getLowerBound(), EPSILON);
        assertEquals(4.553877, b2.getUpperBound(), 1.0E-5);
        assertEquals(-2.985683, b2.getLowerBound(), 1.0E-5);
    }

    @Test
    @DisplayName("Trajectory: Accumulate fixed evidence sequence and assert exact numerical trajectory")
    void testEvidenceAccumulationTrajectory() {
        UUID playerId = UUID.randomUUID();
        double[] evidenceSteps = { 1.2, 0.8, -0.5, 2.1, -0.3 };
        double[] expectedTrajectory = { 1.2, 2.0, 1.5, 3.6, 3.3 };

        for (int i = 0; i < evidenceSteps.length; i++) {
            fusionEngine.addEvidence(playerId, CheatCategory.CLICK, evidenceSteps[i], "Step " + i);
            assertEquals(expectedTrajectory[i], fusionEngine.getLambda(playerId, CheatCategory.CLICK), EPSILON,
                    "Lambda at step " + i + " must match expected value");
        }

        assertFalse(fusionEngine.isFlagged(playerId, CheatCategory.CLICK),
                "Player should not be flagged before crossing upper boundary (3.3 < 6.897)");
    }

    @Test
    @DisplayName("Deterministic Proof: Movement or Combat violation shortcuts directly to upper boundary")
    void testDeterministicViolationShortcut() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger flagEventsReceived = new AtomicInteger(0);
        fusionEngine.registerFlagListener(e -> flagEventsReceived.incrementAndGet());

        assertEquals(0.0, fusionEngine.getLambda(playerId, CheatCategory.MOVEMENT));

        // Submit deterministic violation proof
        String proof = "Movement delta=4.82 exceeds epsilon in AIR (expected=(0,64,0), actual=(0,64,4.82))";
        fusionEngine.addDeterministicViolation(playerId, CheatCategory.MOVEMENT, proof);

        double upper = fusionEngine.getBoundary().getUpperBound();
        assertTrue(fusionEngine.getLambda(playerId, CheatCategory.MOVEMENT) >= upper,
                "Deterministic violation must immediately reach or exceed upper boundary");
        assertTrue(fusionEngine.isFlagged(playerId, CheatCategory.MOVEMENT), "Player must be flagged");
        assertEquals(1, flagEventsReceived.get(), "Flag event must be dispatched immediately");
    }

    @Test
    @DisplayName("Lower Bound: Accumulating legitimate evidence hits lower bound and resets evidence")
    void testLowerBoundReset() {
        UUID playerId = UUID.randomUUID();
        // Lower bound for (0.001, 0.010) is -4.604. Accumulate -1.0 per step
        fusionEngine.addEvidence(playerId, CheatCategory.AIM, -1.5, "Legit 1");
        assertEquals(-1.5, fusionEngine.getLambda(playerId, CheatCategory.AIM), EPSILON);

        fusionEngine.addEvidence(playerId, CheatCategory.AIM, -1.5, "Legit 2");
        assertEquals(-3.0, fusionEngine.getLambda(playerId, CheatCategory.AIM), EPSILON);

        // Next step pushes lambda to -4.5 (still above -4.604)
        fusionEngine.addEvidence(playerId, CheatCategory.AIM, -1.5, "Legit 3");
        assertEquals(-4.5, fusionEngine.getLambda(playerId, CheatCategory.AIM), EPSILON);

        // Next step crosses lower bound (-4.5 + -1.5 = -6.0 <= -4.604) -> clears evidence and resets lambda to 0.0
        fusionEngine.addEvidence(playerId, CheatCategory.AIM, -1.5, "Legit 4 (crosses lower bound)");
        assertEquals(0.0, fusionEngine.getLambda(playerId, CheatCategory.AIM), EPSILON,
                "Crossing lower bound must reset accumulated evidence to 0.0");
        assertFalse(fusionEngine.isFlagged(playerId, CheatCategory.AIM));
    }

    @Test
    @DisplayName("End-to-End Smoke Test: Multi-tick session with legit play followed by cheat injection")
    void testEndToEndSimulatedPlayerSession() {
        UUID playerId = UUID.randomUUID();
        MovementCheck movementCheck = new MovementCheck();
        movementCheck.setDefaultGraceTicks(0);
        movementCheck.initializePlayer(playerId, new Vector3D(0.0, 64.0, 0.0), 0.0f, 0.0f, true);
        CombatEngine combatEngine = new CombatEngine();
        RuleBasedBehaviorScorer behaviorScorer = new RuleBasedBehaviorScorer();

        List<FusionFlagEvent> flags = new ArrayList<>();
        fusionEngine.registerFlagListener(flags::add);

        // --- PHASE A: Ticks 1 to 50: Legit Player Session ---
        JDKRandomGenerator rng = new JDKRandomGenerator(777);
        LogNormalDistribution legitClickDist = new LogNormalDistribution(rng, Math.log(110.0), 0.22);
        List<ClickSample> legitClicks = new ArrayList<>();
        long now = 1000L;
        for (int i = 0; i < 30; i++) {
            double interval = legitClickDist.sample();
            legitClicks.add(new ClickSample(now, interval));
            now += (long) interval;
        }

        PlayerEvidenceWindow legitWindow = new PlayerEvidenceWindow(playerId, legitClicks, List.of());
        BehaviorScore legitScore = behaviorScorer.score(legitWindow);

        // Feed to fusion engine
        fusionEngine.addEvidence(playerId, CheatCategory.CLICK, legitScore.getClickLogLikelihood(), legitScore.getSummary());

        // Legitimate movement check (stationary player)
        EnvironmentState env = EnvironmentState.ground(BlockFriction.NORMAL);
        PlayerInput input = PlayerInput.idle(0.0f, 0.0f);
        MovementViolation moveResult = movementCheck.checkMovement(playerId, new Vector3D(0.0, 64.0, 0.0), input, env);
        if (moveResult.isViolation()) {
            fusionEngine.addDeterministicViolation(playerId, CheatCategory.MOVEMENT, moveResult.getSummary());
        }

        // Legitimate combat attack
        BoundingBox3D targetBox = BoundingBox3D.fromCenterAndDimensions(new Vector3D(0.0, 0.0, 2.2), 0.6, 1.8);
        CombatViolation combatResult = combatEngine.validateHitGeometry(new Vector3D(0.0, 1.62, 0.0), 0.0f, 0.0f, targetBox, false);
        if (combatResult.isViolation()) {
            fusionEngine.addDeterministicViolation(playerId, CheatCategory.COMBAT, combatResult.getSummary());
        }

        // Assert: Zero flags during legitimate session
        assertTrue(flags.isEmpty(), "No flags should be raised during legitimate session");
        assertFalse(fusionEngine.isFlagged(playerId, CheatCategory.CLICK));
        assertFalse(fusionEngine.isFlagged(playerId, CheatCategory.MOVEMENT));
        assertFalse(fusionEngine.isFlagged(playerId, CheatCategory.COMBAT));

        // --- PHASE B: Cheat Injection: Autoclicker activation ---
        List<ClickSample> cheatClicks = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            cheatClicks.add(new ClickSample(now, 100.0)); // Fixed 100ms
            now += 100L;
        }

        PlayerEvidenceWindow cheatWindow = new PlayerEvidenceWindow(playerId, cheatClicks, List.of());
        BehaviorScore cheatScore = behaviorScorer.score(cheatWindow);
        assertTrue(cheatScore.getClickLogLikelihood() > 2.0);

        // Feed autoclicker evidence over successive windows until SPRT upper boundary is crossed
        for (int i = 0; i < 3; i++) {
            fusionEngine.addEvidence(playerId, CheatCategory.CLICK, cheatScore.getClickLogLikelihood(), cheatScore.getSummary());
        }

        // Assert: SPRT upper boundary reached & player flagged for CLICK
        assertTrue(fusionEngine.isFlagged(playerId, CheatCategory.CLICK),
                "Player must be flagged for clicking after consistent autoclicker evidence");
        assertFalse(flags.isEmpty(), "Flag event must have fired");
        assertEquals(CheatCategory.CLICK, flags.get(0).getCategory());

        // --- PHASE C: Cheat Injection: Speed/Teleport hack ---
        // Player teleports 8 blocks away instantaneously without grace
        Vector3D illegalPos = new Vector3D(10.0, 70.0, 10.0);
        MovementViolation illegalMove = movementCheck.checkMovement(playerId, illegalPos, input, env);
        if (illegalMove.isViolation()) {
            fusionEngine.addDeterministicViolation(playerId, CheatCategory.MOVEMENT, illegalMove.getSummary());
        }

        assertTrue(fusionEngine.isFlagged(playerId, CheatCategory.MOVEMENT),
                "Player must be flagged for movement after impossible position delta");
        assertTrue(flags.size() >= 2);
    }
}
