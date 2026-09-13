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
  - Phase 1: Movement Engine — deterministic Minecraft physics replication, tick simulation across medium types (air, water, lava, ice, slime, honey, cobweb, elytra), delta comparison against client reports with grace window, and comprehensive unit tests.
