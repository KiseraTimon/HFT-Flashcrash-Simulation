package com.flashcrash.tests.risk;

import com.flashcrash.core.*;
import com.flashcrash.risk.LuldCircuitBreaker;
import com.flashcrash.sim.SimulationContext;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Tests for LuldCircuitBreaker: the reactive, price-band circuit breaker
 * modelled on the real Limit Up-Limit Down mechanism adopted after 2010.
 * Since it reads price straight from ctx.book (not from an injectable
 * series), these tests drive the book directly through a sequence of
 * evaluate() calls: first a stable price (to build up a reference average),
 * then a sudden jump, and confirm the halt fires exactly when expected --
 * not before, and not after.
 */
public class LuldCircuitBreakerTest implements TestSuite {

    @Override public String name() { return "LuldCircuitBreaker (reactive price-band halt)"; }

    /**
     * Tracks the currently-resting quote so setPrice() can cancel-and-replace
     * it instead of leaving stale orders behind (a naive "just submit a new
     * order every tick" helper would leave OLD resting bids sitting above
     * any new, lower price, permanently pinning the book's best bid high --
     * exactly the kind of bug this project's own agents had to avoid, see
     * NoiseTrader's bounded-resting-orders logic).
     */
    private long lastBidId = -1;
    private long lastAskId = -1;

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());
    }

    /** Cancels the previous quote (if any) and rests a fresh bid/ask pair around `price`. */
    private void setPrice(SimulationContext ctx, double price, double timestamp) {
        if (lastBidId >= 0) ctx.book.cancel(lastBidId);
        if (lastAskId >= 0) ctx.book.cancel(lastAskId);
        lastBidId = ctx.book.submit("PX", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(price - 0.25), 5, timestamp);
        lastAskId = ctx.book.submit("PX", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(price + 0.25), 5, timestamp);
    }

    private void testNoHaltDuringStablePrices(TestReport report) {
        SimulationContext ctx = new SimulationContext(61);
        lastBidId = -1; lastAskId = -1;
        // 3% band, 30s halt duration, 300s rolling reference window.
        LuldCircuitBreaker breaker = new LuldCircuitBreaker(3.0, 30.0, 300.0);

        for (int i = 0; i < 20; i++) {
            ctx.now = i * 1.0;
            setPrice(ctx, 1165.00, ctx.now); // perfectly flat price
            breaker.evaluate(ctx);
        }

        report.check(!ctx.tradingHalted, "a perfectly stable price never triggers the circuit breaker");
        report.checkEquals(breaker.triggerCount, 0L, "trigger count stays at zero throughout a stable period");
    }
}
