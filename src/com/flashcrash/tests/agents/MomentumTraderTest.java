package com.flashcrash.tests.agents;

import com.flashcrash.agents.MomentumTrader;
import com.flashcrash.core.*;
import com.flashcrash.sim.SimulationContext;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Tests for MomentumTrader: the moving-average-crossover feedback trader
 * responsible (along with the HFT market makers) for amplifying the large
 * seller's initial price impact into a larger move.
 *
 * MomentumTrader's fast/slow moving averages are private state built up
 * from repeated calls to midPrice() -- there's no public getter for them,
 * by design (we test observable behaviour through the public API, not
 * internals). So these tests drive the shared order book through a
 * deliberately engineered price path (steadily falling, then perfectly
 * flat) and check the OBSERVABLE consequence: does the trader actually
 * submit a market order in the expected direction, or not at all.
 */
public class MomentumTraderTest implements TestSuite {

    @Override public String name() { return "MomentumTrader"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());
    }

    /**
     * Repeatedly submits a fresh (BUY, SELL) quote pair around a target
     * price that moves by `stepPerIteration` each call, then invokes the
     * momentum trader once per step. This both feeds the trader's moving
     * averages a clean trend AND leaves resting counterparty liquidity for
     * the trader's eventual market order to match against.
     */
    private void driveTrendAndTrade(SimulationContext ctx, MomentumTrader trader, double startPrice,
                                     double stepPerIteration, int iterations) {
        double t = 0.0;
        for (int i = 0; i < iterations; i++) {
            double target = startPrice + stepPerIteration * i;
            long bidTicks = MarketConstants.priceToTicks(target - 0.25);
            long askTicks = MarketConstants.priceToTicks(target + 0.25);
            ctx.book.submit("MARKETMAKER", OrderSide.BUY, OrderType.LIMIT, bidTicks, 20, t);
            ctx.book.submit("MARKETMAKER", OrderSide.SELL, OrderType.LIMIT, askTicks, 20, t);
            t = trader.act(t, ctx);
        }
    }

    private void testDetectsUptrendAndBuys(TestReport report) {
        SimulationContext ctx = new SimulationContext(12);
        MomentumTrader trader = new MomentumTrader("MOM-TEST", 1.0, 3, 6, 0.01, 5);

        // Price rises steadily from 100.00 up by 1.00 each step.
        driveTrendAndTrade(ctx, trader, 100.00, 1.00, 12);

        report.check(ctx.position("MOM-TEST") > 0,
                "in a steadily rising market, the momentum trader ends up net LONG "
                        + "(it bought into the rally) -- actual position: " + ctx.position("MOM-TEST"));
    }

    private void testFlatPriceProducesNoTrades(TestReport report) {
        SimulationContext ctx = new SimulationContext(13);
        MomentumTrader trader = new MomentumTrader("MOM-TEST", 1.0, 3, 6, 0.01, 5);

        /**
         * Price never moves -- fast and slow moving averages should stay
         * equal (or very nearly so), so the trader should never cross its
         * (very sensitive, 0.01%) trigger threshold in either direction.
         */
        driveTrendAndTrade(ctx, trader, 100.00, 0.00, 12);

        report.checkEquals(ctx.position("MOM-TEST"), 0L,
                "with a perfectly flat price history, the momentum trader never trades (no false-positive signal)");
    }
}
