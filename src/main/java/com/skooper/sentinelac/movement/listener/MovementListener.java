package com.skooper.sentinelac.movement.listener;

import com.skooper.sentinelac.movement.check.MovementCheck;
import com.skooper.sentinelac.movement.check.MovementViolation;
import com.skooper.sentinelac.movement.model.BlockFriction;
import com.skooper.sentinelac.movement.model.EnvironmentState;
import com.skooper.sentinelac.movement.model.MovementMedium;
import com.skooper.sentinelac.movement.model.PlayerInput;
import com.skooper.sentinelac.movement.model.Vector3D;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Paper listener bridging Bukkit/Paper events with SentinelAC's Movement Engine.
 */
public final class MovementListener implements Listener {

    private final MovementCheck movementCheck;
    private final Consumer<MovementViolationEvent> violationConsumer;

    public record MovementViolationEvent(Player player, MovementViolation violation) {}

    public MovementListener(MovementCheck movementCheck, Consumer<MovementViolationEvent> violationConsumer) {
        this.movementCheck = movementCheck;
        this.violationConsumer = violationConsumer;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Location loc = p.getLocation();
        movementCheck.initializePlayer(
                p.getUniqueId(),
                new Vector3D(loc.getX(), loc.getY(), loc.getZ()),
                loc.getYaw(),
                loc.getPitch(),
                p.isOnGround()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        movementCheck.removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        Location to = event.getTo();
        movementCheck.triggerGrace(p.getUniqueId(), 15);
        movementCheck.initializePlayer(
                p.getUniqueId(),
                new Vector3D(to.getX(), to.getY(), to.getZ()),
                to.getYaw(),
                to.getPitch(),
                p.isOnGround()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        // Plugin or external knockback/velocity applied: give grace window
        movementCheck.triggerGrace(event.getPlayer().getUniqueId(), 10);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        EnvironmentState env = resolveEnvironment(player, to);
        PlayerInput input = resolveInput(player, from, to);

        Vector3D reportedPos = new Vector3D(to.getX(), to.getY(), to.getZ());
        MovementViolation result = movementCheck.checkMovement(uuid, reportedPos, input, env);

        if (result.isViolation() && violationConsumer != null) {
            violationConsumer.accept(new MovementViolationEvent(player, result));
        }
    }

    private EnvironmentState resolveEnvironment(Player player, Location loc) {
        Block feetBlock = loc.getBlock();
        Block groundBlock = loc.clone().subtract(0, 0.5, 0).getBlock();

        MovementMedium medium = MovementMedium.AIR;
        if (player.isGliding()) {
            medium = MovementMedium.ELYTRA;
        } else if (feetBlock.getType() == Material.WATER) {
            medium = MovementMedium.WATER;
        } else if (feetBlock.getType() == Material.LAVA) {
            medium = MovementMedium.LAVA;
        } else if (feetBlock.getType() == Material.COBWEB) {
            medium = MovementMedium.COBWEB;
        }

        BlockFriction friction = BlockFriction.NORMAL;
        Material groundMat = groundBlock.getType();
        if (groundMat == Material.ICE || groundMat == Material.PACKED_ICE || groundMat == Material.FROSTED_ICE) {
            friction = BlockFriction.ICE;
        } else if (groundMat == Material.BLUE_ICE) {
            friction = BlockFriction.BLUE_ICE;
        } else if (groundMat == Material.SLIME_BLOCK) {
            friction = BlockFriction.SLIME;
        } else if (groundMat == Material.HONEY_BLOCK) {
            friction = BlockFriction.HONEY;
        } else if (groundMat == Material.SOUL_SAND) {
            friction = BlockFriction.SOUL_SAND;
        }

        int jumpBoost = 0;
        PotionEffectType jumpType = PotionEffectType.getByName("JUMP_BOOST");
        if (jumpType == null) jumpType = PotionEffectType.getByName("JUMP");
        if (jumpType != null && player.hasPotionEffect(jumpType)) {
            var effect = player.getPotionEffect(jumpType);
            if (effect != null) jumpBoost = effect.getAmplifier() + 1;
        }

        int speedBoost = 0;
        PotionEffectType speedType = PotionEffectType.getByName("SPEED");
        if (speedType != null && player.hasPotionEffect(speedType)) {
            var effect = player.getPotionEffect(speedType);
            if (effect != null) speedBoost = effect.getAmplifier() + 1;
        }

        return new EnvironmentState(
                medium,
                friction,
                player.isOnGround(),
                feetBlock.getType() == Material.COBWEB,
                player.isGliding(),
                jumpBoost,
                speedBoost
        );
    }

    private PlayerInput resolveInput(Player player, Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        float yaw = to.getYaw();
        float pitch = to.getPitch();

        boolean jumping = to.getY() > from.getY() && player.isOnGround();
        boolean sprinting = player.isSprinting();
        boolean sneaking = player.isSneaking();

        // Project movement onto player look vector to infer forward / strafe
        double yawRad = Math.toRadians(yaw);
        double forward = -dx * Math.sin(yawRad) + dz * Math.cos(yawRad);
        double strafe = dx * Math.cos(yawRad) + dz * Math.sin(yawRad);

        return new PlayerInput(forward, strafe, sprinting, sneaking, jumping, yaw, pitch);
    }
}
