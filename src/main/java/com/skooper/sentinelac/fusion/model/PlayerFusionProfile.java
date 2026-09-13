package com.skooper.sentinelac.fusion.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Accumulated SPRT evidence state and active flags per cheat category for a player.
 */
public final class PlayerFusionProfile {

    private static final int MAX_EVIDENCE_PER_CATEGORY = 30;

    private final UUID playerId;
    private final Map<CheatCategory, Double> lambdas = new EnumMap<>(CheatCategory.class);
    private final Map<CheatCategory, Boolean> flagged = new EnumMap<>(CheatCategory.class);
    private final Map<CheatCategory, Deque<EvidenceRecord>> evidence = new EnumMap<>(CheatCategory.class);

    public PlayerFusionProfile(UUID playerId) {
        this.playerId = playerId;
        for (CheatCategory cat : CheatCategory.values()) {
            lambdas.put(cat, 0.0);
            flagged.put(cat, false);
            evidence.put(cat, new ArrayDeque<>(MAX_EVIDENCE_PER_CATEGORY));
        }
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public synchronized double getLambda(CheatCategory category) {
        return lambdas.getOrDefault(category, 0.0);
    }

    public synchronized void setLambda(CheatCategory category, double value) {
        lambdas.put(category, value);
    }

    public synchronized boolean isFlagged(CheatCategory category) {
        return flagged.getOrDefault(category, false);
    }

    public synchronized boolean isAnyFlagged() {
        for (boolean f : flagged.values()) {
            if (f) return true;
        }
        return false;
    }

    public synchronized void setFlagged(CheatCategory category, boolean isFlagged) {
        flagged.put(category, isFlagged);
    }

    public synchronized void addEvidence(EvidenceRecord record) {
        Deque<EvidenceRecord> deque = evidence.get(record.getCategory());
        if (deque != null) {
            if (deque.size() >= MAX_EVIDENCE_PER_CATEGORY) {
                deque.pollFirst();
            }
            deque.addLast(record);
        }
    }

    public synchronized void clearEvidence(CheatCategory category) {
        lambdas.put(category, 0.0);
        Deque<EvidenceRecord> deque = evidence.get(category);
        if (deque != null) {
            deque.clear();
        }
    }

    public synchronized List<EvidenceRecord> getEvidence(CheatCategory category) {
        Deque<EvidenceRecord> deque = evidence.get(category);
        return deque != null ? new ArrayList<>(deque) : Collections.emptyList();
    }

    public synchronized Map<CheatCategory, Double> getAllLambdas() {
        return new EnumMap<>(lambdas);
    }
}
