package com.skooper.sentinelac.behavior.scorer;

import com.skooper.sentinelac.behavior.analysis.AimResponseAnalysis;
import com.skooper.sentinelac.behavior.analysis.ClickTimingAnalysis;
import com.skooper.sentinelac.behavior.analysis.PeriodicityAnalysis;
import com.skooper.sentinelac.behavior.model.BehaviorScore;
import com.skooper.sentinelac.behavior.model.PlayerEvidenceWindow;

/**
 * Default rule-based implementation of BehaviorScorer synthesizing click dynamics,
 * control-theory aim response, and spectral FFT periodicity.
 */
public final class RuleBasedBehaviorScorer implements BehaviorScorer {

    private final ClickTimingAnalysis clickAnalysis;
    private final AimResponseAnalysis aimAnalysis;
    private final PeriodicityAnalysis periodicityAnalysis;

    public RuleBasedBehaviorScorer() {
        this.clickAnalysis = new ClickTimingAnalysis();
        this.aimAnalysis = new AimResponseAnalysis();
        this.periodicityAnalysis = new PeriodicityAnalysis();
    }

    public RuleBasedBehaviorScorer(ClickTimingAnalysis clickAnalysis, AimResponseAnalysis aimAnalysis, PeriodicityAnalysis periodicityAnalysis) {
        this.clickAnalysis = clickAnalysis;
        this.aimAnalysis = aimAnalysis;
        this.periodicityAnalysis = periodicityAnalysis;
    }

    @Override
    public BehaviorScore score(PlayerEvidenceWindow window) {
        if (window == null) {
            return BehaviorScore.zero();
        }

        // 1. Evaluate Click Timing
        double[] intervals = window.getClickIntervals();
        var clickResult = clickAnalysis.analyze(intervals);

        // 2. Evaluate Aim Response
        var aimResult = aimAnalysis.analyze(window.getAimSamples());

        // 3. Evaluate Periodicity over clicks and yaw deltas
        var clickPeriodicity = periodicityAnalysis.analyze(intervals);
        double[] yawDeltas = window.getYawDeltas();
        var aimPeriodicity = periodicityAnalysis.analyze(yawDeltas);

        double periodicityLlr = Math.max(clickPeriodicity.logLikelihood(), aimPeriodicity.logLikelihood());
        String periodDetail = clickPeriodicity.logLikelihood() >= aimPeriodicity.logLikelihood()
                ? clickPeriodicity.details() : aimPeriodicity.details();

        String summary = String.format("Clicks: [%s], Aim: [%s], Periodicity: [%s]",
                clickResult.details(), aimResult.details(), periodDetail);

        return new BehaviorScore(
                clickResult.logLikelihood(),
                aimResult.logLikelihood(),
                periodicityLlr,
                summary
        );
    }
}
