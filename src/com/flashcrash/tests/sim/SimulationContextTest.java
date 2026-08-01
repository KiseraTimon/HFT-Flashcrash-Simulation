package com.flashcrash.tests.sim;

import com.flashcrash.core.*;
import com.flashcrash.sim.SimulationContext;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Tests for SimulationContext: the shared "world state" every agent reads
 * from and writes to during a run (positions, trade log, trade-flow graph,
 * recorded time series). Almost every other module's correctness depends on
 * this bookkeeping being right, since e.g. the hot-potato network analysis
 * and the inventory half-life estimator both read data this class records.
 */
public class SimulationContextTest implements TestSuite {

    @Override public String name() { return "SimulationContext"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());

        testSeededReproducibility(report);
        testPositionTrackingFollowsTrades(report);
        testAggregateAndNetHftInventory(report);
        testTradeGraphIsSymmetric(report);
        testRecordSnapshotKeepsSeriesAligned(report);
    }

    private void testSeededReproducibility(TestReport report) {
        /**
         * The whole point of seeding the RNG explicitly (rather than using
         * an unseeded Random) is that two contexts built with the same seed
         * must produce an identical sequence of random draws -- this is
         * what makes a "seed=42 flagship run" a meaningful, repeatable
         * artefact rather than a one-off fluke.
         */
        SimulationContext ctx1 = new SimulationContext(42);
        SimulationContext ctx2 = new SimulationContext(42);
        double[] draws1 = new double[5];
        double[] draws2 = new double[5];
        for (int i = 0; i < 5; i++) draws1[i] = ctx1.rng.nextDouble();
        for (int i = 0; i < 5; i++) draws2[i] = ctx2.rng.nextDouble();
        boolean identical = true;
        for (int i = 0; i < 5; i++) if (draws1[i] != draws2[i]) identical = false;
        report.check(identical, "two SimulationContexts built with the same seed produce identical RNG draw sequences");

        SimulationContext ctx3 = new SimulationContext(43);

        // Direct check: the first draw from a different seed should (overwhelmingly likely) differ.
        report.check(ctx1.rng.nextDouble() != ctx3.rng.nextDouble(),
                "two SimulationContexts built with DIFFERENT seeds produce different RNG draws");
    }

    private void testPositionTrackingFollowsTrades(TestReport report) {
        SimulationContext ctx = new SimulationContext(1);
        /**
         * The trade listener that updates positions is wired up inside the
         * SimulationContext constructor (ctx.book.setTradeListener(this::onTrade)),
         * so submitting matching orders through ctx.book is the correct way
         * to exercise this, rather than calling any position-update method directly.
         */
        ctx.book.submit("SELLER", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(100.0), 10, 0.0);
        ctx.book.submit("BUYER", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(100.0), 10, 1.0);

        report.checkEquals(ctx.position("BUYER"), 10L, "buyer's position increases by the traded quantity");
        report.checkEquals(ctx.position("SELLER"), -10L, "seller's position decreases (goes short) by the traded quantity");
        report.checkEquals(ctx.position("NEVER_TRADED"), 0L, "a trader that never traded has a default position of 0");
        report.checkEquals(ctx.tradeLog.size(), 1L, "the trade is recorded in the shared trade log");
    }

    private void testAggregateAndNetHftInventory(TestReport report) {
        SimulationContext ctx = new SimulationContext(1);
        ctx.hftTraderIds.add("HFT-A");
        ctx.hftTraderIds.add("HFT-B");

        /**
         * Give HFT-A a long position of +30 and HFT-B a short position of -10,
         * by trading each of them against a neutral counterparty.
         */
        ctx.book.submit("COUNTERPARTY", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(100.0), 30, 0.0);
        ctx.book.submit("HFT-A", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(100.0), 30, 1.0);
        ctx.book.submit("HFT-B", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(100.0), 10, 2.0);
        ctx.book.submit("COUNTERPARTY2", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(100.0), 10, 3.0);

        report.checkEquals(ctx.position("HFT-A"), 30L, "HFT-A ends up long 30 contracts");
        report.checkEquals(ctx.position("HFT-B"), -10L, "HFT-B ends up short 10 contracts");

        // aggregateHftInventory sums the ABSOLUTE value of each HFT's position: |30| + |-10| = 40
        report.checkEquals(ctx.aggregateHftInventory(), 40L,
                "aggregate HFT inventory sums absolute positions across all registered HFT ids (|30|+|-10|=40)");

        // netHftInventory sums the SIGNED positions: 30 + (-10) = 20
        report.checkEquals(ctx.netHftInventory(), 20L,
                "net HFT inventory sums signed positions across all registered HFT ids (30+(-10)=20)");
    }

    private void testTradeGraphIsSymmetric(TestReport report) {
        SimulationContext ctx = new SimulationContext(1);
        ctx.book.submit("ALICE", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(50.0), 7, 0.0);
        ctx.book.submit("BOB", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(50.0), 7, 1.0);

        /**
         * The hot-potato network analyzer builds a directed graph itself
         * from ctx.tradeLog, but SimulationContext ALSO maintains its own
         * symmetric (undirected) adjacency map (tradeGraph) as a convenience
         * -- both directions should be recorded with the same weight.
         */
        Integer aliceToBob = ctx.tradeGraph.getOrDefault("ALICE", java.util.Map.of()).get("BOB");
        Integer bobToAlice = ctx.tradeGraph.getOrDefault("BOB", java.util.Map.of()).get("ALICE");
        report.check(aliceToBob != null && aliceToBob == 7, "trade graph records Alice<->Bob volume from Alice's side (7)");
        report.check(bobToAlice != null && bobToAlice == 7, "trade graph records the same volume from Bob's side (symmetric, 7)");
    }

    private void testRecordSnapshotKeepsSeriesAligned(TestReport report) {
        SimulationContext ctx = new SimulationContext(1);
        ctx.book.submit("S", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(101.0), 5, 0.0);
        ctx.book.submit("B", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(99.0), 5, 0.0);

        ctx.now = 10.0;
        ctx.recordSnapshot();
        ctx.now = 20.0;
        ctx.recordSnapshot();

        /**
         * Every recorder list must grow in lockstep -- if any single list
         * fell behind, every downstream analytics/ml class that indexes them
         * in parallel (e.g. FeatureExtractor) would silently misalign time
         * and value.
         */
        int n = ctx.sampleTimes.size();
        report.checkEquals(n, 2L, "two calls to recordSnapshot() produce two recorded samples");
        report.checkEquals(ctx.midPriceSeries.size(), (long) n, "midPriceSeries stays aligned with sampleTimes");
        report.checkEquals(ctx.bestBidSeries.size(), (long) n, "bestBidSeries stays aligned with sampleTimes");
        report.checkEquals(ctx.bestAskSeries.size(), (long) n, "bestAskSeries stays aligned with sampleTimes");
        report.checkEquals(ctx.hftAggregateInventorySeries.size(), (long) n, "hftAggregateInventorySeries stays aligned with sampleTimes");
        report.checkEquals(ctx.imbalanceSeries.size(), (long) n, "imbalanceSeries stays aligned with sampleTimes");

        report.checkEquals(ctx.sampleTimes.get(0), 10.0, 1e-9, "first snapshot recorded at the time it was taken");
        report.checkEquals(ctx.sampleTimes.get(1), 20.0, 1e-9, "second snapshot recorded at the time it was taken");
    }
}
