package com.flashcrash.tests.agents;

import com.flashcrash.agents.LargeSeller;
import com.flashcrash.core.*;
import com.flashcrash.sim.SimulationContext;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Tests for LargeSeller: the percentage-of-volume execution algorithm that
 * models the real 2010 trigger event.
 *
 * Rather than routing background volume through a full multi-agent
 * simulation, these tests inject synthetic Trade objects directly into
 * ctx.tradeLog (a public field) to represent "volume traded by everyone
 * else." This isolates exactly the logic under test -- the participation
 * -rate arithmetic -- from the unrelated question of whether some other
 * agent's order-generation logic is also correct.
 */
public class LargeSellerTest implements TestSuite {

    @Override public String name() { return "LargeSeller (POV execution algorithm)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());

        testExecutesTargetParticipationOfBackgroundVolume(report);
        testCapsAtRemainingQuantity(report);
        testCapsAtMaxChunkPerPoll(report);
        testDeactivatesOnceFullyExecuted(report);
    }

    /** Adds a synthetic trade directly to the trade log, representing volume traded by other participants. */
    private void injectBackgroundVolume(SimulationContext ctx, int quantity, double timestamp) {
        ctx.tradeLog.add(new Trade(1, 2, "OTHER_BUYER", "OTHER_SELLER",
                MarketConstants.priceToTicks(1165.00), quantity, timestamp, true));
    }

    private void testExecutesTargetParticipationOfBackgroundVolume(TestReport report) {
        SimulationContext ctx = new SimulationContext(31);
        // Seed enough resting BUY liquidity for the seller's market sell to fully match.
        ctx.book.submit("COUNTERPARTY", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(1160.00), 1000, 0.0);

        // 5,000 contracts of background volume have traded since the program started.
        injectBackgroundVolume(ctx, 5000, 0.1);

        LargeSeller seller = new LargeSeller("BIGSELLER", 1000, 0.10 /* 10% target participation */,
                0.0, 10_000 /* maxChunk large enough not to bind */, 1.0);
        seller.act(0.0, ctx);

        // targetExecuted = round(0.10 * 5000) = 500
        report.checkEquals(seller.getExecutedQty(), 500L,
                "the seller sells exactly its target participation rate of observed background volume (10% of 5000 = 500)");
        report.checkEquals(seller.getRemainingQty(), 500L,
                "remaining quantity correctly reflects the executed amount (1000 - 500 = 500)");

        boolean sawExpectedTrade = ctx.tradeLog.stream()
                .anyMatch(t -> t.sellTraderId.equals("BIGSELLER") && t.quantity == 500);
        report.check(sawExpectedTrade,
                "a real trade for the computed quantity actually appears in the trade log (the order matched, not just internal bookkeeping)");
    }

    private void testCapsAtRemainingQuantity(TestReport report) {
        SimulationContext ctx = new SimulationContext(32);
        ctx.book.submit("COUNTERPARTY", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(1160.00), 1000, 0.0);

        /**
         * Enormous background volume would imply a target far larger than
         * the seller's total order size -- it must never sell more than it
         * actually has to sell.
         */
        injectBackgroundVolume(ctx, 1_000_000, 0.1);

        LargeSeller seller = new LargeSeller("BIGSELLER", 200 /* small total size */, 0.50,
                0.0, 10_000, 1.0);
        seller.act(0.0, ctx);

        report.checkEquals(seller.getExecutedQty(), 200L,
                "the seller never executes more than its configured total quantity, even if the participation target implies more");
        report.checkEquals(seller.getRemainingQty(), 0L, "remaining quantity correctly hits exactly zero, not negative");
    }

    private void testCapsAtMaxChunkPerPoll(TestReport report) {
        SimulationContext ctx = new SimulationContext(33);
        ctx.book.submit("COUNTERPARTY", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(1160.00), 1000, 0.0);

        injectBackgroundVolume(ctx, 10_000, 0.1); // would imply a target of 5,000 at 50% participation

        int maxChunk = 300;
        LargeSeller seller = new LargeSeller("BIGSELLER", 1000, 0.50, 0.0, maxChunk, 1.0);
        seller.act(0.0, ctx);

        report.checkEquals(seller.getExecutedQty(), (long) maxChunk,
                "a single poll never sells more than maxChunk contracts in one go, "
                        + "even when the participation-rate target would call for more (models a real "
                        + "algo's per-tick order-size limit)");
    }

    private void testDeactivatesOnceFullyExecuted(TestReport report) {
        SimulationContext ctx = new SimulationContext(34);
        ctx.book.submit("COUNTERPARTY", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(1160.00), 1000, 0.0);
        injectBackgroundVolume(ctx, 10_000, 0.1);

        LargeSeller seller = new LargeSeller("BIGSELLER", 100 /* small enough to finish in one poll */,
                0.50, 0.0, 10_000, 1.0);
        double next = seller.act(0.0, ctx);

        report.checkEquals(seller.getRemainingQty(), 0L, "the seller's small total order is fully executed in one poll");
        report.check(Double.isInfinite(next),
                "once fully executed, act() returns Double.POSITIVE_INFINITY so the scheduler never calls it again");
    }
}
