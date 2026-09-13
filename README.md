# SentinelAC

[![Build and Test SentinelAC](https://github.com/Skooper-pr/SentinelAC/actions/workflows/build.yml/badge.svg)](https://github.com/Skooper-pr/SentinelAC/actions/workflows/build.yml)
[![Platform](https://img.shields.io/badge/Platform-PaperMC%201.20%2B-blue.svg)](https://papermc.io)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-v1.0.0-gold.svg)](https://github.com/Skooper-pr/SentinelAC/releases)

---

## 1. What SentinelAC Does

**SentinelAC** is a production-grade, server-authoritative Minecraft anti-cheat plugin engineered specifically for modern PaperMC servers. It replaces traditional fragile heuristics with deterministic vanilla physics simulation and 3D geometric raycasting to detect movement and combat violations as mathematical proofs. Simultaneously, a statistical behavior engine evaluates click interval distributions, damped neuromuscular aim curves, and spectral FFT periodicity to catch subtle macros and aimbots. All evidence streams into a central **Sequential Probability Ratio Test (SPRT)** fusion engine that calculates calibrated, false-positive-bounded confidence scores ($\Lambda$) per player before triggering automated actions.

---

## 2. How to Get the Built `.jar`

You can obtain the ready-to-run shaded plugin jar (`SentinelAC-1.0.0.jar`) in two ways:

1. **From GitHub Releases (Recommended):**
   Navigate to the [Releases](https://github.com/Skooper-pr/SentinelAC/releases) tab on the repository and download `SentinelAC-1.0.0.jar` attached to the latest release (`v1.0.0`).
2. **From GitHub Actions CI Artifacts:**
   Go to the [Actions](https://github.com/Skooper-pr/SentinelAC/actions) tab, click the latest workflow run on the `main` branch, scroll down to the **Artifacts** section, and download `SentinelAC-jar.zip` (contains the shaded plugin `.jar`).
3. **Build from Source:**
   Clone the repository and compile using Maven with Java 17+:
   ```bash
   git clone https://github.com/Skooper-pr/SentinelAC.git
   cd SentinelAC
   mvn clean package
   ```
   The compiled plugin will be located at `target/SentinelAC-1.0.0.jar`.

---

## 3. How to Install

1. Ensure your server is running **PaperMC 1.20+** (or compatible fork) on **Java 17 or higher**.
2. Copy `SentinelAC-1.0.0.jar` into your server's `plugins/` directory.
3. Start or restart your Paper server.
4. Verify proper initialization in your server console. Look for the exact startup log line:
   ```text
   [INFO] [SentinelAC]  SentinelAC v1.0.0 loaded successfully!
   [INFO] [SentinelAC]  Deterministic Physics & Wald SPRT Fusion Engine Active.
   ```
   The plugin will automatically generate its data directory (`plugins/SentinelAC/`), configuration file (`config.yml`), and SQLite database (`sentinelac.db`).

---

## 4. How to Test End-to-End

To verify that SentinelAC is actively protecting your server:

1. **Verify Movement & Fly Detection:**
   - Log into your test server with an account that has permission to move freely, or test on a dummy player.
   - Using a cheat client (or custom velocity injection), enable Fly or abnormal Speed (e.g. 5x vanilla speed) and move across the terrain.
   - Staff with the `sentinelac.admin` permission will receive an in-game alert:
     ```text
     [SentinelAC] PlayerName flagged for MOVEMENT (Λ=6.90, Flag #1)
     ```
   - In console or chat, run:
     ```bash
     /sentinelac flagged
     ```
     You will see the player listed under currently flagged players.

2. **Verify Combat & Reach Detection:**
   - Stand further than 3.05 blocks away from another player or mob.
   - Attempt to hit the target beyond maximum reach, or face 90 degrees away from the target while attacking (Impossible Hit/Angle).
   - The attack is flagged geometrically, the delta is recorded, and a combat alert is dispatched.

3. **Verify Behavioral Autoclicker & Aim Detection:**
   - Turn on an autoclicker with a fixed interval (e.g. exactly 100ms or 10 CPS flat).
   - Attack or swing at an entity for 10–15 seconds.
   - Inspect the player's live SPRT confidence accumulation:
     ```bash
     /sentinelac status PlayerName
     ```
     Observe the `CLICK` category's $\Lambda$ rising toward the Wald upper threshold ($\sim 6.90$) before auto-flagging.

---

## 5. Staff Commands & Permissions

All administrative commands require the `sentinelac.admin` permission node (granted to operators by default).

| Command | Description | Example |
|---|---|---|
| `/sentinelac status <player>` | Inspects live SPRT confidence log-likelihood ratios ($\Lambda$) across all categories for a player. | `/sentinelac status Steve` |
| `/sentinelac flagged` | Displays a list of all currently flagged players and their trigger categories. | `/sentinelac flagged` |
| `/sentinelac verdict <flagId> <upheld\|overturned>` | Records a staff review outcome for a flag into SQLite, generating labeled ground-truth training data. | `/sentinelac verdict 1 upheld` |
| `/sentinelac reload` | Hot-reloads `config.yml` without restarting the server. | `/sentinelac reload` |

---

## 6. Configuration Guide (`config.yml`)

Every option in `plugins/SentinelAC/config.yml` is documented below:

```yaml
# --- Check Toggles ---
checks:
  movement:
    enabled: true               # Enables deterministic vanilla movement physics simulation.
    epsilon: 0.005              # Floating-point distance tolerance (in blocks) for position deltas.
    default_grace_ticks: 10     # Ticks to absorb network lag, server teleports, and knockback velocity.

  combat:
    enabled: true               # Enables 3D geometric ray-AABB intersection hit verification.
    survival_reach: 3.00        # Maximum reach distance permitted in Survival/Adventure mode (blocks).
    creative_reach: 5.00        # Maximum reach distance permitted in Creative mode (blocks).
    reach_tolerance: 0.05       # Floating-point tolerance buffer for reach measurements (blocks).
    hitbox_expansion: 0.10      # Server-side AABB buffer absorbing client network latency and ping (blocks).
    cancel_violations: false    # If true, directly cancels damage events that fail geometric validation.

  behavior:
    click_timing:
      enabled: true             # Evaluates click intervals via Log-Normal distribution and Kolmogorov-Smirnov test.
    aim_response:
      enabled: true             # Models aim trajectories against damped neuromuscular response with a 150ms reaction floor.
    periodicity:
      enabled: true             # Runs Fast Fourier Transform (FFT) & autocorrelation to identify repeating macros.

# --- Fusion Engine (SPRT) Parameters ---
fusion:
  alpha: 0.001                  # Target Type I error rate (false-positive: 0.1% target -> Upper bound ~6.898).
  beta: 0.010                   # Target Type II error rate (false-negative: 1.0% target -> Lower bound ~-4.604).

# --- Alerting & Webhooks ---
alerts:
  in_game_broadcast: true       # Broadcasts detection flag alerts to online staff with sentinelac.admin.
  discord:
    enabled: false              # Enables asynchronous Discord webhook notifications (free, pure Java).
    webhook_url: ""             # Target Discord channel webhook URL.

# --- Logging & Verbosity ---
logging:
  verbosity: INFO               # Logging level: DEBUG, INFO, WARN, SEVERE.
```

---

## 7. Known Limitations

> [!WARNING]
> **No anti-cheat can detect 100% of all cheat clients.** Private, actively-maintained clients running in "legit mode" utilize intentional human-like noise injection, random bezier spline curves, and variable micro-delays designed specifically to blend into human statistical baselines. SentinelAC is designed as an evidence-fusion and data-collection platform: it provides mathematically sound, false-positive-bounded verdicts, and records ground-truth staff verdicts into local SQLite storage so server administrators can tune thresholds over time against empirical telemetry rather than static signatures.

---

## 8. Roadmap / Not Yet Implemented

The following capabilities are deliberately planned for subsequent iterations and are cleanly supported by existing seams in the architecture:

- **Pluggable Machine Learning Scoring:** The `BehaviorScorer` interface ships with `RuleBasedBehaviorScorer` today; future releases will introduce an offline-trained neural / gradient-boosted model consuming labeled records from the `verdicts` table.
- **Client Protocol Translation Integration:** Direct hooks for ViaVersion / ViaBackwards to account for legacy 1.8 bounding boxes and combat mechanics when running multi-version networks.
- **Packet-Level Injection (PacketEvents / ProtocolLib):** Optional packet-level listener layer for micro-tick sub-packet analysis before Bukkit event dispatch.
- **Automated Discord Webhook Graph Visuals:** Attaching graphical aim residual plots and click histogram charts to Discord webhook alerts.
