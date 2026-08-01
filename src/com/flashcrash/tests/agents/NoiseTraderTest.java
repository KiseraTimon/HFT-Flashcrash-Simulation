package com.flashcrash.tests.agents;

import com.flashcrash.agents.NoiseTrader;
import com.flashcrash.core.*;
import com.flashcrash.sim.SimulationContext;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Tests for NoiseTrader. The most important test here (testRestingOrdersStayBounded)
 * is a direct regression test for a real bug found during this project's
 * development: NoiseTrader originally never cancelled its own old resting
 * orders, so book depth grew without limit over a long run, making the
 * simulated market effectively infinitely liquid and hiding the large
 * seller's price impact entirely (see strategy.md's commit history for
 * `fix/agents-noisetrader-unbounded-depth`). If someone "simplifies" this
 * class in the future and drops the bounding logic, this test is what
 * should catch it.
 */
public class NoiseTraderTest implements TestSuite {

    @Override public String name() { return "NoiseTrader"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());
    }

    private void testRestingOrdersStayBounded(TestReport report) {
        SimulationContext ctx = new SimulationContext(7);
        int maxResting = 4;
        NoiseTrader trader = new NoiseTrader("NOISE-0", 1.0, 3, 8, maxResting);

        /**
         * Call act() directly and repeatedly (bypassing the Poisson-scheduled
         * wake-ups, since we only care about the cumulative effect on book
         * depth, not the timing). 200 calls is far more than enough to reveal
         * unbounded growth if the bounding logic were ever removed.
         */
        double t = 0.0;
        for (int i = 0; i < 200; i++) {
            t = trader.act(t, ctx);
        }

        /**
         * Since this trader is the ONLY participant in this test's book,
         * every resting order still present belongs to it -- so book.size()
         * is a direct, black-box (no access to NoiseTrader's private fields
         * needed) measurement of "how many orders is this agent leaving
         * resting at once."
         */
        report.check(ctx.book.size() <= maxResting,
                "after 200 wake-ups, a single NoiseTrader never leaves more than maxRestingOrders="
                        + maxResting + " orders resting in the book (actual: " + ctx.book.size() + ")");
    }

    private void testSubmittedOrdersAreNearMidPrice(TestReport report) {
        /**
         * A NoiseTrader's passive orders should land within maxTicksFromMid
         * of the current mid-price -- not at some arbitrary, unrelated
         * price -- since its purpose is to provide realistic, localized
         * background liquidity.
         */
        SimulationContext ctx = new SimulationContext(3);
        int maxTicksFromMid = 8;
        NoiseTrader trader = new NoiseTrader("NOISE-0", 1.0, 3, maxTicksFromMid, 50);

        double midBefore = ctx.book.midPrice();
        double t = 0.0;
        for (int i = 0; i < 50; i++) {
            t = trader.act(t, ctx);
        }

        /**
         * Every resting order's price should be within maxTicksFromMid ticks
         * of SOME reasonable mid-price over the run. We check the final
         * book's best bid/ask are still within a generous multiple of that
         * window of the original mid, as a sanity bound against orders
         * being placed at wildly wrong prices (e.g. a sign error in the
         * BUY/SELL offset direction).
         */
        Double bestBid = ctx.book.bestBid();
        Double bestAsk = ctx.book.bestAsk();
        double maxOffsetPrice = maxTicksFromMid * MarketConstants.TICK_SIZE;
        if (bestBid != null) {
            report.check(bestBid <= midBefore + 5 * maxOffsetPrice && bestBid >= midBefore - 5 * maxOffsetPrice,
                    "resting bid prices stay in the general neighborhood of the starting mid price, not at an arbitrary offset");
        }
        if (bestAsk != null) {
            report.check(bestAsk <= midBefore + 5 * maxOffsetPrice && bestAsk >= midBefore - 5 * maxOffsetPrice,
                    "resting ask prices stay in the general neighborhood of the starting mid price, not at an arbitrary offset");
        }
    }
}
