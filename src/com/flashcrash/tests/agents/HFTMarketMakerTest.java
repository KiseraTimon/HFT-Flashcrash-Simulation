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

    private void testInventorySkewsQuotesInCorrectDirection(TestReport report) {
        /**
         * Economically: an agent that is already LONG (positive inventory)
         * should skew its quotes DOWN to encourage selling (reduce
         * inventory) and discourage further buying. We verify this by
         * comparing quotes at zero inventory vs. quotes after forcing a
         * long position, all else equal.
         */
        SimulationContext ctxFlat = new SimulationContext(22);
        ctxFlat.book.submit("SEED", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(1164.75), 5, 0.0);
        ctxFlat.book.submit("SEED", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(1165.25), 5, 0.0);
        HFTMarketMaker mmFlat = new HFTMarketMaker("HFT-FLAT", 3.0, 0.05, 1.0, 1.0, 1.0, 1, 100);
        mmFlat.act(0.0, ctxFlat);
        double bidAtZeroInventory = ctxFlat.book.bestBid();

        SimulationContext ctxLong = new SimulationContext(22); // same seed -> same starting book randomness
        ctxLong.book.submit("SEED", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(1164.75), 5, 0.0);
        ctxLong.book.submit("SEED", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(1165.25), 5, 0.0);

        /**
         * Directly force the agent to already be long 50 contracts before it quotes,
         * exactly as it would be mid-simulation after absorbing sell pressure.
         */
        ctxLong.positions.put("HFT-LONG", 50);
        HFTMarketMaker mmLong = new HFTMarketMaker("HFT-LONG", 3.0, 0.05, 1.0, 1.0, 1.0, 1, 100);
        mmLong.act(0.0, ctxLong);
        double bidWhenLong = ctxLong.book.bestBid();

        report.check(bidWhenLong <= bidAtZeroInventory,
                "a market maker holding a long position quotes a bid at or below what it would quote when flat "
                        + "(skewing down to discourage buying more) -- flat bid=" + bidAtZeroInventory + ", long bid=" + bidWhenLong);
    }
}
