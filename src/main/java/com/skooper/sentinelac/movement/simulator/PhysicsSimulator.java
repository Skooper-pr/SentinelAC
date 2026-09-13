package com.skooper.sentinelac.movement.simulator;

import com.skooper.sentinelac.movement.model.BlockFriction;
import com.skooper.sentinelac.movement.model.EnvironmentState;
import com.skooper.sentinelac.movement.model.MovementMedium;
import com.skooper.sentinelac.movement.model.PlayerInput;
import com.skooper.sentinelac.movement.model.PlayerPhysicsState;
import com.skooper.sentinelac.movement.model.Vector3D;

/**
 * Server-authoritative deterministic Minecraft player physics simulation engine.
 * Faithfully replicates per-tick kinematic state updates across all mediums and block interactions.
 */
public final class PhysicsSimulator {

    /**
     * Simulates exactly one tick of player motion given initial state, control inputs, and environment.
     *
     * @param current The current kinematic state at tick T.
     * @param input   The player's input controls for tick T.
     * @param env     The surrounding medium, block frictions, and effects.
     * @return The predicted kinematic state at tick T + 1.
     */
    public PlayerPhysicsState simulateTick(PlayerPhysicsState current, PlayerInput input, EnvironmentState env) {
        Vector3D velocity = current.getVelocity();
        float yaw = input.getYaw();
        float pitch = input.getPitch();

        // 1. Handle Elytra gliding separately
        if (env.getMedium() == MovementMedium.ELYTRA || env.isGliding()) {
            return simulateElytraTick(current, input, env);
        }

        // 2. Handle Jump initiation if on ground
        boolean jumpingThisTick = current.isOnGround() && input.isJumping();
        if (jumpingThisTick) {
            if (env.getBlockFriction() == BlockFriction.HONEY) {
                // Honey drastically limits jumping capability
                velocity = velocity.add(0.0, PhysicsConstants.JUMP_IMPULSE_BASE * 0.5, 0.0);
            } else {
                double jumpImpulse = PhysicsConstants.JUMP_IMPULSE_BASE;
                if (env.getJumpBoostLevel() > 0) {
                    jumpImpulse += env.getJumpBoostLevel() * 0.1;
                }
                velocity = new Vector3D(velocity.getX(), jumpImpulse, velocity.getZ());

                if (input.isSprinting()) {
                    double yawRad = Math.toRadians(yaw);
                    double boostX = -Math.sin(yawRad) * PhysicsConstants.SPRINT_JUMP_HORIZONTAL_BOOST;
                    double boostZ = Math.cos(yawRad) * PhysicsConstants.SPRINT_JUMP_HORIZONTAL_BOOST;
                    velocity = velocity.add(boostX, 0.0, boostZ);
                }
            }
        }

        // 3. Medium-specific acceleration and drag
        MovementMedium medium = env.getMedium();
        if (medium == MovementMedium.WATER) {
            velocity = simulateWater(velocity, input, env);
        } else if (medium == MovementMedium.LAVA) {
            velocity = simulateLava(velocity, input, env);
        } else if (env.isOnGround() && !jumpingThisTick) {
            velocity = simulateGround(velocity, input, env);
        } else {
            velocity = simulateAir(velocity, input, env);
        }

        // 4. Cobweb interaction
        if (env.isInCobweb() || medium == MovementMedium.COBWEB) {
            velocity = velocity.multiply(
                    PhysicsConstants.WEB_HORIZONTAL_FACTOR,
                    PhysicsConstants.WEB_VERTICAL_FACTOR,
                    PhysicsConstants.WEB_HORIZONTAL_FACTOR
            );
        }

        // 5. Honey wall slide cap
        if (env.getBlockFriction() == BlockFriction.HONEY && velocity.getY() < PhysicsConstants.HONEY_FALL_VELOCITY_CAP) {
            velocity = new Vector3D(velocity.getX(), PhysicsConstants.HONEY_FALL_VELOCITY_CAP, velocity.getZ());
        }

        // 6. Slime block bounce
        boolean nextOnGround = env.isOnGround() && !jumpingThisTick;
        if (env.getBlockFriction() == BlockFriction.SLIME && !input.isSneaking() && current.getVelocity().getY() < -0.1 && nextOnGround) {
            velocity = new Vector3D(velocity.getX(), -current.getVelocity().getY() * 0.8, velocity.getZ());
            nextOnGround = false;
        }

        // 7. Integrate position
        Vector3D nextPosition = current.getPosition().add(velocity);

        return new PlayerPhysicsState(
                nextPosition,
                velocity,
                yaw,
                pitch,
                nextOnGround,
                current.getTick() + 1,
                nextOnGround ? 0.0f : (float) (current.getFallDistance() - velocity.getY())
        );
    }

    private Vector3D simulateGround(Vector3D velocity, PlayerInput input, EnvironmentState env) {
        BlockFriction friction = env.getBlockFriction();
        double s = friction.getSlipperiness();
        double accelBase = PhysicsConstants.ACCELERATION_GROUND_BASE * Math.pow(0.6 / s, 3);

        if (input.isSprinting()) {
            accelBase *= 1.3;
        }
        if (env.getSpeedEffectLevel() > 0) {
            accelBase *= (1.0 + 0.2 * env.getSpeedEffectLevel());
        }
        accelBase *= friction.getSpeedModifier();

        velocity = applyInputAcceleration(velocity, input, accelBase);
        velocity = new Vector3D(velocity.getX() * (s * 0.91), 0.0, velocity.getZ() * (s * 0.91));
        return velocity;
    }

    private Vector3D simulateAir(Vector3D velocity, PlayerInput input, EnvironmentState env) {
        double accel = input.isSprinting() ? PhysicsConstants.ACCELERATION_AIR_SPRINT : PhysicsConstants.ACCELERATION_AIR;
        velocity = applyInputAcceleration(velocity, input, accel);

        double vy = (velocity.getY() - PhysicsConstants.GRAVITY_AIR) * PhysicsConstants.DRAG_VERTICAL_AIR;
        double vx = velocity.getX() * PhysicsConstants.INERTIA_AIR;
        double vz = velocity.getZ() * PhysicsConstants.INERTIA_AIR;
        return new Vector3D(vx, vy, vz);
    }

    private Vector3D simulateWater(Vector3D velocity, PlayerInput input, EnvironmentState env) {
        velocity = applyInputAcceleration(velocity, input, PhysicsConstants.ACCELERATION_WATER);
        double vy = (velocity.getY() - PhysicsConstants.GRAVITY_WATER) * PhysicsConstants.DRAG_VERTICAL_WATER;
        if (input.isJumping()) {
            vy += 0.04;
        }
        double vx = velocity.getX() * PhysicsConstants.DRAG_HORIZONTAL_WATER;
        double vz = velocity.getZ() * PhysicsConstants.DRAG_HORIZONTAL_WATER;
        return new Vector3D(vx, vy, vz);
    }

    private Vector3D simulateLava(Vector3D velocity, PlayerInput input, EnvironmentState env) {
        velocity = applyInputAcceleration(velocity, input, PhysicsConstants.ACCELERATION_LAVA);
        double vy = (velocity.getY() - PhysicsConstants.GRAVITY_LAVA) * PhysicsConstants.DRAG_VERTICAL_LAVA;
        if (input.isJumping()) {
            vy += 0.04;
        }
        double vx = velocity.getX() * PhysicsConstants.DRAG_HORIZONTAL_LAVA;
        double vz = velocity.getZ() * PhysicsConstants.DRAG_HORIZONTAL_LAVA;
        return new Vector3D(vx, vy, vz);
    }

    private PlayerPhysicsState simulateElytraTick(PlayerPhysicsState current, PlayerInput input, EnvironmentState env) {
        Vector3D vel = current.getVelocity();
        float yaw = input.getYaw();
        float pitch = input.getPitch();

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double cosPitch = Math.cos(pitchRad);
        double lookX = -Math.sin(yawRad) * cosPitch;
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * cosPitch;

        double vy = (vel.getY() - PhysicsConstants.ELYTRA_GRAVITY + lookY * -0.04) * PhysicsConstants.ELYTRA_DRAG_VERTICAL;
        double vx = vel.getX() * PhysicsConstants.ELYTRA_DRAG_HORIZONTAL + lookX * 0.1 * cosPitch;
        double vz = vel.getZ() * PhysicsConstants.ELYTRA_DRAG_HORIZONTAL + lookZ * 0.1 * cosPitch;

        Vector3D nextVel = new Vector3D(vx, vy, vz);
        Vector3D nextPos = current.getPosition().add(nextVel);

        return new PlayerPhysicsState(
                nextPos,
                nextVel,
                yaw,
                pitch,
                false,
                current.getTick() + 1,
                (float) (current.getFallDistance() - nextVel.getY())
        );
    }

    private Vector3D applyInputAcceleration(Vector3D velocity, PlayerInput input, double acceleration) {
        double forward = input.getForward();
        double strafe = input.getStrafe();

        if (input.isSneaking()) {
            forward *= 0.3;
            strafe *= 0.3;
        }

        double lengthSquared = strafe * strafe + forward * forward;
        if (lengthSquared < 1.0E-4) {
            return velocity;
        }

        double length = Math.sqrt(lengthSquared);
        if (length < 1.0) {
            length = 1.0;
        }

        double factor = acceleration / length;
        strafe *= factor;
        forward *= factor;

        double yawRad = Math.toRadians(input.getYaw());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double addX = strafe * cos - forward * sin;
        double addZ = forward * cos + strafe * sin;

        return velocity.add(addX, 0.0, addZ);
    }
}
