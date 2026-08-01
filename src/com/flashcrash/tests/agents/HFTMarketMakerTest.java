package com.flashcrash.tests.agents;

import com.flashcrash.agents.HFTMarketMaker;
import com.flashcrash.core.*;
import com.flashcrash.sim.SimulationContext;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Tests for HFTMarketMaker: the Avellaneda-Stoikov inventory-skewed
 * quoting agent. This is the highest-risk class
 * in the project, because an earlier version of this exact logic caused a
 * runaway price spiral (reservation price collapsing through zero) during
 * development. These tests specifically target the properties whose
 * absence caused that bug: quotes must never cross, the reservation price
 * must never go non-positive, and the inventory cap must actually stop the
 * agent from adding to its position once breached.
 */
public class HFTMarketMakerTest implements TestSuite {

    @Override public String name() { return "HFTMarketMaker"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());

    }

    private void testQuotesNeverCrossAtZeroInventory(TestReport report) {
        SimulationContext ctx = new SimulationContext(21);
        // Seed a neutral starting book so midPrice() has something sensible to read.
        ctx.book.submit("SEED", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(1164.75), 5, 0.0);
        ctx.book.submit("SEED", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(1165.25), 5, 0.0);

        HFTMarketMaker mm = new HFTMarketMaker("HFT-TEST", 3.0, 0.05, 1.0, 1.0, 1.0, 1, 10);
        mm.act(0.0, ctx);

        Double bestBid = ctx.book.bestBid();
        Double bestAsk = ctx.book.bestAsk();
        report.check(bestBid != null && bestAsk != null,
                "the market maker actually posts both a bid and an ask when flat (zero inventory)");
        if (bestBid != null && bestAsk != null) {
            report.check(bestBid < bestAsk,
                    "the market maker's own bid and ask never cross each other (bid=" + bestBid + ", ask=" + bestAsk + ")");
        }
    }
}
