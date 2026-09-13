# SentinelAC Development Progress

## Phase 0 — Scaffolding & Repository Setup
- **Status:** COMPLETED
- **What was built:**
  - Standard Maven project structure (`src/main/java`, `src/test/java`, `src/main/resources`).
  - `.gitignore` configured for Java, Maven, IDEs, and OS temp files.
  - `pom.xml` configured with PaperMC 1.20.4 API, pure Java SQLite JDBC, Apache Commons Math 3, JUnit 5 Jupiter, Mockito, and Maven Shade Plugin with relocated shaded packages.
  - GitHub Actions CI workflow in `.github/workflows/build.yml` compiling with JDK 17, running `mvn -B verify`, and uploading `SentinelAC-*.jar`.
  - Base package `com.skooper.sentinelac` and primary entry point `SentinelAC` extending `JavaPlugin`.
  - `plugin.yml` declaring `SentinelAC`, API version `1.20`, root command `/sentinelac`, and permission `sentinelac.admin`.
- **Assumptions made:**
  - Targeted PaperMC API `1.20.4-R0.1-SNAPSHOT` as stable release baseline.
  - Relocated shaded runtime libraries (`org.sqlite` -> `com.skooper.sentinelac.libs.sqlite`, `org.apache.commons.math3` -> `com.skooper.sentinelac.libs.math3`) to prevent classpath conflicts with other Paper plugins.
- **What's next:**
  - Phase 1: Movement Engine.

## Phase 1 — Movement Engine
- **Status:** COMPLETED
- **What was built:**
  - `com.skooper.sentinelac.movement.model`: `Vector3D` immutable 3D vector, `PlayerInput` tick controls, `MovementMedium` (air, water, lava, cobweb, elytra), `BlockFriction` (normal, ice, packed ice, blue ice, slime, honey, soul sand), `EnvironmentState`, and `PlayerPhysicsState`.
  - `com.skooper.sentinelac.movement.simulator`: `PhysicsConstants` and `PhysicsSimulator` replicating per-tick vanilla physics: gravity (0.08 blocks/tick² in air, 0.02 in water/lava), vertical drag (0.98 air, 0.80 water, 0.50 lava), horizontal inertia (0.91 air, 0.80 water, 0.50 lava, block slipperiness scaled), jump impulse (0.42) with sprint boost (0.20), slime rebound, honey fall caps, cobweb resistance, and pitch-dependent elytra gliding.
  - `com.skooper.sentinelac.movement.check`: `MovementCheck` and `MovementViolation` calculating spatial deltas against epsilon tolerance, managing per-player states, and absorbing network lag/velocity via configurable grace windows.
  - `com.skooper.sentinelac.movement.listener`: `MovementListener` integrating Paper events (`PlayerMoveEvent`, `PlayerTeleportEvent`, `PlayerVelocityEvent`, `PlayerJoinEvent`, `PlayerQuitEvent`).
  - Unit tests in `MovementPhysicsTest` covering jumping, sprint-jumping, water drag, ice sliding, elytra glide, cobweb damping, and delta violation thresholds.
- **Assumptions made:**
  - Standard epsilon tolerance configured to 0.005 blocks to accommodate client-server floating-point rounding.
  - Grace window defaults to 10–15 ticks on velocity change and teleport to prevent false flags on knockback or server teleportation.
- **What's next:**
  - Phase 2: Combat Engine (`combat` package) — server-authoritative raycast + lag-compensated hit validation (3D ray-AABB intersection test, maximum reach enforcement, and synthetic geometry unit tests).
