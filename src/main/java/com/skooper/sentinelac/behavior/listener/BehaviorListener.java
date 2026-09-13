package com.skooper.sentinelac.behavior.listener;

import com.skooper.sentinelac.behavior.model.AimSample;
import com.skooper.sentinelac.behavior.model.BehaviorScore;
import com.skooper.sentinelac.behavior.model.ClickSample;
import com.skooper.sentinelac.behavior.model.PlayerEvidenceWindow;
import com.skooper.sentinelac.behavior.scorer.BehaviorScorer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Paper event listener collecting telemetry on arm animations and aim trajectories,
 * dispatching evaluation windows to the BehaviorScorer.
 */
public final class BehaviorListener implements Listener {

    private static final int MAX_CLICK_HISTORY = 40;
    private static final int MAX_AIM_HISTORY = 40;

    private final BehaviorScorer scorer;
    private final Consumer<BehaviorScoreEvent> scoreConsumer;

    private final Map<UUID, Deque<ClickSample>> clickHistory = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<AimSample>> aimHistory = new ConcurrentHashMap<>();

    public record BehaviorScoreEvent(Player player, BehaviorScore score) {}

    public BehaviorListener(BehaviorScorer scorer, Consumer<BehaviorScoreEvent> scoreConsumer) {
        this.scorer = scorer;
        this.scoreConsumer = scoreConsumer;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        clickHistory.remove(uuid);
        lastClickTime.remove(uuid);
        aimHistory.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long last = lastClickTime.put(uuid, now);
        if (last != null) {
            double interval = now - last;
            if (interval > 10.0 && interval < 1000.0) { // Valid combat click interval
                Deque<ClickSample> deque = clickHistory.computeIfAbsent(uuid, k -> new ArrayDeque<>(MAX_CLICK_HISTORY));
                synchronized (deque) {
                    if (deque.size() >= MAX_CLICK_HISTORY) deque.pollFirst();
                    deque.addLast(new ClickSample(now, interval));
                }
                evaluateIfReady(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        float yawDelta = Math.abs(to.getYaw() - from.getYaw());
        float pitchDelta = Math.abs(to.getPitch() - from.getPitch());

        if (yawDelta < 1.0E-4 && pitchDelta < 1.0E-4) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        double angleError = calculateAngleToNearestTarget(player);
        boolean hasTarget = angleError < 45.0;

        Deque<AimSample> deque = aimHistory.computeIfAbsent(uuid, k -> new ArrayDeque<>(MAX_AIM_HISTORY));
        synchronized (deque) {
            if (deque.size() >= MAX_AIM_HISTORY) deque.pollFirst();
            deque.addLast(new AimSample(now, yawDelta, pitchDelta, angleError, hasTarget));
        }

        evaluateIfReady(player);
    }

    private void evaluateIfReady(Player player) {
        UUID uuid = player.getUniqueId();
        Deque<ClickSample> clicks = clickHistory.get(uuid);
        Deque<AimSample> aims = aimHistory.get(uuid);

        int clickCount = clicks != null ? clicks.size() : 0;
        int aimCount = aims != null ? aims.size() : 0;

        if (clickCount >= 16 || aimCount >= 16) {
            List<ClickSample> clickList;
            List<AimSample> aimList;
            if (clicks != null) {
                synchronized (clicks) { clickList = new ArrayList<>(clicks); }
            } else {
                clickList = List.of();
            }
            if (aims != null) {
                synchronized (aims) { aimList = new ArrayList<>(aims); }
            } else {
                aimList = List.of();
            }

            PlayerEvidenceWindow window = new PlayerEvidenceWindow(uuid, clickList, aimList);
            BehaviorScore score = scorer.score(window);

            if (scoreConsumer != null) {
                scoreConsumer.accept(new BehaviorScoreEvent(player, score));
            }
        }
    }

    private double calculateAngleToNearestTarget(Player player) {
        Location eye = player.getEyeLocation();
        Vector lookDir = eye.getDirection().normalize();

        double minAngle = 180.0;
        List<Entity> nearby = player.getNearbyEntities(5.0, 5.0, 5.0);
        for (Entity e : nearby) {
            if (e instanceof LivingEntity && e != player) {
                Vector toTarget = e.getLocation().add(0, 1.0, 0).toVector().subtract(eye.toVector()).normalize();
                double dot = Math.max(-1.0, Math.min(1.0, lookDir.dot(toTarget)));
                double angleDeg = Math.toDegrees(Math.acos(dot));
                if (angleDeg < minAngle) {
                    minAngle = angleDeg;
                }
            }
        }
        return minAngle;
    }
}
