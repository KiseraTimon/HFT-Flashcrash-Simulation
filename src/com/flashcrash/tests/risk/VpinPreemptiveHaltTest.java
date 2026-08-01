package com.flashcrash.tests.risk;

import com.flashcrash.core.MarketConstants;
import com.flashcrash.core.Trade;
import com.flashcrash.risk.VpinPreemptiveHalt;
import com.flashcrash.sim.SimulationContext;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Tests for VpinPreemptiveHalt: the proactive, order-flow-toxicity-based
 * circuit breaker -- the intervention this whole project's headline finding
 * is built around (it substantially outperforms the reactive LULD breaker
 * in the Monte Carlo comparison in Main). We inject synthetic trades
 * directly into ctx.tradeLog to control exactly how "toxic" the order flow
 * looks, the same technique used in VPINCalculatorTest.
 */
public class VpinPreemptiveHaltTest implements TestSuite {

    @Override public String name() { return "VpinPreemptiveHalt (proactive toxicity-based halt)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());
    }

    /** Adds `count` synthetic trades to the trade log, prices following the given sequence. */
    private void addTrades(SimulationContext ctx, double[] prices, int qtyPerTrade, double startTime) {
        double t = startTime;
        for (double price : prices) {
            ctx.tradeLog.add(new Trade(1, 2, "B", "S", MarketConstants.priceToTicks(price), qtyPerTrade, t, true));
            t += 0.01;
        }
    }

    private void testHaltsOnOneSidedOrderFlow(TestReport report) {
        SimulationContext ctx = new SimulationContext(71);

        /**
         * 30 trades, price rising steadily -- strongly one-sided, as in
         * VPINCalculatorTest's "trending" case, which scored VPIN > 0.7 there.
         */
        double[] prices = new double[30];
        for (int i = 0; i < 30; i++) prices[i] = 100.0 + i;
        addTrades(ctx, prices, 5, 0.0); // bucketVolume=50 below -> 10 trades of qty 5 fill one bucket

        VpinPreemptiveHalt halt = new VpinPreemptiveHalt(50, 3, 0.5, 20.0, 1.0);
        ctx.now = 0.0;
        halt.evaluate(ctx);

        report.check(ctx.tradingHalted, "one-sided (trending) order flow crosses the VPIN threshold and triggers a halt");
        report.checkEquals(halt.triggerCount, 1L, "trigger count increments exactly once");
        report.check(halt.lastVpin > 0.5, "the recorded VPIN value is indeed above the configured threshold");
    }

    private void testDoesNotHaltOnBalancedOrderFlow(TestReport report) {
        SimulationContext ctx = new SimulationContext(72);

        /**
         * Oscillating prices (as in VPINCalculatorTest) score a low VPIN --
         * should stay comfortably under a 0.5 threshold.
         */
        double[] prices = new double[30];
        for (int i = 0; i < 30; i++) prices[i] = 100.0 + (i % 2 == 0 ? 1.0 : 0.0);
        addTrades(ctx, prices, 5, 0.0);

        VpinPreemptiveHalt halt = new VpinPreemptiveHalt(50, 3, 0.5, 20.0, 1.0);
        ctx.now = 0.0;
        halt.evaluate(ctx);

        report.check(!ctx.tradingHalted, "balanced (oscillating) order flow does not trigger a halt");
        report.checkEquals(halt.triggerCount, 0L, "trigger count stays at zero for balanced order flow");
    }

}
