# Prompt for Opus 5.5: rebuild SentinelAC into a serious anti-cheat

Paste everything below the line into a fresh session that has this repository checked out.

---

You are taking over SentinelAC, a Minecraft server anti-cheat plugin in this repository. The owner wants it to run on servers from 1.8 through the latest release and to catch both blatant cheats and "ghost" clients such as Vape, whose users play with low, human-looking settings (aim assist, an autoclicker with jitter, 3.1–3.3 block reach, 80–95% velocity, sprint reset, backtrack, legit scaffold). The current code does not do that. Your job is to rebuild it on a foundation that can, and to be honest at every step about what has and has not been verified.

This is a long project. Work in the phases below, commit after each one, and keep `PROGRESS.md` truthful. The existing `PROGRESS.md` and `README.md` call the plugin "production-quality" and "completed"; that is not accurate and you should rewrite those claims as you go.

## Where the code stands today

About 3,500 lines of Java in `src/main/java/com/skooper/sentinelac`, built with Maven against `paper-api 1.20.4`, Java 17, `api-version: '1.20'`. Read all of it before changing anything. The problems that matter:

**Platform.** It only loads on 1.20+. Every detector listens to Bukkit events (`PlayerMoveEvent`, `EntityDamageByEntityEvent`, `PlayerAnimationEvent`). Those events are throttled, merged, and fired after the server has already processed the packet, so the plugin never sees what the client actually sent or in what order. There is no packet layer, no latency compensation, and no replica of the world as each client sees it.

**Movement** (`movement/listener/MovementListener.java`, `movement/check/MovementCheck.java`, `movement/simulator/PhysicsSimulator.java`).
- `resolveInput` derives forward/strafe from the displacement the player reported, then the simulator is asked whether that displacement is legal. The check is circular: a speed hack reports a larger displacement, which is read as a larger input.
- `onGround` comes from `player.isOnGround()`, which is the client's own claim. NoFall and ground spoof pass untouched.
- There is no block collision at all. The simulator integrates velocity in free space, so walls, steps, slabs, stairs, and ceilings do not exist for it.
- One sample over `epsilon = 0.005` calls `fusionEngine.addDeterministicViolation`, which flags immediately. On a violation the stored state is not advanced, so the next tick is compared against stale state and fails too. Expect legitimate players to be flagged within seconds of normal play.
- Any velocity or teleport grants 10–15 ticks in which nothing is checked. Anti-knockback lives entirely inside that window and is therefore undetectable.
- No setback or other mitigation exists; the plugin only records a flag.

**Combat** (`combat/listener/CombatListener.java`, `combat/engine/*`).
- The ray starts at the server's copy of the attacker and ends at the server's copy of the target, rewound by `player.getPing() / 50` ticks. `getPing()` is a smoothed keepalive average that a client can inflate at will, and the rewind is capped only at one second, so a cheater can buy reach by faking lag.
- Target history is recorded only in `PlayerMoveEvent`, so mobs and standing players have no history.
- `EntityDamageByEntityEvent` also fires for sweep attacks and thorns, where the attacker is not looking at the victim. Those will be reported as impossible hits.
- One failed ray is an instant flag.

**Behavior** (`behavior/listener/BehaviorListener.java`, `behavior/analysis/*`).
- Click intervals are measured with `System.currentTimeMillis()` inside an event that runs on the 50 ms server tick. Every interval is a multiple of roughly 50 ms plus scheduler noise. The coefficient-of-variation thresholds, the log-normal fit, and the KS test are therefore measuring the server tick, not the player's hand.
- `ARM_SWING` also fires while mining, so digging is scored as clicking.
- `evaluateIfReady` runs on every swing and every rotation and re-scores the same 40-sample sliding window each time. The same samples are added to the SPRT dozens of times. The test assumes independent observations, so its stated α = 0.001 means nothing.
- Every log-likelihood value (3.5, 2.2, -0.8, …) is a hand-picked constant, not a ratio of measured distributions.
- Yaw deltas are not wrapped at ±180°, and the aim model has no notion of mouse sensitivity.

**Fusion and plumbing** (`fusion/engine/FusionEngine.java`, `SentinelAC.java`, `storage/StorageManager.java`).
- A flag fires once per category and never decays or clears. Fusion profiles are not removed on quit.
- `storageManager.saveFlag` runs synchronously on the main thread from `handleFlagEvent`.
- There is no punishment path, no per-check configuration, no exemption for Bedrock players, creative flight, or vehicles.

The SPRT idea and the SQLite verdict table are worth keeping. Most of the rest should be replaced rather than patched. Decide file by file and say what you kept.

## What "strong" means here

Set expectations correctly, for yourself and in the docs. A server-side anti-cheat can prove physical impossibility (movement that vanilla cannot produce, hits outside reach, packets in an order the vanilla client never sends). It cannot prove intent. A closet cheater whose settings sit inside the human range is separable from a human only statistically and only over time. So the design goal is to shrink the advantage a cheat can give before it becomes detectable, and to make every remaining advantage cost evidence. Reach of 3.05 should be provable. Velocity of 95% should be provable. Aim assist should break a mathematical invariant of real mouse input. Do not promise 100% detection anywhere, and do not ship a check whose false-positive behavior you have not measured.

False positives matter more than missed detections. A legitimate player punished by the plugin is the worst outcome, and it is the failure mode of the current code.

## Foundation (build this first; everything depends on it)

1. **Packet layer.** Move all detection onto raw packets using PacketEvents 2.x, which covers 1.8 through current versions. Check its current documentation and release for the API rather than relying on memory, since it changes between versions. Keep Bukkit API use to the minimum needed for commands, config, and scheduling, and compile that part against an API surface that exists on 1.8.8. Decide the Java bytecode target deliberately and document it; if you require Java 17 on 1.8 servers, say so in the README.

2. **Per-player state on the network thread.** One data object per connection, mutated only from that player's netty thread, holding everything the checks need. No Bukkit calls on that thread.

3. **Latency compensation by transaction sandwiching.** Before and after every server-to-client packet that changes what the client simulates (velocity, explosions, teleports, entity spawns and movement, block changes, chunk data, abilities, effects, attributes, metadata, vehicle changes), send a transaction (window confirmation before 1.17, ping packet after). When the client answers, you know exactly which state it had applied when it produced each movement packet. Between the two answers the state is uncertain, so checks must accept either. This replaces `getPing()` and every grace window in the current code. A client that withholds or reorders transaction answers must be detected and eventually timed out, because withholding them is how fake lag, backtrack, and blink work.

4. **Compensated world.** A per-player copy of blocks as that client sees them, fed from chunk and block-change packets and gated by the transactions above. Include version-correct collision shapes for every block.

5. **Client version awareness.** With ViaVersion, a 1.8 client on a 1.21 server runs 1.8 physics, 1.8 hitboxes, and 1.8 packet ordering. Every physics and combat rule must key off the client's protocol version, not the server's.

## Movement: a prediction engine

Replace the simulator with a faithful re-implementation of vanilla client movement that includes collision. For each movement packet, enumerate the inputs the client could have pressed (forward and strafe in {-1, 0, 1}, jump, sprint, sneak, item use, and on 1.21.2+ the inputs the client now reports), run one vanilla tick for each from the last confirmed state, and take the candidate closest to the reported position. The distance to that candidate is the offset. A legitimate client's offset is near floating-point error. Derive the physics from the vanilla client itself for each version family; guessing constants is how the current simulator went wrong.

You need to handle, per client version: the pre-1.18.2 behavior where movements under 0.03 blocks are not sent, the periodic position resend, step-up, sneaking edges, swimming and the 1.13 water changes, lava, bubble columns, climbing, scaffolding, powder snow, cobwebs, berry bushes, slime and honey, ice, soul sand and soul speed, depth strider, dolphin's grace, levitation, slow falling, jump boost, speed and slowness, the 1.20.5+ attribute system (gravity, step height, scale, jump strength, movement efficiency and the rest), elytra and firework boosts, riptide, pistons and shulker boxes pushing the player, wind charges, the mace, entity collision pushing, and vehicles (boats, horses, pigs, striders, camels), which are client-authoritative and need their own prediction.

Knockback and explosions become additional candidate starting velocities bracketed by their transactions. If the client's movement matches the "no knockback applied" candidate or a scaled-down one after the confirming transaction, that is an anti-knockback detection, measurable down to a few percent.

From this one engine you get speed, fly, step, jesus, no-slow, phase, no-fall and ground spoof (ground state is computed from collision, never read from the client), velocity, strafe, and fast-ladder without writing separate heuristics. Add a timer check that uses movement packets as the client's clock and tracks its balance against real time, and a set of bad-packet checks for values and sequences the vanilla client cannot produce.

When the offset exceeds tolerance, accumulate it in a decaying buffer and, past a threshold, set the player back to the last valid position. Setbacks are the mitigation: they remove the benefit of a movement cheat without punishing anyone on a single sample.

## Combat

Validate on the interact-entity packet, not on damage events.

- **Reach and hitbox.** The attacker's eye position comes from their own movement packets (check the positions on both sides of the attack, because 1.8 and later clients order look and attack differently). The target's position is the one that attacker's client was shown, reconstructed from the relative-move and teleport packets sent to them, including client-side interpolation steps, and bounded by the transaction window. That gives a small box of positions the target could occupy on the attacker's screen. Reach is the minimum distance from eye to that box along the look ray. Use 3.0 blocks, the 0.1 hitbox margin that 1.8 clients have, and no other slack. Target: prove 3.05.
- **Wall hits**, using the compensated world.
- **Multi-target and tick-order checks**: attacks on several entities within one client tick, attacks without the swing a given version always sends, and action packets that arrive in a position within the tick that vanilla never uses. These "post" and ordering checks are cheap and catch a lot of ghost-client modules.
- **Sprint and block state**: keep-sprint, impossible sprint resets, auto-block and no-slow while attacking on 1.8.
- **Backtrack, lag range, fake lag, blink**: these delay inbound or outbound packets. Look for transaction answers arriving in bursts or drifting against the movement-packet clock while the player keeps fighting.

## Aim

Real mouse input has a property aim assists break. The vanilla client converts mouse counts to rotation through a sensitivity-derived step, so consecutive yaw and pitch deltas share a greatest common divisor tied to the player's sensitivity setting. Recover that divisor per player, then test each rotation against it. Aim assist, aimbot, and silent rotations add deltas that are not on that grid. Exempt cinematic camera and zoom, which you can recognise by their smoothing signature, and very small deltas where the estimate is unstable.

Layer statistical checks on top, each with its own measured false-positive rate: rotation snaps followed by an attack, constant or repeating deltas, yaw motion with implausibly still pitch, tracking error that stays inside a fraction of the hitbox while the target strafes, cross-correlation between target movement and attacker rotation at lags below human reaction time, and rotation that changes only on ticks where an attack happens.

## Clicking

The server cannot see millisecond click timing, so stop pretending it can. Use movement packets as the client's tick clock and count swings per client tick, excluding ticks where the player is digging or placing. Analyse the resulting sequence: clicks per second, variance, skewness, kurtosis, entropy, run lengths, share of repeated values, double-click patterns. On 1.9+ combat, attack cooldown makes high click rates irrelevant, so weight these checks by client version.

## World interaction and inventory

Placement validation against the compensated world (the look ray must hit the clicked face, the face must be reachable and visible), fast-place, air-place, scaffold rotation and sneak patterns, fast-break and nuker against computed break times, and inventory actions that vanilla forbids (clicking while moving or sprinting on the versions where that is impossible, click cadence in chest stealers and auto-armor).

## Fusion and punishment

Keep the SPRT idea but make it valid. Feed it non-overlapping windows so observations are independent. Replace the hand-picked constants with log-likelihood ratios computed from recorded distributions of legitimate play and of each cheat. Let evidence decay. Keep physically provable checks out of the SPRT; give them violation buffers and setbacks instead.

Punishment is a configurable ladder per check: alert staff, then cancel or set back, then kick, then run a configured command. No automatic ban from a single statistical check. Give staff a verbose alert mode and a way to see the numbers behind a flag.

## Things the plugin must get right around the edges

Bedrock players through Geyser/Floodgate (exempt them from checks that assume Java client behavior), creative and spectator, plugin-applied velocity and teleports (they arrive as packets and are handled by the transaction system, not by grace windows), server lag (never punish the player for a slow server tick), Folia if it is cheap to support, async storage, cleanup of all per-player state on disconnect, a public API with cancellable flag events, and a per-check config with enable, thresholds, and punishment.

## How to verify

You cannot play Minecraft, so build the tools that stand in for it and state plainly what they cover.

- Unit tests for the physics engine against trajectories produced by the vanilla code for each version family.
- A packet recorder and a replay harness, so that a recorded session can be run through the checks offline. The owner can then record real legitimate play and real cheat clients on a test server and send you the files. Ask for those recordings; they are the only real calibration data.
- If the environment allows, an integration test that starts a real server and connects a protocol bot that moves and fights legitimately, with zero flags as the pass condition, plus bot variants that cheat in specific ways.
- A regression gate: every recorded legitimate session must produce zero setbacks and zero flags before a phase is called done.

Report results as they are. If a check is implemented but has only been unit-tested, write that. If something does not work on a given version, write that. Do not describe anything as production-ready until it has run against real clients.

## Order of work

1. Build and packaging for 1.8–latest, packet layer, per-player state, transactions, compensated world, client-version handling. Remove the Bukkit-event detectors.
2. Prediction engine with collision, for one version family first, then the others. Setbacks. Timer and bad packets.
3. Reach, hitbox, wall and ordering checks.
4. Velocity detection through the prediction engine.
5. Aim (sensitivity divisor first, statistics second) and click analysis.
6. Placement, breaking, inventory.
7. Fake-lag family.
8. Fusion rework, punishment ladder, config, commands, alerts, storage, API.
9. Recorder, replay harness, calibration against the owner's recordings, documentation.

Open-source anti-cheats such as Grim use several of these techniques and are worth reading to understand them. Check their licenses and this repository's license before reusing any code; understand the technique and write your own implementation unless the licenses are compatible and the owner agrees.

Develop on the branch you are given, commit at the end of each phase with a message that says what now works and what is still unverified, and stop to ask the owner only when a decision is theirs to make, such as the minimum Java version, the license, or the default punishments.
