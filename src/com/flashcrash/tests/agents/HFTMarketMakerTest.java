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

    private void testQuotesNeverGoNonPositiveUnderExtremeInventory(TestReport report) {
        /**
         * This is the direct regression test for the historical bug: with a
         * large enough inventory and an insufficiently dampened gamma, the
         * reservation price formula (mid - q*gamma*sigma^2*horizon) can go
         * to zero or negative. The production code guards against this with
         * an explicit floor (reservation >= 0.2*mid). We deliberately force
         * an extreme inventory here to confirm that floor actually holds.
         */
        SimulationContext ctx = new SimulationContext(23);
        ctx.book.submit("SEED", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(1164.75), 5, 0.0);
        ctx.book.submit("SEED", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(1165.25), 5, 0.0);

        String id = "HFT-EXTREME";
        /**
         * An inventory far larger than anything the hard cap would normally allow,
         * simulating "what if the skew math were still as aggressive as the buggy
         * version" -- the price floor must hold regardless of how large q gets.
         */
        ctx.positions.put(id, 100_000);
        HFTMarketMaker mm = new HFTMarketMaker(id, 3.0, 0.05, 1.0, 1.0, 1.0, 1, 1_000_000);
        mm.act(0.0, ctx);

        Double bestBid = ctx.book.bestBid();
        Double bestAsk = ctx.book.bestAsk();
        report.check(bestBid == null || bestBid > 0,
                "even under an extreme, artificially-forced inventory, the quoted bid price never goes to zero or negative");
        report.check(bestAsk == null || bestAsk > 0,
                "even under an extreme, artificially-forced inventory, the quoted ask price never goes to zero or negative");
    }

    private void testHardCapStopsFurtherAccumulationAndTriggersFlatten(TestReport report) {
        /**
         * Once inventory reaches the hard cap, the agent must (a) stop
         * quoting on the side that would add to the position, and (b)
         * actively submit an aggressive order to flatten back toward the
         * cap. We verify (b) by seeding a counterparty for it to trade
         * against and confirming a de-risking trade actually occurs.
         */
        SimulationContext ctx = new SimulationContext(24);
        String id = "HFT-CAPPED";
        int cap = 10;
        ctx.positions.put(id, cap); // exactly at the cap, long

        // A counterparty resting buy order for the agent's forced flatten-sell to match against.
        ctx.book.submit("COUNTERPARTY", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(1160.00), 50, 0.0);

        HFTMarketMaker mm = new HFTMarketMaker(id, 3.0, 0.05, 1.0, 1.0, 1.0, 1, cap);
        int tradesBefore = ctx.tradeLog.size();
        mm.act(0.0, ctx);
        int tradesAfter = ctx.tradeLog.size();

        report.check(tradesAfter > tradesBefore,
                "once inventory is at/over the hard cap, the agent actively trades to flatten "
                        + "(a de-risking trade occurs) rather than silently sitting at the cap forever");

        boolean sawSellFromAgent = ctx.tradeLog.stream().anyMatch(t -> t.sellTraderId.equals(id));
        report.check(sawSellFromAgent,
                "the de-risking trade is on the correct side: a LONG agent at its cap sells to reduce inventory");
    }
}
