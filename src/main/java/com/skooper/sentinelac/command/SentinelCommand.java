package com.skooper.sentinelac.command;

import com.skooper.sentinelac.SentinelAC;
import com.skooper.sentinelac.fusion.engine.FusionEngine;
import com.skooper.sentinelac.fusion.model.CheatCategory;
import com.skooper.sentinelac.fusion.model.PlayerFusionProfile;
import com.skooper.sentinelac.storage.FlagRecord;
import com.skooper.sentinelac.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Administrative command handler for /sentinelac (status, flagged, verdict, reload).
 */
public final class SentinelCommand implements CommandExecutor, TabCompleter {

    private final SentinelAC plugin;
    private final FusionEngine fusionEngine;
    private final StorageManager storageManager;

    public SentinelCommand(SentinelAC plugin, FusionEngine fusionEngine, StorageManager storageManager) {
        this.plugin = plugin;
        this.fusionEngine = fusionEngine;
        this.storageManager = storageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("sentinelac.admin")) {
            sender.sendMessage(Component.text("You do not have permission to execute this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status" -> handleStatus(sender, args);
            case "flagged" -> handleFlagged(sender);
            case "verdict" -> handleVerdict(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /sentinelac status <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        UUID targetUuid = target != null ? target.getUniqueId() : null;

        if (targetUuid == null) {
            // Attempt offline lookup from storage
            var recent = storageManager.getRecentFlags(100);
            for (FlagRecord r : recent) {
                if (r.getPlayerName().equalsIgnoreCase(targetName)) {
                    targetUuid = r.getPlayerUuid();
                    break;
                }
            }
        }

        if (targetUuid == null) {
            sender.sendMessage(Component.text("Player not found or has no active session: " + targetName, NamedTextColor.RED));
            return;
        }

        PlayerFusionProfile profile = fusionEngine.getProfile(targetUuid);
        double upper = fusionEngine.getBoundary().getUpperBound();
        double lower = fusionEngine.getBoundary().getLowerBound();

        sender.sendMessage(Component.text("---- SentinelAC Telemetry: " + targetName + " ----", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text(String.format("SPRT Bounds: Lower=%.3f, Upper=%.3f", lower, upper), NamedTextColor.GRAY));

        for (CheatCategory cat : CheatCategory.values()) {
            double lambda = profile.getLambda(cat);
            boolean flagged = profile.isFlagged(cat);
            NamedTextColor color = flagged ? NamedTextColor.RED : (lambda > 2.0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN);

            String status = flagged ? "[FLAGGED]" : "[WATCHING]";
            sender.sendMessage(Component.text(String.format("  • %-10s : Λ = %6.3f  %s", cat.name(), lambda, status), color));
        }
    }

    private void handleFlagged(CommandSender sender) {
        Set<UUID> flagged = fusionEngine.getFlaggedPlayers();
        if (flagged.isEmpty()) {
            sender.sendMessage(Component.text("No players currently flagged by SentinelAC.", NamedTextColor.GREEN));
            return;
        }

        sender.sendMessage(Component.text("---- Currently Flagged Players (" + flagged.size() + ") ----", NamedTextColor.RED, TextDecoration.BOLD));
        for (UUID uuid : flagged) {
            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : uuid.toString();
            PlayerFusionProfile profile = fusionEngine.getProfile(uuid);

            List<String> flaggedCategories = new ArrayList<>();
            for (CheatCategory cat : CheatCategory.values()) {
                if (profile.isFlagged(cat)) {
                    flaggedCategories.add(cat.name() + " (Λ=" + String.format("%.2f", profile.getLambda(cat)) + ")");
                }
            }

            sender.sendMessage(Component.text("  • " + name + " -> " + String.join(", ", flaggedCategories), NamedTextColor.YELLOW));
        }
    }

    private void handleVerdict(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /sentinelac verdict <flagId> <upheld|overturned>", NamedTextColor.RED));
            return;
        }

        int flagId;
        try {
            flagId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid flag ID: " + args[1], NamedTextColor.RED));
            return;
        }

        String outcome = args[2].toLowerCase();
        if (!outcome.equals("upheld") && !outcome.equals("overturned")) {
            sender.sendMessage(Component.text("Outcome must be either 'upheld' or 'overturned'", NamedTextColor.RED));
            return;
        }

        Optional<FlagRecord> flagOpt = storageManager.getFlag(flagId);
        if (flagOpt.isEmpty()) {
            sender.sendMessage(Component.text("Flag ID #" + flagId + " not found in database.", NamedTextColor.RED));
            return;
        }

        String staff = sender.getName();
        boolean saved = storageManager.saveVerdict(flagId, outcome, staff);
        if (saved) {
            FlagRecord flag = flagOpt.get();
            sender.sendMessage(Component.text(String.format("Successfully recorded verdict '%s' for flag #%d (%s - %s) by %s.",
                    outcome, flagId, flag.getPlayerName(), flag.getCategory(), staff), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Failed to save verdict to database.", NamedTextColor.RED));
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfigValues();
        sender.sendMessage(Component.text("SentinelAC configuration reloaded successfully.", NamedTextColor.GREEN));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("---- SentinelAC Admin Commands ----", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/sentinelac status <player> ", NamedTextColor.YELLOW)
                .append(Component.text("- View live SPRT confidence lambda per category", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/sentinelac flagged ", NamedTextColor.YELLOW)
                .append(Component.text("- List all currently flagged players", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/sentinelac verdict <flagId> <upheld|overturned> ", NamedTextColor.YELLOW)
                .append(Component.text("- Label a flag outcome for ML training", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/sentinelac reload ", NamedTextColor.YELLOW)
                .append(Component.text("- Reload config.yml settings", NamedTextColor.GRAY)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("sentinelac.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filterPrefix(List.of("status", "flagged", "verdict", "reload"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            return filterPrefix(names, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("verdict")) {
            return filterPrefix(List.of("upheld", "overturned"), args[2]);
        }

        return List.of();
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
