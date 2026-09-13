package com.skooper.sentinelac.combat.listener;

import com.skooper.sentinelac.combat.engine.CombatEngine;
import com.skooper.sentinelac.combat.model.BoundingBox3D;
import com.skooper.sentinelac.combat.model.CombatViolation;
import com.skooper.sentinelac.movement.model.Vector3D;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.BoundingBox;

import java.util.function.Consumer;

/**
 * Paper listener validating combat attacks against geometric ray-AABB intersections.
 */
public final class CombatListener implements Listener {

    private final CombatEngine combatEngine;
    private final Consumer<CombatViolationEvent> violationConsumer;
    private volatile boolean cancelViolations = false;
    private long currentServerTick = 0L;

    public record CombatViolationEvent(Player attacker, Entity target, CombatViolation violation) {}

    public CombatListener(CombatEngine combatEngine, Consumer<CombatViolationEvent> violationConsumer) {
        this.combatEngine = combatEngine;
        this.violationConsumer = violationConsumer;
    }

    public void setCancelViolations(boolean cancelViolations) {
        this.cancelViolations = cancelViolations;
    }

    public void incrementTick() {
        this.currentServerTick++;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        recordEntityBox(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        combatEngine.getLagCompensator().removeEntity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        combatEngine.getLagCompensator().removeEntity(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        Entity target = event.getEntity();
        Location eyeLoc = attacker.getEyeLocation();
        Vector3D eye = new Vector3D(eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ());
        float yaw = eyeLoc.getYaw();
        float pitch = eyeLoc.getPitch();

        int ping = attacker.getPing();
        boolean isCreative = attacker.getGameMode() == GameMode.CREATIVE;

        // Retrieve lag-compensated box, fallback to current target bounding box
        BoundingBox3D targetBox = combatEngine.getLagCompensator()
                .getCompensatedHitbox(target.getUniqueId(), currentServerTick, ping)
                .orElseGet(() -> fromBukkitBoundingBox(target.getBoundingBox()));

        CombatViolation result = combatEngine.validateHitGeometry(eye, yaw, pitch, targetBox, isCreative);

        if (result.isViolation()) {
            if (cancelViolations) {
                event.setCancelled(true);
            }
            if (violationConsumer != null) {
                violationConsumer.accept(new CombatViolationEvent(attacker, target, result));
            }
        }
    }

    private void recordEntityBox(Entity entity) {
        BoundingBox bb = entity.getBoundingBox();
        BoundingBox3D box3D = fromBukkitBoundingBox(bb);
        combatEngine.getLagCompensator().recordEntityState(entity.getUniqueId(), currentServerTick, box3D);
    }

    private static BoundingBox3D fromBukkitBoundingBox(BoundingBox bb) {
        return new BoundingBox3D(
                bb.getMinX(), bb.getMinY(), bb.getMinZ(),
                bb.getMaxX(), bb.getMaxY(), bb.getMaxZ()
        );
    }
}
