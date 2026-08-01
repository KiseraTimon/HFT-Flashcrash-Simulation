package com.flashcrash.tests.core;

import com.flashcrash.core.MarketConstants;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Covers MarketConstants' tick <-> price conversion. This is small, but it
 * is load-bearing: the entire OrderBook works internally in integer ticks
 * specifically to avoid floating-point rounding errors during price
 * comparisons, so a bug here (e.g. an off-by-one-tick rounding error) would
 * silently corrupt price-time priority throughout the matching engine.
 */
public class MarketConstantsTest implements TestSuite {

    @Override public String name() { return "MarketConstants"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());

        /** ticksToPrice:
         * straightforward multiplication by tick size (0.25)
         */
        report.checkEquals(MarketConstants.ticksToPrice(4), 1.0, 1e-9,
                "4 ticks * 0.25 tick size = 1.00");
        report.checkEquals(MarketConstants.ticksToPrice(4660), 1165.0, 1e-9,
                "4660 ticks = 1165.00 (the simulation's opening price)");
        report.checkEquals(MarketConstants.ticksToPrice(0), 0.0, 1e-9,
                "0 ticks = 0.00");

        /** priceToTicks:
         * division by tick size, rounded to the nearest tick
         */
        report.checkEquals(MarketConstants.priceToTicks(1165.00), 4660L,
                "1165.00 converts to exactly 4660 ticks");
        report.checkEquals(MarketConstants.priceToTicks(1165.10), 4660L,
                "1165.10 rounds down to the nearest tick boundary (4660 -> 1165.00), since 1165.10/0.25=4660.40 is closer to 4660");
        report.checkEquals(MarketConstants.priceToTicks(1165.20), 4661L,
                "1165.20 rounds UP to the nearest tick boundary (4661 -> 1165.25), since 1165.20/0.25=4660.80 is closer to 4661");

        /** Round-trip consistency:
         * For any price that already sits exactly on a tick boundary,
         * converting to ticks and back must return the original price
         * exactly (no drift). This is the property that makes the engine's
         * internal integer-tick representation safe to use.
         */
        double[] exactTickPrices = {0.0, 0.25, 1.00, 1164.75, 1165.00, 1165.25, 9999.75};
        for (double p : exactTickPrices) {
            long ticks = MarketConstants.priceToTicks(p);
            double roundTripped = MarketConstants.ticksToPrice(ticks);
            report.checkEquals(roundTripped, p, 1e-9,
                    "round-trip priceToTicks(ticksToPrice(" + p + ")) is lossless for exact tick boundaries");
        }

        report.checkEquals(MarketConstants.TICK_SIZE, 0.25, 1e-9,
                "tick size matches the real E-mini S&P 500 contract's minimum price increment");
    }
}
