package com.flashcrash.tests.core;

import com.flashcrash.core.*;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Covers the small, mostly-non-algorithmic building blocks of the {@code core}
 * package: the two enums and the two plain data classes. These have very
 * little logic, but the little they do have (OrderSide.opposite(), and
 * Order/Trade's derived getters) is exactly the kind of one-line-typo bug
 * that's cheap to catch here and expensive to debug three modules downstream.
 */
public class CoreTypesTest implements TestSuite {

    @Override public String name() { return "CoreTypes (OrderSide/OrderType/Order/Trade)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());

        /** OrderSide.opposite()
         * Every matching-engine call site that looks up "the other side of the
         * book" depends on this being correct in both directions.
         */
        report.check(OrderSide.BUY.opposite() == OrderSide.SELL,
                "BUY.opposite() is SELL");
        report.check(OrderSide.SELL.opposite() == OrderSide.BUY,
                "SELL.opposite() is BUY");

        /** OrderType
         * No behaviour to test (a pure marker enum), but we confirm both
         * values exist and are distinct, since OrderBook.match() branches on
         * `incoming.type == OrderType.MARKET` and a typo here would silently
         * make every order behave like the other type.
         */
        report.check(OrderType.LIMIT != OrderType.MARKET,
                "LIMIT and MARKET are distinct enum constants");

        /** Order.isFilled()
         * isFilled() is used throughout OrderBook to decide when to pop a
         * resting order off its price-level queue.
         */
        Order freshOrder = new Order(1, "T1", OrderSide.BUY, OrderType.LIMIT,
                MarketConstants.priceToTicks(100.0), 10, 0.0);
        report.check(!freshOrder.isFilled(),
                "a freshly created order with remainingQty=originalQty is not filled");

        freshOrder.remainingQty = 0;
        report.check(freshOrder.isFilled(),
                "an order with remainingQty=0 is filled");

        freshOrder.remainingQty = -1; // shouldn't happen in practice, but isFilled() should be defensive (<=0)
        report.check(freshOrder.isFilled(),
                "isFilled() treats remainingQty<0 as filled too (defensive: <=0, not ==0)");

        /** Trade.price()
         * Confirms Trade correctly delegates to MarketConstants for the
         * tick -> price conversion rather than duplicating (and potentially
         * desyncing from) that logic.
         */
        long ticks = MarketConstants.priceToTicks(1165.25);
        Trade trade = new Trade(1, 2, "BUYER", "SELLER", ticks, 5, 12.34, true);
        report.checkEquals(trade.price(), 1165.25, 1e-9,
                "Trade.price() converts ticks back to the correct dollar price");
    }
}
