package com.skooper.sentinelac.behavior.analysis;

import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

/**
 * Evaluates inter-click intervals (ICIs) using distribution fitting and statistical variance tests.
 * Produces calibrated Log-Likelihood Ratios (LLR) indicating evidence of automated clicking.
 */
public final class ClickTimingAnalysis {

    private static final int MIN_SAMPLES = 10;
    private final KolmogorovSmirnovTest ksTest = new KolmogorovSmirnovTest();

    public record ClickAnalysisResult(double logLikelihood, double cv, double ksStatistic, String details) {}

    /**
     * Evaluates a sequence of inter-click intervals in milliseconds.
     *
     * @param intervals Array of click intervals.
     * @return ClickAnalysisResult with LLR contribution.
     */
    public ClickAnalysisResult analyze(double[] intervals) {
        if (intervals == null || intervals.length < MIN_SAMPLES) {
            return new ClickAnalysisResult(0.0, 0.0, 0.0, "Insufficient click samples");
        }

        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (double v : intervals) {
            if (v > 0.0) {
                stats.addValue(v);
            }
        }

        if (stats.getN() < MIN_SAMPLES) {
            return new ClickAnalysisResult(0.0, 0.0, 0.0, "Insufficient positive click intervals");
        }

        double mean = stats.getMean();
        double std = stats.getStandardDeviation();
        double cv = mean > 1.0E-6 ? (std / mean) : 0.0;

        // 1. Extreme low variance test (Autoclickers with constant intervals)
        if (cv < 0.04) {
            // Implausibly constant intervals (human click CV is almost always > 0.15)
            double llr = 3.5;
            return new ClickAnalysisResult(llr, cv, 1.0,
                    String.format("Unnatural click consistency: CV=%.4f (std=%.2fms, mean=%.2fms)", cv, std, mean));
        }

        if (cv < 0.08) {
            double llr = 2.2;
            return new ClickAnalysisResult(llr, cv, 0.8,
                    String.format("Low click variance: CV=%.4f (std=%.2fms, mean=%.2fms)", cv, std, mean));
        }

        // 2. Fit to Log-Normal distribution and evaluate Kolmogorov-Smirnov goodness-of-fit
        double[] logValues = new double[(int) stats.getN()];
        double logSum = 0.0;
        double[] validIntervals = stats.getValues();

        for (int i = 0; i < validIntervals.length; i++) {
            logValues[i] = Math.log(Math.max(1.0, validIntervals[i]));
            logSum += logValues[i];
        }

        double muLn = logSum / validIntervals.length;
        double varLnSum = 0.0;
        for (double lv : logValues) {
            double diff = lv - muLn;
            varLnSum += diff * diff;
        }
        double sigmaLn = Math.sqrt(varLnSum / Math.max(1, validIntervals.length - 1));

        if (sigmaLn < 0.02) {
            // Log intervals are virtually identical
            return new ClickAnalysisResult(3.0, cv, 1.0, "Log-interval variance near zero");
        }

        double ksStatistic;
        try {
            LogNormalDistribution dist = new LogNormalDistribution(muLn, Math.max(0.05, sigmaLn));
            ksStatistic = ksTest.kolmogorovSmirnovStatistic(dist, validIntervals);
        } catch (Exception e) {
            ksStatistic = 0.5;
        }

        // Calibrated LLR synthesis
        double llr;
        String details;
        if (cv >= 0.14 && ksStatistic <= 0.22) {
            // Natural human distribution: high jitter, excellent log-normal fit
            llr = -0.8;
            details = String.format("Human-like click dynamics: CV=%.3f, KS=%.3f", cv, ksStatistic);
        } else if (cv < 0.11) {
            // Sub-human variance
            llr = 1.2;
            details = String.format("Constrained click variance: CV=%.3f, KS=%.3f", cv, ksStatistic);
        } else if (ksStatistic > 0.38) {
            // Unnatural uniform distribution (synthetic random clicker)
            llr = 1.5;
            details = String.format("Synthetic click distribution mismatch: KS=%.3f, CV=%.3f", ksStatistic, cv);
        } else {
            llr = -0.2;
            details = String.format("Ambiguous click dynamics: CV=%.3f, KS=%.3f", cv, ksStatistic);
        }

        return new ClickAnalysisResult(llr, cv, ksStatistic, details);
    }
}
