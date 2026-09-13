package com.skooper.sentinelac.behavior.scorer;

import com.skooper.sentinelac.behavior.model.BehaviorScore;
import com.skooper.sentinelac.behavior.model.PlayerEvidenceWindow;

/**
 * Pluggable scoring interface for behavioral telemetry.
 * Enables future trained ML models or neural heuristics to substitute the default rule-based engine.
 */
@FunctionalInterface
public interface BehaviorScorer {

    /**
     * Scores a temporal window of player behavioral interactions.
     *
     * @param window Temporal evidence window of clicks, rotations, and aim.
     * @return Quantitative BehaviorScore containing log-likelihood contributions.
     */
    BehaviorScore score(PlayerEvidenceWindow window);
}
