package com.skooper.sentinelac.movement.check;

import com.skooper.sentinelac.movement.model.EnvironmentState;
import com.skooper.sentinelac.movement.model.PlayerInput;
import com.skooper.sentinelac.movement.model.PlayerPhysicsState;
import com.skooper.sentinelac.movement.model.Vector3D;
import com.skooper.sentinelac.movement.simulator.PhysicsConstants;
import com.skooper.sentinelac.movement.simulator.PhysicsSimulator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates player movement packets against server-authoritative physics simulations.
 * Incorporates grace windows for teleports, velocity changes, and respawns.
 */
public final class MovementCheck {

    private final PhysicsSimulator simulator;
    private final Map<UUID, PlayerPhysicsState> playerStates = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> graceTicksRemaining = new ConcurrentHashMap<>();

    private volatile double epsilon = PhysicsConstants.DEFAULT_EPSILON;
    private volatile int defaultGraceTicks = 10;

    public MovementCheck() {
        this.simulator = new PhysicsSimulator();
    }

    public MovementCheck(PhysicsSimulator simulator) {
        this.simulator = simulator;
    }

    public void setEpsilon(double epsilon) {
        this.epsilon = Math.max(0.0001, epsilon);
    }

    public double getEpsilon() {
        return epsilon;
    }

    public void setDefaultGraceTicks(int defaultGraceTicks) {
        this.defaultGraceTicks = Math.max(0, defaultGraceTicks);
    }

    public int getDefaultGraceTicks() {
        return defaultGraceTicks;
    }

    public void triggerGrace(UUID playerId, int ticks) {
        graceTicksRemaining.merge(playerId, ticks, Math::max);
    }

    public void initializePlayer(UUID playerId, Vector3D pos, float yaw, float pitch, boolean onGround) {
        playerStates.put(playerId, PlayerPhysicsState.initial(pos, yaw, pitch, onGround));
        triggerGrace(playerId, defaultGraceTicks);
    }

    public void removePlayer(UUID playerId) {
        playerStates.remove(playerId);
        graceTicksRemaining.remove(playerId);
    }

    public PlayerPhysicsState getPlayerState(UUID playerId) {
        return playerStates.get(playerId);
    }

    /**
     * Checks a reported movement packet against predicted physics.
     */
    public MovementViolation checkMovement(UUID playerId, Vector3D reportedPos, PlayerInput input, EnvironmentState env) {
        PlayerPhysicsState currentState = playerStates.get(playerId);
        if (currentState == null) {
            initializePlayer(playerId, reportedPos, input.getYaw(), input.getPitch(), env.isOnGround());
            return MovementViolation.grace(0.0, reportedPos, reportedPos, env.getMedium().name());
        }

        int grace = graceTicksRemaining.getOrDefault(playerId, 0);
        boolean inGrace = grace > 0;
        if (inGrace) {
            graceTicksRemaining.put(playerId, grace - 1);
        }

        // Simulate expected next tick
        PlayerPhysicsState predictedState = simulator.simulateTick(currentState, input, env);
        Vector3D expectedPos = predictedState.getPosition();

        double delta = reportedPos.distance(expectedPos);

        if (inGrace) {
            // Re-anchor physics state to reported position during grace periods (e.g. knockback/teleport)
            Vector3D reconciledVel = reportedPos.subtract(currentState.getPosition());
            PlayerPhysicsState reconciled = new PlayerPhysicsState(
                    reportedPos, reconciledVel, input.getYaw(), input.getPitch(),
                    env.isOnGround(), currentState.getTick() + 1, 0.0f
            );
            playerStates.put(playerId, reconciled);
            return MovementViolation.grace(delta, expectedPos, reportedPos, env.getMedium().name());
        }

        if (delta > epsilon) {
            // Delta exceeds epsilon tolerance: violation detected
            return MovementViolation.fail(delta, expectedPos, reportedPos, env.getMedium().name());
        }

        // Within tolerance: update confirmed state
        Vector3D confirmedVel = reportedPos.subtract(currentState.getPosition());
        PlayerPhysicsState confirmed = new PlayerPhysicsState(
                reportedPos, confirmedVel, input.getYaw(), input.getPitch(),
                env.isOnGround(), currentState.getTick() + 1, predictedState.getFallDistance()
        );
        playerStates.put(playerId, confirmed);

        return MovementViolation.pass(delta, expectedPos, reportedPos, env.getMedium().name());
    }
}
