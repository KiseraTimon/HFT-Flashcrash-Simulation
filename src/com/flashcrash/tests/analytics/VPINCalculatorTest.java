package com.flashcrash.tests.analytics;

import com.flashcrash.analytics.VPINCalculator;
import com.flashcrash.core.MarketConstants;
import com.flashcrash.core.Trade;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for VPINCalculator: the Volume-Synchronized Probability of Informed
 * Trading measure. VPIN's whole purpose is to distinguish "trading that
 * looks one-sided" from "trading that looks balanced" -- so rather than
 * trying to hand-verify an exact numeric VPIN value (which would require
 * essentially reimplementing the Bulk Volume Classification formula in the
 * test), we construct two synthetic trade tapes with an OBVIOUS, designed
 * -in difference in one-sidedness and confirm VPIN correctly ranks them:
 * a steadily-trending tape should score much higher than a tape that
 * oscillates back to its starting price every bucket.
 */
public class VPINCalculatorTest implements TestSuite {

    @Override public String name() { return "VPINCalculator (order-flow toxicity)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());
    }

    /** Builds a synthetic trade tape: `tradesPerBucket` trades of `qtyPerTrade` each, following `prices`. */
    private List<Trade> buildTape(double[] prices, int qtyPerTrade) {
        List<Trade> trades = new ArrayList<>();
        double t = 0.0;
        for (double price : prices) {
            long ticks = MarketConstants.priceToTicks(price);
            trades.add(new Trade(1, 2, "B", "S", ticks, qtyPerTrade, t, true));
            t += 1.0;
        }
        return trades;
    }

    private void testTrendingTapeScoresHigherThanOscillatingTape(TestReport report) {
        int bucketVolume = 100;
        int qtyPerTrade = 10; // 10 trades fill one bucket
        int windowBuckets = 3;

        /**
         * Trending tape: price rises by 1.0 every trade, 30 trades total (3 full buckets of 10 trades each).
         * Every bucket ends well above where it started -> strongly buy-classified -> high VPIN.
         */
        double[] trendingPrices = new double[30];
        for (int i = 0; i < 30; i++) trendingPrices[i] = 100.0 + i;
        List<Trade> trendingTape = buildTape(trendingPrices, qtyPerTrade);

        /**
         * Oscillating tape: price alternates +1/-1 around 100, engineered so
         * each 10-trade bucket ends at exactly the same price it started at
         * -> zero net price change per bucket -> low VPIN.
         */
        double[] oscillatingPrices = new double[30];
        for (int i = 0; i < 30; i++) oscillatingPrices[i] = 100.0 + (i % 2 == 0 ? 1.0 : 0.0);
        List<Trade> oscillatingTape = buildTape(oscillatingPrices, qtyPerTrade);

        VPINCalculator calc = new VPINCalculator(bucketVolume, windowBuckets);
        List<VPINCalculator.VpinPoint> trendingResult = calc.compute(trendingTape);
        List<VPINCalculator.VpinPoint> oscillatingResult = calc.compute(oscillatingTape);

        report.check(!trendingResult.isEmpty(), "trending tape produces at least one VPIN reading once enough buckets accumulate");
        report.check(!oscillatingResult.isEmpty(), "oscillating tape produces at least one VPIN reading once enough buckets accumulate");

        double trendingVpin = trendingResult.get(trendingResult.size() - 1).vpin;
        double oscillatingVpin = oscillatingResult.get(oscillatingResult.size() - 1).vpin;

        report.check(trendingVpin > oscillatingVpin,
                "a steadily one-sided (trending) trade tape scores a HIGHER VPIN than a tape that oscillates back to "
                        + "its starting price every bucket (trending=" + trendingVpin + ", oscillating=" + oscillatingVpin + ")");
        report.check(trendingVpin > 0.7,
                "the trending tape's VPIN is high in absolute terms, close to the theoretical maximum of 1.0 "
                        + "(actual: " + trendingVpin + ")");
        report.check(oscillatingVpin < 0.3,
                "the oscillating tape's VPIN is low in absolute terms, close to the theoretical minimum of 0.0 "
                        + "(actual: " + oscillatingVpin + ")");

        // VPIN is a ratio of volumes and must always land in [0, 1] by construction.
        for (VPINCalculator.VpinPoint p : trendingResult) {
            report.check(p.vpin >= 0.0 && p.vpin <= 1.0, "every VPIN reading stays within the valid [0,1] range");
        }
    }

    private void testEmptyInputProducesNoOutput(TestReport report) {
        VPINCalculator calc = new VPINCalculator(100, 3);
        List<VPINCalculator.VpinPoint> result = calc.compute(new ArrayList<>());
        report.check(result.isEmpty(), "an empty trade list produces an empty VPIN series (no divide-by-zero, no crash)");
    }

}
