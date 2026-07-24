package com.flashcrash;

import com.flashcrash.benchmark.WelchTTest;

import java.util.ArrayList;
import java.util.List;

/**
 * ALGORITHM 3: Monte Carlo simulation.
 *
 * A single simulation run is one draw from a stochastic process (agent
 * arrival times, order sizes/prices, etc. are all randomized). To answer
 * "does this risk control actually help, on average, across the space of
 * plausible market conditions" rather than "did it help in this one
 * lucky/unlucky run", we repeat the simulation across N independent random
 * seeds per configuration and look at the empirical distribution of
 * outcomes (mean, std, crash frequency), then use Welch's t-test to check
 * whether the difference between configurations is likely to be real.
 */
public class MonteCarloRunner {

    public static class ConfigSummary {
        public String label;
        public List<RunResult> runs;
        public double crashFrequencyPct;
        public double meanDrawdownPct, stdDrawdownPct;
        public double meanRecoverySec;
        public double meanHalfLifeMin;
    }

    public static ConfigSummary runConfig(String label, int nRuns, long baseSeed,
                                           boolean useLuld, boolean useVpinHalt) {
        List<RunResult> results = new ArrayList<>();
        for (int i = 0; i < nRuns; i++) {
            long seed = baseSeed + i;
            Scenario.Output out = Scenario.run(seed, useLuld, useVpinHalt);
            results.add(out.result);
        }
        ConfigSummary s = new ConfigSummary();
        s.label = label;
        s.runs = results;

        int crashes = 0;
        double sumDD = 0, sumRecovery = 0, sumHalfLife = 0;
        int recoveryCount = 0, halfLifeCount = 0;
        List<Double> ddList = new ArrayList<>();
        for (RunResult r : results) {
            if (r.crashOccurred) crashes++;
            sumDD += r.maxDrawdownPct;
            ddList.add(r.maxDrawdownPct);
            if (r.recoveryTimeSec >= 0) { sumRecovery += r.recoveryTimeSec; recoveryCount++; }
            if (!Double.isNaN(r.hftInventoryHalfLifeMinutes) && r.hftInventoryHalfLifeMinutes > 0
                    && r.hftInventoryHalfLifeMinutes < 60) {
                sumHalfLife += r.hftInventoryHalfLifeMinutes;
                halfLifeCount++;
            }
        }
        s.crashFrequencyPct = 100.0 * crashes / nRuns;
        s.meanDrawdownPct = sumDD / nRuns;
        double sumSq = 0;
        for (double dd : ddList) sumSq += Math.pow(dd - s.meanDrawdownPct, 2);
        s.stdDrawdownPct = Math.sqrt(sumSq / Math.max(1, nRuns - 1));
        s.meanRecoverySec = recoveryCount == 0 ? Double.NaN : sumRecovery / recoveryCount;
        s.meanHalfLifeMin = halfLifeCount == 0 ? Double.NaN : sumHalfLife / halfLifeCount;
        return s;
    }
}
