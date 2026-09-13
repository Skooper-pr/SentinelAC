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
  - Phase 2: Combat Engine.

## Phase 2 — Combat Engine
- **Status:** COMPLETED
- **What was built:**
  - `com.skooper.sentinelac.combat.model`: `Ray3D` (origin from eye coordinates, normalized directional look vector from yaw/pitch), `BoundingBox3D` (3D AABB with Kay-Kajiya / Williams slab intersection algorithm returning exact distance or miss), `HistoricalEntityState` (per-tick entity hitbox snapshots), and `CombatViolation` (verdicts: `NONE`, `IMPOSSIBLE_HIT_NO_INTERSECTION`, `REACH_EXCEEDED`).
  - `com.skooper.sentinelac.combat.engine`: `LagCompensator` (stores rolling 40-tick history of entity hitboxes, rewinds to exact ping latency tick) and `CombatEngine` (deterministic geometric ray-AABB hit validation, configurable survival/creative reach caps, hitbox expansion).
  - `com.skooper.sentinelac.combat.listener`: `CombatListener` Paper event listener for `EntityDamageByEntityEvent`, `PlayerMoveEvent`, entity death and disconnect cleanup.
  - Comprehensive unit tests in `CombatEngineTest`: synthetic geometry test cases verifying clear hits, 90-degree misses, edge-grazing hits (inside vs outside box edge), hits at exact max reach limit (3.00m), hits beyond limit (3.05m), creative mode reach, and lag-compensated hitbox rewinding.
- **Assumptions made:**
  - Base survival reach default set to 3.00 blocks with 0.05m tolerance; creative reach default set to 5.00 blocks.
  - Lag compensation bounded to a maximum of 20 ticks (1000ms ping) to prevent historical exploitation from artificial lag spiking.
- **What's next:**
  - Phase 3: Behavior Engine (`behavior` package) — statistical/control-theory analysis of clicks and aim:
    - Click-timing analysis: rolling window log-normal distribution fit, Kolmogorov–Smirnov test, and coefficient of variation (stddev/mean).
    - Aim-response analysis: yaw/pitch deltas modeled as damped PID convergence with ~150ms biological reaction floor, calculating residual.
    - Periodicity analysis: FFT / autocorrelation over click-timing and yaw-delta time series detecting spectral peaks indicative of macros/autoclickers.
    - Synthetic legit vs cheat unit tests.
