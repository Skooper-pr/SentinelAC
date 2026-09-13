package com.skooper.sentinelac.behavior.analysis;

import com.skooper.sentinelac.behavior.model.AimSample;

import java.util.List;

/**
 * Control-theory aim analysis evaluating target convergence, reaction latency floor (~150ms),
 * and residual against a critically damped neuromuscular response model.
 */
public final class AimResponseAnalysis {

    public static final double BIOLOGICAL_REACTION_FLOOR_MS = 150.0;
    private static final int MIN_SAMPLES = 5;

    public record AimAnalysisResult(double logLikelihood, double convergenceTimeMs, double residual, String details) {}

    /**
     * Evaluates aim tracking trajectory samples during target engagement.
     *
     * @param samples Chronological list of aim samples.
     * @return AimAnalysisResult with LLR contribution.
     */
    public AimAnalysisResult analyze(List<AimSample> samples) {
        if (samples == null || samples.size() < MIN_SAMPLES) {
            return new AimAnalysisResult(0.0, 0.0, 0.0, "Insufficient aim samples");
        }

        // 1. Detect convergence episodes: from significant target angle deviation (> 10 deg) to lock (< 2 deg)
        int startIndex = -1;
        int lockIndex = -1;

        for (int i = 0; i < samples.size(); i++) {
            AimSample s = samples.get(i);
            if (startIndex == -1 && s.getTargetAngleError() > 10.0) {
                startIndex = i;
            } else if (startIndex != -1 && s.getTargetAngleError() <= 2.0) {
                lockIndex = i;
                break;
            }
        }

        if (startIndex == -1 || lockIndex == -1) {
            // Check for steady tracking tremor vs robotic lock
            return evaluateSteadyTracking(samples);
        }

        AimSample startSample = samples.get(startIndex);
        AimSample lockSample = samples.get(lockIndex);
        double convergenceTimeMs = lockSample.getTimestampMs() - startSample.getTimestampMs();
        double initialAngle = startSample.getTargetAngleError();

        // 2. Reaction time check: Sub-biological convergence
        if (convergenceTimeMs < 80.0 && initialAngle > 12.0) {
            // Instantaneous snap aimbot: converged in <= 1 tick from > 12 degrees
            return new AimAnalysisResult(
                    3.5,
                    convergenceTimeMs,
                    0.0,
                    String.format("Superhuman snap convergence: %.1f deg in %.1f ms (< 80ms)", initialAngle, convergenceTimeMs)
            );
        }

        if (convergenceTimeMs < BIOLOGICAL_REACTION_FLOOR_MS && initialAngle > 10.0) {
            return new AimAnalysisResult(
                    2.0,
                    convergenceTimeMs,
                    0.01,
                    String.format("Sub-biological reaction time: %.1f deg in %.1f ms (< 150ms)", initialAngle, convergenceTimeMs)
            );
        }

        // 3. Model convergence as critically damped 2nd order response:
        // theta_model(t) = theta0 * (1 + omega * t) * exp(-omega * t)
        double omega = 0.010; // ~10 rad/sec characteristic human motion bandwidth
        double residualSum = 0.0;
        int count = 0;

        for (int i = startIndex; i <= lockIndex; i++) {
            AimSample s = samples.get(i);
            double t = s.getTimestampMs() - startSample.getTimestampMs();
            double modeledAngle = initialAngle * (1.0 + omega * t) * Math.exp(-omega * t);
            double actualAngle = s.getTargetAngleError();
            double diff = actualAngle - modeledAngle;
            residualSum += diff * diff;
            count++;
        }

        double mseResidual = count > 0 ? (residualSum / count) : 0.0;

        // 4. Calculate micro-tremor along the path
        double tremor = calculateAngularTremor(samples, startIndex, lockIndex);

        // Calibrated LLR
        if (mseResidual < 0.02 && tremor < 0.005) {
            // Synthetically perfect linear/bezier curve with zero human micro-tremor
            return new AimAnalysisResult(
                    2.4,
                    convergenceTimeMs,
                    mseResidual,
                    String.format("Synthetic robotic curve: residual=%.5f, tremor=%.5f", mseResidual, tremor)
            );
        }

        if (convergenceTimeMs >= BIOLOGICAL_REACTION_FLOOR_MS && tremor >= 0.03) {
            // Natural human motion with biological tremor and realistic latency
            return new AimAnalysisResult(
                    -0.9,
                    convergenceTimeMs,
                    mseResidual,
                    String.format("Human neuromuscular convergence: latency=%.1f ms, tremor=%.4f", convergenceTimeMs, tremor)
            );
        }

        return new AimAnalysisResult(-0.2, convergenceTimeMs, mseResidual, "Ambiguous aim trajectory");
    }

    private AimAnalysisResult evaluateSteadyTracking(List<AimSample> samples) {
        double tremor = calculateAngularTremor(samples, 0, samples.size() - 1);
        if (tremor < 0.002) {
            return new AimAnalysisResult(1.8, 0.0, 0.0, "Robotic zero-tremor tracking lock");
        }
        return new AimAnalysisResult(-0.4, 0.0, 0.0, "Natural tracking tremor");
    }

    private double calculateAngularTremor(List<AimSample> samples, int from, int to) {
        if (to - from < 2) return 0.05;
        double secondDiffSum = 0.0;
        int count = 0;
        for (int i = from + 1; i < to; i++) {
            float prevYaw = samples.get(i - 1).getYawDelta();
            float currYaw = samples.get(i).getYawDelta();
            float nextYaw = samples.get(i + 1).getYawDelta();
            double jerk = (nextYaw - currYaw) - (currYaw - prevYaw);
            secondDiffSum += Math.abs(jerk);
            count++;
        }
        return count > 0 ? (secondDiffSum / count) : 0.05;
    }
}
