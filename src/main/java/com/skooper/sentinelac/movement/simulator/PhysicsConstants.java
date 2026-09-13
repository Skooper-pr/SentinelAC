package com.skooper.sentinelac.movement.simulator;

/**
 * Fundamental Minecraft kinematic and environmental physical constants.
 */
public final class PhysicsConstants {

    private PhysicsConstants() {}

    // Gravity
    public static final double GRAVITY_AIR = 0.08;
    public static final double GRAVITY_WATER = 0.02;
    public static final double GRAVITY_LAVA = 0.02;

    // Vertical drag multipliers
    public static final double DRAG_VERTICAL_AIR = 0.98;
    public static final double DRAG_VERTICAL_WATER = 0.80;
    public static final double DRAG_VERTICAL_LAVA = 0.50;

    // Horizontal inertia / drag multipliers
    public static final double INERTIA_AIR = 0.91;
    public static final double DRAG_HORIZONTAL_WATER = 0.80;
    public static final double DRAG_HORIZONTAL_LAVA = 0.50;

    // Acceleration constants
    public static final double ACCELERATION_AIR = 0.02;
    public static final double ACCELERATION_AIR_SPRINT = 0.026;
    public static final double ACCELERATION_WATER = 0.02;
    public static final double ACCELERATION_LAVA = 0.02;
    public static final double ACCELERATION_GROUND_BASE = 0.10;

    // Jump velocity impulse
    public static final double JUMP_IMPULSE_BASE = 0.42;
    public static final double SPRINT_JUMP_HORIZONTAL_BOOST = 0.20;

    // Special block factors
    public static final double WEB_HORIZONTAL_FACTOR = 0.25;
    public static final double WEB_VERTICAL_FACTOR = 0.05;
    public static final double HONEY_FALL_VELOCITY_CAP = -0.05;

    // Elytra constants
    public static final double ELYTRA_DRAG_HORIZONTAL = 0.99;
    public static final double ELYTRA_DRAG_VERTICAL = 0.98;
    public static final double ELYTRA_GRAVITY = 0.08;

    // Tolerances
    public static final double DEFAULT_EPSILON = 0.005;
}
