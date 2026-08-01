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

}
