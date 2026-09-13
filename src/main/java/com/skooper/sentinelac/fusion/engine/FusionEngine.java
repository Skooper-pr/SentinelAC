package com.skooper.sentinelac.fusion.engine;

import com.skooper.sentinelac.fusion.model.CheatCategory;
import com.skooper.sentinelac.fusion.model.EvidenceRecord;
import com.skooper.sentinelac.fusion.model.FusionFlagEvent;
import com.skooper.sentinelac.fusion.model.PlayerFusionProfile;
import com.skooper.sentinelac.fusion.model.SprtBoundary;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Central evidence-fusion engine executing Wald's Sequential Probability Ratio Test (SPRT).
 * Accumulates log-likelihood ratios per player across all cheat categories, shortcuts
 * deterministic proofs, and enforces calibrated Wald decision boundaries.
 */
public final class FusionEngine {

    public static final double DEFAULT_ALPHA = 0.001; // Target false-positive rate (0.1%)
    public static final double DEFAULT_BETA = 0.010;  // Target false-negative rate (1.0%)

    private volatile SprtBoundary boundary;
    private final Map<UUID, PlayerFusionProfile> profiles = new ConcurrentHashMap<>();
    private final List<Consumer<FusionFlagEvent>> flagListeners = new CopyOnWriteArrayList<>();

    public FusionEngine() {
        this(DEFAULT_ALPHA, DEFAULT_BETA);
    }

    public FusionEngine(double alpha, double beta) {
        this.boundary = new SprtBoundary(alpha, beta);
    }

    public void setAlphaBeta(double alpha, double beta) {
        this.boundary = new SprtBoundary(alpha, beta);
    }

    public SprtBoundary getBoundary() {
        return boundary;
    }

    public void registerFlagListener(Consumer<FusionFlagEvent> listener) {
        flagListeners.add(listener);
    }

    public PlayerFusionProfile getProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, PlayerFusionProfile::new);
    }

    public double getLambda(UUID playerId, CheatCategory category) {
        PlayerFusionProfile profile = profiles.get(playerId);
        return profile != null ? profile.getLambda(category) : 0.0;
    }

    public boolean isFlagged(UUID playerId, CheatCategory category) {
        PlayerFusionProfile profile = profiles.get(playerId);
        return profile != null && profile.isFlagged(category);
    }

    public Set<UUID> getFlaggedPlayers() {
        return profiles.entrySet().stream()
                .filter(e -> e.getValue().isAnyFlagged())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public void removePlayer(UUID playerId) {
        profiles.remove(playerId);
    }

    public void resetPlayer(UUID playerId) {
        profiles.put(playerId, new PlayerFusionProfile(playerId));
    }

    /**
     * Accumulates probabilistic log-likelihood ratio evidence from behavioral checks.
     *
     * @param playerId       The player's UUID.
     * @param category       The cheat category.
     * @param deltaLlr       The log-likelihood ratio contribution from the check.
     * @param evidenceSummary Descriptive explanation of the evidence.
     */
    public void addEvidence(UUID playerId, CheatCategory category, double deltaLlr, String evidenceSummary) {
        if (Double.isNaN(deltaLlr) || Double.isInfinite(deltaLlr) || Math.abs(deltaLlr) < 1.0E-6) {
            return;
        }

        PlayerFusionProfile profile = getProfile(playerId);
        long now = System.currentTimeMillis();
        EvidenceRecord record = new EvidenceRecord(now, category, deltaLlr, false, evidenceSummary);
        profile.addEvidence(record);

        double updatedLambda = profile.getLambda(category) + deltaLlr;
        double upper = boundary.getUpperBound();
        double lower = boundary.getLowerBound();

        if (updatedLambda >= upper) {
            // Upper Wald boundary reached: flag for cheating
            profile.setLambda(category, updatedLambda);
            boolean wasFlagged = profile.isFlagged(category);
            profile.setFlagged(category, true);
            if (!wasFlagged) {
                dispatchFlag(new FusionFlagEvent(playerId, category, updatedLambda, upper, false, evidenceSummary, now));
            }
        } else if (updatedLambda <= lower) {
            // Lower Wald boundary reached: clear accumulated evidence, reset to 0, and keep watching
            profile.clearEvidence(category);
        } else {
            // Between bounds: keep accumulating
            profile.setLambda(category, updatedLambda);
        }
    }

    /**
     * Instantly flags a player upon receiving a deterministic violation proof from the Movement or Combat engine.
     * Shortcuts straight to the upper Wald boundary.
     *
     * @param playerId       The player's UUID.
     * @param category       The cheat category (MOVEMENT or COMBAT).
     * @param evidenceSummary Descriptive violation proof.
     */
    public void addDeterministicViolation(UUID playerId, CheatCategory category, String evidenceSummary) {
        PlayerFusionProfile profile = getProfile(playerId);
        long now = System.currentTimeMillis();
        double upper = boundary.getUpperBound();

        EvidenceRecord record = new EvidenceRecord(now, category, upper, true, evidenceSummary);
        profile.addEvidence(record);

        double current = Math.max(profile.getLambda(category), upper);
        profile.setLambda(category, current);
        boolean wasFlagged = profile.isFlagged(category);
        profile.setFlagged(category, true);

        if (!wasFlagged) {
            dispatchFlag(new FusionFlagEvent(playerId, category, current, upper, true, evidenceSummary, now));
        }
    }

    private void dispatchFlag(FusionFlagEvent event) {
        for (Consumer<FusionFlagEvent> listener : flagListeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {
            }
        }
    }
}
