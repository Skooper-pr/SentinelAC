package com.skooper.sentinelac.behavior.analysis;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.Arrays;

/**
 * Spectral and autocorrelation analysis detecting periodic or looped input patterns
 * in inter-click intervals and rotational aim trajectories.
 */
public final class PeriodicityAnalysis {

    private static final int MIN_SAMPLES = 16;
    private final FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

    public record PeriodicityResult(double logLikelihood, double peakToAverageRatio, double maxAutocorrelation, String details) {}

    /**
     * Evaluates a time series (click intervals or yaw deltas) for spectral periodicity.
     *
     * @param series Time series signal.
     * @return PeriodicityResult with LLR contribution.
     */
    public PeriodicityResult analyze(double[] series) {
        if (series == null || series.length < MIN_SAMPLES) {
            return new PeriodicityResult(0.0, 0.0, 0.0, "Insufficient samples for spectral analysis");
        }

        // 1. Prepare power-of-2 padded sequence with DC offset removed
        int n = getNextPowerOfTwo(Math.min(series.length, 128));
        double[] signal = new double[n];
        int sampleCount = Math.min(series.length, n);

        double sum = 0.0;
        for (int i = 0; i < sampleCount; i++) {
            sum += series[i];
        }
        double mean = sum / sampleCount;

        for (int i = 0; i < sampleCount; i++) {
            signal[i] = series[i] - mean;
        }
        // Zero-pad remainder
        for (int i = sampleCount; i < n; i++) {
            signal[i] = 0.0;
        }

        // Check if signal is entirely constant (infinite periodicity)
        double variance = 0.0;
        for (int i = 0; i < sampleCount; i++) {
            variance += signal[i] * signal[i];
        }
        if (variance < 1.0E-6) {
            return new PeriodicityResult(3.0, 100.0, 1.0, "Constant signal: identical repeated inputs");
        }

        // 2. Perform Fast Fourier Transform
        Complex[] fft = transformer.transform(signal, TransformType.FORWARD);
        int halfN = n / 2;
        double maxPower = 0.0;
        double totalPower = 0.0;

        for (int k = 1; k <= halfN; k++) { // Analyze full spectrum up to Nyquist bin
            double real = fft[k].getReal();
            double imag = fft[k].getImaginary();
            double power = real * real + imag * imag;
            if (power > maxPower) {
                maxPower = power;
            }
            totalPower += power;
        }

        int activeBins = Math.max(1, halfN);
        double meanPower = totalPower / activeBins;
        double spar = meanPower > 1.0E-9 ? (maxPower / meanPower) : 1.0;

        // 3. Normalized Autocorrelation at non-zero lags
        double maxAutoCorr = computeMaxAutocorrelation(series);

        // 4. Calibrated LLR
        // A true harmonic macro exhibits both concentrated spectral power and repeating autocorrelation
        boolean strongHarmonic = (spar > 8.0 && maxAutoCorr > 0.55) || spar > 14.0 || maxAutoCorr > 0.80;
        boolean moderateHarmonic = (spar > 6.0 && maxAutoCorr > 0.45) || spar > 9.0;

        if (strongHarmonic) {
            double baseLlr = 2.0;
            if (spar > 8.0) {
                baseLlr += Math.min(1.0, (spar - 8.0) * 0.1);
            }
            if (maxAutoCorr > 0.80) {
                baseLlr += (maxAutoCorr - 0.80) * 2.0;
            }
            double llr = Math.min(3.5, baseLlr);
            return new PeriodicityResult(
                    llr,
                    spar,
                    maxAutoCorr,
                    String.format("Sharp spectral peak detected: SPAR=%.2f, MaxAutoCorr=%.3f", spar, maxAutoCorr)
            );
        }

        if (moderateHarmonic) {
            return new PeriodicityResult(
                    1.2,
                    spar,
                    maxAutoCorr,
                    String.format("Elevated periodicity: SPAR=%.2f, MaxAutoCorr=%.3f", spar, maxAutoCorr)
            );
        }

        if (spar < 7.0 && maxAutoCorr < 0.40) {
            // Broadband human stochastic noise floor
            return new PeriodicityResult(
                    -0.7,
                    spar,
                    maxAutoCorr,
                    String.format("Aperiodic human noise floor: SPAR=%.2f, MaxAutoCorr=%.3f", spar, maxAutoCorr)
            );
        }

        return new PeriodicityResult(-0.1, spar, maxAutoCorr, "Nominal input variation");
    }

    private double computeMaxAutocorrelation(double[] data) {
        int n = data.length;
        if (n < 4) return 0.0;

        double sum = 0.0;
        for (double v : data) sum += v;
        double mean = sum / n;

        double var = 0.0;
        for (double v : data) {
            double d = v - mean;
            var += d * d;
        }
        if (var < 1.0E-6) return 1.0;

        double maxR = 0.0;
        int maxLag = Math.min(n / 2, 20);

        for (int lag = 2; lag <= maxLag; lag++) {
            double cov = 0.0;
            for (int i = 0; i < n - lag; i++) {
                cov += (data[i] - mean) * (data[i + lag] - mean);
            }
            double r = cov / var;
            if (r > maxR) {
                maxR = r;
            }
        }
        return maxR;
    }

    private int getNextPowerOfTwo(int x) {
        int n = 1;
        while (n < x) {
            n <<= 1;
        }
        return Math.max(16, n);
    }
}
