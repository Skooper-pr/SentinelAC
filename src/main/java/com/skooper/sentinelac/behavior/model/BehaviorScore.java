package com.skooper.sentinelac.behavior.model;

/**
 * Encapsulates the quantitative log-likelihood-ratio contributions evaluated from behavioral analyses.
 */
public final class BehaviorScore {

    private final double clickLogLikelihood;
    private final double aimLogLikelihood;
    private final double periodicityLogLikelihood;
    private final double combinedLogLikelihood;
    private final String summary;

    public BehaviorScore(double clickLogLikelihood, double aimLogLikelihood, double periodicityLogLikelihood, String summary) {
        this.clickLogLikelihood = clickLogLikelihood;
        this.aimLogLikelihood = aimLogLikelihood;
        this.periodicityLogLikelihood = periodicityLogLikelihood;
        this.combinedLogLikelihood = clickLogLikelihood + aimLogLikelihood + periodicityLogLikelihood;
        this.summary = summary;
    }

    public static BehaviorScore zero() {
        return new BehaviorScore(0.0, 0.0, 0.0, "Insufficient data");
    }

    public double getClickLogLikelihood() {
        return clickLogLikelihood;
    }

    public double getAimLogLikelihood() {
        return aimLogLikelihood;
    }

    public double getPeriodicityLogLikelihood() {
        return periodicityLogLikelihood;
    }

    public double getCombinedLogLikelihood() {
        return combinedLogLikelihood;
    }

    public String getSummary() {
        return summary;
    }

    @Override
    public String toString() {
        return String.format("BehaviorScore[click=%.3f, aim=%.3f, period=%.3f, total=%.3f, summary=%s]",
                clickLogLikelihood, aimLogLikelihood, periodicityLogLikelihood, combinedLogLikelihood, summary);
    }
}
