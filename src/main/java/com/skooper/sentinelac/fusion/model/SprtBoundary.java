package com.skooper.sentinelac.fusion.model;

/**
 * Wald Sequential Probability Ratio Test (SPRT) decision boundaries derived from
 * target Type I (alpha) and Type II (beta) error rates.
 */
public final class SprtBoundary {

    private final double alpha;
    private final double beta;
    private final double upperBound;
    private final double lowerBound;

    public SprtBoundary(double alpha, double beta) {
        if (alpha <= 0.0 || alpha >= 1.0) {
            throw new IllegalArgumentException("Alpha (false positive rate) must be between 0 and 1: " + alpha);
        }
        if (beta <= 0.0 || beta >= 1.0) {
            throw new IllegalArgumentException("Beta (false negative rate) must be between 0 and 1: " + beta);
        }
        if (alpha + beta >= 1.0) {
            throw new IllegalArgumentException("Alpha + Beta must be strictly less than 1.0: " + (alpha + beta));
        }

        this.alpha = alpha;
        this.beta = beta;
        // Upper Wald boundary: A = ln((1 - beta) / alpha)
        this.upperBound = Math.log((1.0 - beta) / alpha);
        // Lower Wald boundary: B = ln(beta / (1 - alpha))
        this.lowerBound = Math.log(beta / (1.0 - alpha));
    }

    public double getAlpha() {
        return alpha;
    }

    public double getBeta() {
        return beta;
    }

    public double getUpperBound() {
        return upperBound;
    }

    public double getLowerBound() {
        return lowerBound;
    }

    @Override
    public String toString() {
        return String.format("SprtBoundary[alpha=%.4f, beta=%.4f, upper=%.4f, lower=%.4f]",
                alpha, beta, upperBound, lowerBound);
    }
}
