package com.skooper.sentinelac.combat.engine;

import com.skooper.sentinelac.combat.model.BoundingBox3D;
import com.skooper.sentinelac.combat.model.HistoricalEntityState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative lag compensator that stores historical bounding boxes
 * and rewinds target hitboxes based on attacker latency (ping).
 */
public final class LagCompensator {

    private static final int MAX_HISTORY_TICKS = 40;
    private final Map<UUID, Deque<HistoricalEntityState>> history = new ConcurrentHashMap<>();

    public void recordEntityState(UUID entityId, long tick, BoundingBox3D box) {
        history.compute(entityId, (id, queue) -> {
            if (queue == null) {
                queue = new ArrayDeque<>(MAX_HISTORY_TICKS);
            }
            if (queue.size() >= MAX_HISTORY_TICKS) {
                queue.pollFirst();
            }
            queue.addLast(new HistoricalEntityState(tick, System.currentTimeMillis(), box));
            return queue;
        });
    }

    public void removeEntity(UUID entityId) {
        history.remove(entityId);
    }

    /**
     * Reconstructs the target's lag-compensated bounding box for the attacker's latency.
     *
     * @param entityId    The target entity ID.
     * @param currentTick Current server tick.
     * @param pingMs      Attacker ping in milliseconds.
     * @return Optional BoundingBox3D rewound to the attacker's perspective.
     */
    public Optional<BoundingBox3D> getCompensatedHitbox(UUID entityId, long currentTick, int pingMs) {
        Deque<HistoricalEntityState> queue = history.get(entityId);
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }

        // Calculate how many ticks to rewind (bounded between 0 and 20 ticks = 1 second)
        int latencyTicks = Math.min(20, Math.max(0, pingMs / 50));
        long targetTick = currentTick - latencyTicks;

        HistoricalEntityState bestMatch = null;
        long minTickDiff = Long.MAX_VALUE;

        for (HistoricalEntityState state : queue) {
            long diff = Math.abs(state.getTick() - targetTick);
            if (diff < minTickDiff) {
                minTickDiff = diff;
                bestMatch = state;
            }
        }

        if (bestMatch != null) {
            return Optional.of(bestMatch.getBoundingBox());
        }

        return Optional.of(queue.peekLast().getBoundingBox());
    }
}
