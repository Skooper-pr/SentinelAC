package com.skooper.sentinelac.behavior;

import com.skooper.sentinelac.behavior.analysis.AimResponseAnalysis;
import com.skooper.sentinelac.behavior.analysis.ClickTimingAnalysis;
import com.skooper.sentinelac.behavior.analysis.PeriodicityAnalysis;
import com.skooper.sentinelac.behavior.model.AimSample;
import com.skooper.sentinelac.behavior.model.BehaviorScore;
import com.skooper.sentinelac.behavior.model.ClickSample;
import com.skooper.sentinelac.behavior.model.PlayerEvidenceWindow;
import com.skooper.sentinelac.behavior.scorer.RuleBasedBehaviorScorer;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviorEngineTest {

    private ClickTimingAnalysis clickAnalysis;
    private AimResponseAnalysis aimAnalysis;
    private PeriodicityAnalysis periodicityAnalysis;
    private RuleBasedBehaviorScorer scorer;

    @BeforeEach
    void setUp() {
        clickAnalysis = new ClickTimingAnalysis();
        aimAnalysis = new AimResponseAnalysis();
        periodicityAnalysis = new PeriodicityAnalysis();
        scorer = new RuleBasedBehaviorScorer(clickAnalysis, aimAnalysis, periodicityAnalysis);
    }

    @Test
    @DisplayName("Click Timing: Legit human log-normal clicks score negative LLR, autoclicker scores positive LLR")
    void testClickTimingAnalysis() {
        // 1. Synthetic Legit Data: Log-normal human clicking distribution (~10 CPS, mu=ln(100), sigma=0.25)
        JDKRandomGenerator rng = new JDKRandomGenerator(42);
        LogNormalDistribution humanDist = new LogNormalDistribution(rng, Math.log(100.0), 0.22);
        double[] legitClicks = new double[35];
        for (int i = 0; i < legitClicks.length; i++) {
            legitClicks[i] = humanDist.sample();
        }

        var legitResult = clickAnalysis.analyze(legitClicks);
        assertTrue(legitResult.logLikelihood() <= 0.0,
                "Human log-normal clicking must produce negative/neutral LLR: " + legitResult.logLikelihood());
        assertTrue(legitResult.cv() >= 0.14, "Human clicking should have realistic CV: " + legitResult.cv());

        // 2. Synthetic Cheat Data: Fixed-interval autoclicker (constant 100ms clicks)
        double[] cheatClicks = new double[35];
        for (int i = 0; i < cheatClicks.length; i++) {
            cheatClicks[i] = 100.0;
        }

        var cheatResult = clickAnalysis.analyze(cheatClicks);
        assertTrue(cheatResult.logLikelihood() >= 2.0,
                "Fixed autoclicker must produce strong positive LLR: " + cheatResult.logLikelihood());
        assertTrue(cheatResult.cv() < 0.04, "Autoclicker CV must be near zero: " + cheatResult.cv());
    }

    @Test
    @DisplayName("Aim Response: Damped human reaction vs superhuman snap aimbot")
    void testAimResponseAnalysis() {
        long startTime = 1000L;

        // 1. Synthetic Cheat Data: Superhuman snap aimbot (25 deg error snaps to 0 in 50ms / 1 tick)
        List<AimSample> snapSamples = new ArrayList<>();
        snapSamples.add(new AimSample(startTime, 0.0f, 0.0f, 25.0, true));
        snapSamples.add(new AimSample(startTime + 50L, 25.0f, 0.0f, 0.5, true));
        snapSamples.add(new AimSample(startTime + 100L, 0.0f, 0.0f, 0.2, true));
        snapSamples.add(new AimSample(startTime + 150L, 0.0f, 0.0f, 0.1, true));
        snapSamples.add(new AimSample(startTime + 200L, 0.0f, 0.0f, 0.1, true));

        var snapResult = aimAnalysis.analyze(snapSamples);
        assertTrue(snapResult.logLikelihood() >= 3.0,
                "Superhuman snap aimbot (< 80ms) must produce strong positive LLR: " + snapResult.logLikelihood());
        assertTrue(snapResult.convergenceTimeMs() < AimResponseAnalysis.BIOLOGICAL_REACTION_FLOOR_MS);

        // 2. Synthetic Legit Data: Human reaction delay (200ms) followed by damped tracking with natural micro-tremor
        List<AimSample> humanSamples = new ArrayList<>();
        humanSamples.add(new AimSample(startTime, 0.0f, 0.0f, 25.0, true));
        humanSamples.add(new AimSample(startTime + 50L, 0.2f, 0.1f, 24.8, true));
        humanSamples.add(new AimSample(startTime + 100L, 0.5f, 0.2f, 24.2, true));
        humanSamples.add(new AimSample(startTime + 150L, 1.2f, 0.3f, 23.0, true));
        humanSamples.add(new AimSample(startTime + 200L, 3.5f, 0.5f, 18.0, true));
        humanSamples.add(new AimSample(startTime + 250L, 5.0f, 0.4f, 10.0, true));
        humanSamples.add(new AimSample(startTime + 300L, 4.0f, -0.2f, 4.0, true));
        humanSamples.add(new AimSample(startTime + 350L, 2.5f, 0.1f, 1.5, true)); // Locked after 350ms (> 150ms)
        humanSamples.add(new AimSample(startTime + 400L, 0.8f, 0.2f, 1.0, true));

        var humanResult = aimAnalysis.analyze(humanSamples);
        assertTrue(humanResult.logLikelihood() < 0.0,
                "Human neuromuscular convergence must produce negative LLR: " + humanResult.logLikelihood());
        assertTrue(humanResult.convergenceTimeMs() >= AimResponseAnalysis.BIOLOGICAL_REACTION_FLOOR_MS);
    }

    @Test
    @DisplayName("Periodicity: Aperiodic white noise vs periodic looped macro script")
    void testPeriodicityAnalysis() {
        Random random = new Random(12345);

        // 1. Synthetic Legit Signal: Aperiodic random stochastic noise
        double[] legitSignal = new double[64];
        for (int i = 0; i < legitSignal.length; i++) {
            legitSignal[i] = 80.0 + random.nextDouble() * 50.0;
        }

        var legitResult = periodicityAnalysis.analyze(legitSignal);
        assertTrue(legitResult.logLikelihood() < 0.0,
                "Aperiodic noise should yield negative LLR: " + legitResult.logLikelihood());
        assertTrue(legitResult.peakToAverageRatio() < 7.0,
                "SPAR should be low for stochastic noise: " + legitResult.peakToAverageRatio());

        // 2. Synthetic Cheat Signal: Periodic macro repeating a distinct square wave [120, 80, 120, 80, ...]
        double[] cheatSignal = new double[64];
        for (int i = 0; i < cheatSignal.length; i++) {
            cheatSignal[i] = (i % 2 == 0) ? 120.0 : 80.0;
        }

        var cheatResult = periodicityAnalysis.analyze(cheatSignal);
        assertTrue(cheatResult.logLikelihood() >= 1.5,
                "Periodic macro must yield high positive LLR: " + cheatResult.logLikelihood());
        assertTrue(cheatResult.peakToAverageRatio() > 6.0,
                "SPAR should have a sharp harmonic peak: " + cheatResult.peakToAverageRatio());
    }

    @Test
    @DisplayName("RuleBasedBehaviorScorer: End-to-end evidence window scoring")
    void testRuleBasedBehaviorScorerEndToEnd() {
        UUID playerId = UUID.randomUUID();

        // 1. Cheat Window: Autoclicker + Snap Aim
        List<ClickSample> cheatClicks = new ArrayList<>();
        long t = 1000L;
        for (int i = 0; i < 30; i++) {
            cheatClicks.add(new ClickSample(t, 100.0));
            t += 100L;
        }

        List<AimSample> cheatAims = new ArrayList<>();
        cheatAims.add(new AimSample(1000L, 0.0f, 0.0f, 20.0, true));
        cheatAims.add(new AimSample(1050L, 20.0f, 0.0f, 0.5, true));
        cheatAims.add(new AimSample(1100L, 0.0f, 0.0f, 0.2, true));
        cheatAims.add(new AimSample(1150L, 0.0f, 0.0f, 0.1, true));
        cheatAims.add(new AimSample(1200L, 0.0f, 0.0f, 0.1, true));

        PlayerEvidenceWindow cheatWindow = new PlayerEvidenceWindow(playerId, cheatClicks, cheatAims);
        BehaviorScore cheatScore = scorer.score(cheatWindow);

        assertTrue(cheatScore.getCombinedLogLikelihood() > 3.0,
                "Blatant automated behavior must have high positive combined LLR: " + cheatScore.getCombinedLogLikelihood());
        assertTrue(cheatScore.getClickLogLikelihood() > 1.5);
        assertTrue(cheatScore.getAimLogLikelihood() > 1.5);

        // 2. Legit Window: Natural jitter clicks + human reaction tracking
        JDKRandomGenerator rng = new JDKRandomGenerator(999);
        LogNormalDistribution humanDist = new LogNormalDistribution(rng, Math.log(110.0), 0.20);
        List<ClickSample> legitClicks = new ArrayList<>();
        long lt = 1000L;
        for (int i = 0; i < 30; i++) {
            double interval = humanDist.sample();
            legitClicks.add(new ClickSample(lt, interval));
            lt += (long) interval;
        }

        List<AimSample> legitAims = new ArrayList<>();
        legitAims.add(new AimSample(1000L, 0.0f, 0.0f, 15.0, true));
        legitAims.add(new AimSample(1050L, 0.2f, 0.1f, 14.8, true));
        legitAims.add(new AimSample(1100L, 0.4f, 0.2f, 14.2, true));
        legitAims.add(new AimSample(1150L, 1.0f, 0.3f, 13.0, true));
        legitAims.add(new AimSample(1200L, 2.5f, 0.4f, 9.0, true));
        legitAims.add(new AimSample(1250L, 3.2f, 0.2f, 3.0, true));
        legitAims.add(new AimSample(1300L, 1.8f, 0.1f, 1.2, true)); // Converged at 300ms

        PlayerEvidenceWindow legitWindow = new PlayerEvidenceWindow(playerId, legitClicks, legitAims);
        BehaviorScore legitScore = scorer.score(legitWindow);

        assertTrue(legitScore.getCombinedLogLikelihood() < 0.0,
                "Legitimate play must result in negative combined LLR: " + legitScore.getCombinedLogLikelihood());
    }
}
