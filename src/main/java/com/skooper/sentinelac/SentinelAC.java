package com.skooper.sentinelac;

import com.skooper.sentinelac.behavior.listener.BehaviorListener;
import com.skooper.sentinelac.behavior.scorer.BehaviorScorer;
import com.skooper.sentinelac.behavior.scorer.RuleBasedBehaviorScorer;
import com.skooper.sentinelac.combat.engine.CombatEngine;
import com.skooper.sentinelac.combat.listener.CombatListener;
import com.skooper.sentinelac.command.SentinelCommand;
import com.skooper.sentinelac.fusion.engine.FusionEngine;
import com.skooper.sentinelac.fusion.model.CheatCategory;
import com.skooper.sentinelac.fusion.model.FusionFlagEvent;
import com.skooper.sentinelac.movement.check.MovementCheck;
import com.skooper.sentinelac.movement.listener.MovementListener;
import com.skooper.sentinelac.reporting.DiscordWebhookNotifier;
import com.skooper.sentinelac.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * SentinelAC - Production-quality server-authoritative Minecraft Anti-Cheat.
 */
public final class SentinelAC extends JavaPlugin {

    private static SentinelAC instance;
    private Logger logger;

    private StorageManager storageManager;
    private DiscordWebhookNotifier discordNotifier;
    private FusionEngine fusionEngine;
    private MovementCheck movementCheck;
    private CombatEngine combatEngine;
    private BehaviorScorer behaviorScorer;
    private CombatListener combatListener;

    @Override
    public void onEnable() {
        instance = this;
        this.logger = getLogger();

        // 1. Configuration
        saveDefaultConfig();

        // 2. Storage
        File dbFile = new File(getDataFolder(), "sentinelac.db");
        this.storageManager = new StorageManager(dbFile, logger);

        // 3. Reporting / Discord
        this.discordNotifier = new DiscordWebhookNotifier(logger);

        // 4. Engines
        this.fusionEngine = new FusionEngine();
        this.movementCheck = new MovementCheck();
        this.combatEngine = new CombatEngine();
        this.behaviorScorer = new RuleBasedBehaviorScorer();

        // Load config parameters into engines
        reloadConfigValues();

        // 5. Connect Fusion Engine Flag Listener
        fusionEngine.registerFlagListener(this::handleFlagEvent);

        // 6. Register Paper Listeners
        PluginManager pm = getServer().getPluginManager();

        // Movement Listener -> Deterministic Fusion Shortcut
        MovementListener movementListener = new MovementListener(movementCheck, event -> {
            if (getConfig().getBoolean("checks.movement.enabled", true)) {
                fusionEngine.addDeterministicViolation(
                        event.player().getUniqueId(),
                        CheatCategory.MOVEMENT,
                        event.violation().getSummary()
                );
            }
        });
        pm.registerEvents(movementListener, this);

        // Combat Listener -> Deterministic Fusion Shortcut
        this.combatListener = new CombatListener(combatEngine, event -> {
            if (getConfig().getBoolean("checks.combat.enabled", true)) {
                fusionEngine.addDeterministicViolation(
                        event.attacker().getUniqueId(),
                        CheatCategory.COMBAT,
                        event.violation().getSummary()
                );
            }
        });
        pm.registerEvents(combatListener, this);

        // Behavior Listener -> Probabilistic SPRT Accumulation
        BehaviorListener behaviorListener = new BehaviorListener(behaviorScorer, event -> {
            FileConfiguration cfg = getConfig();
            var score = event.score();
            var uuid = event.player().getUniqueId();

            if (cfg.getBoolean("checks.behavior.click_timing.enabled", true) && Math.abs(score.getClickLogLikelihood()) > 1.0E-5) {
                fusionEngine.addEvidence(uuid, CheatCategory.CLICK, score.getClickLogLikelihood(), score.getSummary());
            }
            if (cfg.getBoolean("checks.behavior.aim_response.enabled", true) && Math.abs(score.getAimLogLikelihood()) > 1.0E-5) {
                fusionEngine.addEvidence(uuid, CheatCategory.AIM, score.getAimLogLikelihood(), score.getSummary());
            }
            if (cfg.getBoolean("checks.behavior.periodicity.enabled", true) && Math.abs(score.getPeriodicityLogLikelihood()) > 1.0E-5) {
                fusionEngine.addEvidence(uuid, CheatCategory.CLICK, score.getPeriodicityLogLikelihood(), score.getSummary());
            }
        });
        pm.registerEvents(behaviorListener, this);

        // Schedule server tick counter for lag compensator
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (combatListener != null) {
                combatListener.incrementTick();
            }
        }, 1L, 1L);

        // 7. Register Commands
        SentinelCommand commandHandler = new SentinelCommand(this, fusionEngine, storageManager);
        var cmd = Objects.requireNonNull(getCommand("sentinelac"), "Command sentinelac must be registered in plugin.yml");
        cmd.setExecutor(commandHandler);
        cmd.setTabCompleter(commandHandler);

        logger.info("==========================================================");
        logger.info(" SentinelAC v" + getDescription().getVersion() + " loaded successfully!");
        logger.info(" Deterministic Physics & Wald SPRT Fusion Engine Active.");
        logger.info(" Database: SQLite at " + dbFile.getAbsolutePath());
        logger.info(" Discord Alerts: " + (discordNotifier.isEnabled() ? "ENABLED" : "DISABLED"));
        logger.info("==========================================================");
    }

    @Override
    public void onDisable() {
        if (storageManager != null) {
            storageManager.close();
        }
        logger.info("SentinelAC disabled.");
        instance = null;
    }

    public void reloadConfigValues() {
        reloadConfig();
        FileConfiguration config = getConfig();

        // Fusion Engine boundaries
        double alpha = config.getDouble("fusion.alpha", 0.001);
        double beta = config.getDouble("fusion.beta", 0.010);
        fusionEngine.setAlphaBeta(alpha, beta);

        // Movement tolerances
        double epsilon = config.getDouble("checks.movement.epsilon", 0.005);
        int grace = config.getInt("checks.movement.default_grace_ticks", 10);
        movementCheck.setEpsilon(epsilon);
        movementCheck.setDefaultGraceTicks(grace);

        // Combat tolerances
        double survReach = config.getDouble("checks.combat.survival_reach", 3.0);
        double creatReach = config.getDouble("checks.combat.creative_reach", 5.0);
        double reachTol = config.getDouble("checks.combat.reach_tolerance", 0.05);
        double hitboxExp = config.getDouble("checks.combat.hitbox_expansion", 0.10);
        boolean cancelDmg = config.getBoolean("checks.combat.cancel_violations", false);

        combatEngine.setSurvivalMaxReach(survReach);
        combatEngine.setCreativeMaxReach(creatReach);
        combatEngine.setReachTolerance(reachTol);
        combatEngine.setHitboxExpansion(hitboxExp);
        if (combatListener != null) {
            combatListener.setCancelViolations(cancelDmg);
        }

        // Discord notifier
        boolean discordEnabled = config.getBoolean("alerts.discord.enabled", false);
        String discordUrl = config.getString("alerts.discord.webhook_url", "");
        discordNotifier.configure(discordEnabled, discordUrl);

        logger.info("SentinelAC configuration refreshed: SPRT Bounds [A=" +
                String.format("%.3f", fusionEngine.getBoundary().getUpperBound()) +
                ", B=" + String.format("%.3f", fusionEngine.getBoundary().getLowerBound()) + "]");
    }

    private void handleFlagEvent(FusionFlagEvent event) {
        Player player = Bukkit.getPlayer(event.getPlayerId());
        String playerName = player != null ? player.getName() : event.getPlayerId().toString();

        // 1. Persist to SQLite
        int flagId = storageManager.saveFlag(
                event.getPlayerId(),
                playerName,
                event.getCategory().name(),
                event.getLambdaAtFlag(),
                event.getSummary()
        );

        // 2. Broadcast in-game alert to staff
        if (getConfig().getBoolean("alerts.in_game_broadcast", true)) {
            Component alert = Component.text("[SentinelAC] ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .append(Component.text(playerName, NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(" flagged for ", NamedTextColor.GRAY))
                    .append(Component.text(event.getCategory().name(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(String.format(" (Λ=%.2f, Flag #%d)", event.getLambdaAtFlag(), flagId), NamedTextColor.DARK_GRAY));

            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("sentinelac.admin")) {
                    staff.sendMessage(alert);
                }
            }
            logger.warning(String.format("FLAG #%d: Player %s flagged for %s (Lambda=%.3f): %s",
                    flagId, playerName, event.getCategory().name(), event.getLambdaAtFlag(), event.getSummary()));
        }

        // 3. Optional Discord Webhook Alert
        discordNotifier.sendAlertAsync(playerName, event, flagId);
    }

    public static SentinelAC getInstance() {
        return instance;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public FusionEngine getFusionEngine() {
        return fusionEngine;
    }

    public MovementCheck getMovementCheck() {
        return movementCheck;
    }

    public CombatEngine getCombatEngine() {
        return combatEngine;
    }

    public BehaviorScorer getBehaviorScorer() {
        return behaviorScorer;
    }

    public DiscordWebhookNotifier getDiscordNotifier() {
        return discordNotifier;
    }
}
