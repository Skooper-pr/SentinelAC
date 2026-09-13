package com.skooper.sentinelac.movement;

import com.skooper.sentinelac.movement.check.MovementCheck;
import com.skooper.sentinelac.movement.check.MovementViolation;
import com.skooper.sentinelac.movement.model.BlockFriction;
import com.skooper.sentinelac.movement.model.EnvironmentState;
import com.skooper.sentinelac.movement.model.MovementMedium;
import com.skooper.sentinelac.movement.model.PlayerInput;
import com.skooper.sentinelac.movement.model.PlayerPhysicsState;
import com.skooper.sentinelac.movement.model.Vector3D;
import com.skooper.sentinelac.movement.simulator.PhysicsConstants;
import com.skooper.sentinelac.movement.simulator.PhysicsSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementPhysicsTest {

    private PhysicsSimulator simulator;
    private MovementCheck movementCheck;
    private static final double EPSILON = 1.0E-4;

    @BeforeEach
    void setUp() {
        simulator = new PhysicsSimulator();
        movementCheck = new MovementCheck(simulator);
    }

    @Test
    @DisplayName("Verify deterministic jumping tick sequence matches known-correct trajectory")
    void testJumpingSequence() {
        PlayerPhysicsState state = PlayerPhysicsState.initial(new Vector3D(0.0, 64.0, 0.0), 0.0f, 0.0f, true);
        EnvironmentState envGround = EnvironmentState.ground(BlockFriction.NORMAL);
        EnvironmentState envAir = EnvironmentState.air(false);

        // Tick 1: Initiate jump from ground
        PlayerInput jumpInput = PlayerInput.jumping(0.0, 0.0, 0.0f, 0.0f);
        state = simulator.simulateTick(state, jumpInput, envGround);

        // Expected Vy after jump impulse (0.42) then air gravity & drag: (0.42 - 0.08) * 0.98 = 0.3332
        double expectedVy1 = (PhysicsConstants.JUMP_IMPULSE_BASE - PhysicsConstants.GRAVITY_AIR) * PhysicsConstants.DRAG_VERTICAL_AIR;
        assertEquals(expectedVy1, state.getVelocity().getY(), EPSILON);
        assertEquals(64.0 + expectedVy1, state.getPosition().getY(), EPSILON);

        // Tick 2: Ascending in air
        PlayerInput idleInput = PlayerInput.idle(0.0f, 0.0f);
        state = simulator.simulateTick(state, idleInput, envAir);
        double expectedVy2 = (expectedVy1 - PhysicsConstants.GRAVITY_AIR) * PhysicsConstants.DRAG_VERTICAL_AIR;
        assertEquals(expectedVy2, state.getVelocity().getY(), EPSILON);
        assertEquals(64.0 + expectedVy1 + expectedVy2, state.getPosition().getY(), EPSILON);

        // Tick 3: Ascending in air towards apex
        state = simulator.simulateTick(state, idleInput, envAir);
        double expectedVy3 = (expectedVy2 - PhysicsConstants.GRAVITY_AIR) * PhysicsConstants.DRAG_VERTICAL_AIR;
        assertEquals(expectedVy3, state.getVelocity().getY(), EPSILON);
        assertEquals(64.0 + expectedVy1 + expectedVy2 + expectedVy3, state.getPosition().getY(), EPSILON);
    }

    @Test
    @DisplayName("Verify sprint-jump provides forward boost and accurate horizontal displacement")
    void testSprintJumpingSequence() {
        // Facing South (yaw = 0): +Z direction
        PlayerPhysicsState state = PlayerPhysicsState.initial(new Vector3D(0.0, 64.0, 0.0), 0.0f, 0.0f, true);
        EnvironmentState envGround = EnvironmentState.ground(BlockFriction.NORMAL);

        PlayerInput sprintJumpInput = PlayerInput.sprintJumping(1.0, 0.0, 0.0f, 0.0f);
        PlayerPhysicsState nextState = simulator.simulateTick(state, sprintJumpInput, envGround);

        // Vertical must have jump impulse applied
        assertTrue(nextState.getVelocity().getY() > 0.3);
        // Horizontal Z velocity must include sprint boost (>= 0.20)
        assertTrue(nextState.getVelocity().getZ() >= 0.20);
        assertTrue(nextState.getPosition().getZ() > 0.0);
    }

    @Test
    @DisplayName("Verify water movement applies fluid drag and fluid gravity")
    void testWaterMovementSequence() {
        PlayerPhysicsState state = PlayerPhysicsState.initial(new Vector3D(0.0, 60.0, 0.0), 0.0f, 0.0f, false);
        EnvironmentState envWater = EnvironmentState.water();

        PlayerInput forwardInput = PlayerInput.walking(1.0, 0.0, 0.0f, 0.0f);
        PlayerPhysicsState tick1 = simulator.simulateTick(state, forwardInput, envWater);

        // In water: gravity is 0.02, drag is 0.8 -> vertical velocity = -0.02 * 0.8 = -0.016
        double expectedVy = -PhysicsConstants.GRAVITY_WATER * PhysicsConstants.DRAG_VERTICAL_WATER;
        assertEquals(expectedVy, tick1.getVelocity().getY(), EPSILON);

        // Forward motion in water: accel 0.02, drag 0.8 -> Vz = 0.02 * 0.8 = 0.016
        double expectedVz = PhysicsConstants.ACCELERATION_WATER * PhysicsConstants.DRAG_HORIZONTAL_WATER;
        assertEquals(expectedVz, tick1.getVelocity().getZ(), EPSILON);
        assertEquals(60.0 + expectedVy, tick1.getPosition().getY(), EPSILON);
        assertEquals(expectedVz, tick1.getPosition().getZ(), EPSILON);
    }

    @Test
    @DisplayName("Verify ice sliding retains momentum significantly longer than normal ground")
    void testIceSlidingFriction() {
        Vector3D initialVelocity = new Vector3D(0.0, 0.0, 0.5);
        PlayerPhysicsState stateIce = new PlayerPhysicsState(new Vector3D(0.0, 64.0, 0.0), initialVelocity, 0.0f, 0.0f, true, 0L, 0.0f);
        PlayerPhysicsState stateNormal = new PlayerPhysicsState(new Vector3D(0.0, 64.0, 0.0), initialVelocity, 0.0f, 0.0f, true, 0L, 0.0f);

        EnvironmentState envIce = EnvironmentState.ground(BlockFriction.ICE);
        EnvironmentState envNormal = EnvironmentState.ground(BlockFriction.NORMAL);

        PlayerInput coasting = PlayerInput.idle(0.0f, 0.0f);

        PlayerPhysicsState nextIce = simulator.simulateTick(stateIce, coasting, envIce);
        PlayerPhysicsState nextNormal = simulator.simulateTick(stateNormal, coasting, envNormal);

        // Ice slipperiness 0.98 vs Normal 0.60
        // Ice retains 0.98 * 0.91 = 0.8918 of velocity
        // Normal retains 0.60 * 0.91 = 0.5460 of velocity
        assertEquals(0.5 * 0.98 * 0.91, nextIce.getVelocity().getZ(), EPSILON);
        assertEquals(0.5 * 0.60 * 0.91, nextNormal.getVelocity().getZ(), EPSILON);
        assertTrue(nextIce.getVelocity().getZ() > nextNormal.getVelocity().getZ());
    }

    @Test
    @DisplayName("Verify elytra flight kinematics based on pitch and horizontal lift")
    void testElytraFlight() {
        Vector3D glideVelocity = new Vector3D(0.0, 0.0, 1.2);
        PlayerPhysicsState state = new PlayerPhysicsState(new Vector3D(0.0, 100.0, 0.0), glideVelocity, 0.0f, 0.0f, false, 0L, 0.0f);
        EnvironmentState envElytra = EnvironmentState.elytra();

        // Level flight (pitch = 0)
        PlayerInput inputLevel = PlayerInput.idle(0.0f, 0.0f);
        PlayerPhysicsState next = simulator.simulateTick(state, inputLevel, envElytra);

        // Should maintain horizontal motion with 0.99 drag and slight gravity descent
        assertTrue(next.getVelocity().getZ() > 1.0);
        assertTrue(next.getPosition().getY() < 100.0);
    }

    @Test
    @DisplayName("Verify cobweb dramatically slows movement down")
    void testCobwebDamping() {
        Vector3D movingVelocity = new Vector3D(0.0, 0.0, 0.4);
        PlayerPhysicsState state = new PlayerPhysicsState(new Vector3D(0.0, 64.0, 0.0), movingVelocity, 0.0f, 0.0f, false, 0L, 0.0f);
        EnvironmentState envWeb = EnvironmentState.cobweb();

        PlayerPhysicsState next = simulator.simulateTick(state, PlayerInput.idle(0.0f, 0.0f), envWeb);

        // Web applies 0.25 horizontal factor and 0.05 vertical factor
        assertTrue(next.getVelocity().getZ() < 0.15);
    }

    @Test
    @DisplayName("Verify MovementCheck flags speed/fly hacks beyond epsilon and honors grace windows")
    void testMovementCheckViolationsAndGrace() {
        UUID playerId = UUID.randomUUID();
        Vector3D startPos = new Vector3D(100.0, 64.0, 100.0);
        movementCheck.initializePlayer(playerId, startPos, 0.0f, 0.0f, true);

        EnvironmentState envGround = EnvironmentState.ground(BlockFriction.NORMAL);
        PlayerInput walking = PlayerInput.walking(1.0, 0.0, 0.0f, 0.0f);

        // Consume grace ticks (initialized with 10 grace ticks)
        for (int i = 0; i < 10; i++) {
            movementCheck.checkMovement(playerId, startPos, walking, envGround);
        }

        // Now player is out of grace. Simulate one legitimate tick:
        PlayerPhysicsState current = movementCheck.getPlayerState(playerId);
        PlayerPhysicsState predicted = simulator.simulateTick(current, walking, envGround);

        // Case 1: Client reports exact predicted position -> PASS
        MovementViolation passResult = movementCheck.checkMovement(playerId, predicted.getPosition(), walking, envGround);
        assertFalse(passResult.isViolation());
        assertFalse(passResult.isGracePeriodActive());

        // Case 2: Client teleports 10 blocks ahead (Speed/Fly hack) -> FAIL
        Vector3D hackedPos = predicted.getPosition().add(0.0, 5.0, 10.0);
        MovementViolation failResult = movementCheck.checkMovement(playerId, hackedPos, walking, envGround);
        assertTrue(failResult.isViolation());
        assertTrue(failResult.getDelta() > movementCheck.getEpsilon());

        // Case 3: Knockback / Teleport triggers grace window -> PASS (Grace active)
        movementCheck.triggerGrace(playerId, 5);
        MovementViolation graceResult = movementCheck.checkMovement(playerId, hackedPos, walking, envGround);
        assertFalse(graceResult.isViolation());
        assertTrue(graceResult.isGracePeriodActive());
    }
}
