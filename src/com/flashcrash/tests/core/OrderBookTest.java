package com.flashcrash.tests.core;

import com.flashcrash.core.*;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for OrderBook: the price-time priority continuous double auction
 * matching engine that every other module in this project ultimately sits
 * on top of. If this class has a bug, it doesn't show up as "OrderBook is
 * wrong" -- it shows up three modules away as "the simulated market behaves
 * strangely," exactly the kind of bug this project spent real debugging
 * time on (see strategy.md, the price-spiral incident). These tests exist
 * to catch that class of problem at the source instead.
 *
 * Each test builds a fresh OrderBook (matching engines are stateful, so
 * sharing one across tests would let earlier tests contaminate later ones).
 */
public class OrderBookTest implements TestSuite {

    @Override public String name() { return "OrderBook (matching engine)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());

        testEmptyBook(report);
        testRestingLimitOrder(report);
        testSimpleCross(report);
        testPriceTimePriority(report);
        testPartialFillSweepsMultipleLevels(report);
        testMarketOrderIgnoresLimitPriceButRespectsAvailableLiquidity(report);
        testMarketOrderWithNoLiquidityGoesUnfilled(report);
        testCancel(report);
        testCancelNonexistentOrderReturnsFalse(report);
        testDepthAndImbalance(report);
        testBookNeverCrosses(report);
    }

    private void testEmptyBook(TestReport report) {
        OrderBook book = new OrderBook();
        report.check(book.bestBid() == null, "empty book has no best bid");
        report.check(book.bestAsk() == null, "empty book has no best ask");

        /**
         * With no resting orders and no trades yet, midPrice() falls back to
         * lastTradePrice, which OrderBook initializes to MarketConstants.OPENING_PRICE.
         */
        report.checkEquals(book.midPrice(), MarketConstants.OPENING_PRICE, 1e-9,
                "empty book's mid price falls back to the initial last-trade price");
        report.checkEquals(book.size(), 0L, "empty book has zero resting orders");
    }

    private void testRestingLimitOrder(TestReport report) {
        OrderBook book = new OrderBook();

        // A single BUY limit order with nothing to match against should simply rest.
        book.submit("T1", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(100.00), 10, 0.0);
        report.checkEquals(book.bestBid(), 100.00, 1e-9,
                "a resting BUY limit order becomes the best bid");
        report.check(book.bestAsk() == null, "no SELL orders submitted yet -> no best ask");
        report.checkEquals(book.size(), 1L, "one resting order in the book");
    }

    private void testSimpleCross(TestReport report) {
        /**
         * A resting SELL at 100.00, then an incoming BUY at 100.00 should
         * match in full at the resting order's price.
         */
        OrderBook book = new OrderBook();
        List<Trade> trades = new ArrayList<>();
        book.setTradeListener(trades::add);

        book.submit("SELLER", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(100.00), 10, 0.0);
        book.submit("BUYER", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(100.00), 10, 1.0);

        report.checkEquals(trades.size(), 1L, "exactly one trade results from a fully-crossing pair of orders");
        Trade t = trades.get(0);
        report.checkEquals(t.price(), 100.00, 1e-9, "trade executes at the resting order's price");
        report.checkEquals(t.quantity, 10L, "trade quantity matches both orders' full size");
        report.checkEquals(t.buyTraderId, "BUYER", "buy side of the trade is the incoming buyer");
        report.checkEquals(t.sellTraderId, "SELLER", "sell side of the trade is the resting seller");
        report.check(t.buyIsAggressor, "the incoming BUY order is correctly flagged as the aggressor (liquidity taker)");
        report.checkEquals(book.size(), 0L, "both orders are fully filled -> nothing left resting");
    }

    private void testPriceTimePriority(TestReport report) {
        /**
         * Two resting SELL orders at the SAME price, submitted in a known
         * order. An incoming BUY that only partially fills must match the
         * EARLIER-arriving resting order first (time priority within a
         * price level) -- this is the property that makes it "price-TIME
         * priority" and not just "price priority."
         */
        OrderBook book = new OrderBook();
        List<Trade> trades = new ArrayList<>();
        book.setTradeListener(trades::add);

        long px = MarketConstants.priceToTicks(50.00);
        book.submit("FIRST", OrderSide.SELL, OrderType.LIMIT, px, 5, 0.0);   // arrives first
        book.submit("SECOND", OrderSide.SELL, OrderType.LIMIT, px, 5, 1.0); // arrives second, same price

        // Incoming buy for only 5 -- should match FIRST entirely and leave SECOND untouched.
        book.submit("BUYER", OrderSide.BUY, OrderType.LIMIT, px, 5, 2.0);

        report.checkEquals(trades.size(), 1L, "one trade for the partial-size incoming buy");
        report.checkEquals(trades.get(0).sellTraderId, "FIRST",
                "the earlier-arriving resting order at the same price fills before the later one (time priority)");
        report.checkEquals(book.size(), 1L, "SECOND's order is still resting, untouched");
        report.checkEquals(book.bestAsk(), 50.00, 1e-9, "best ask is still 50.00 (SECOND's price)");
    }

    private void testPartialFillSweepsMultipleLevels(TestReport report) {
        /**
         * A single large incoming market-style buy should sweep through
         * multiple price levels in ascending price order until filled.
         */
        OrderBook book = new OrderBook();
        List<Trade> trades = new ArrayList<>();
        book.setTradeListener(trades::add);

        book.submit("S1", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(100.00), 5, 0.0);
        book.submit("S2", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(100.25), 5, 0.0);
        book.submit("S3", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(100.50), 5, 0.0);

        // Buy 12 total: should take all 5 @ 100.00, all 5 @ 100.25, and 2 @ 100.50.
        book.submit("BUYER", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(100.50), 12, 1.0);

        report.checkEquals(trades.size(), 3L, "sweeping 3 price levels produces 3 separate trades");
        report.checkEquals(trades.get(0).price(), 100.00, 1e-9, "cheapest level fills first (best price priority)");
        report.checkEquals(trades.get(1).price(), 100.25, 1e-9, "second-cheapest level fills next");
        report.checkEquals(trades.get(2).price(), 100.50, 1e-9, "most expensive (but still acceptable) level fills last");
        report.checkEquals(trades.get(2).quantity, 2L, "the sweep only takes the 2 contracts needed to finish filling, not all 5 resting there");
        report.checkEquals(book.size(), 1L, "3 remaining contracts of S3's order are still resting");
        report.checkEquals(book.bestAsk(), 100.50, 1e-9, "best ask is now S3's partially-filled level");
    }

    private void testMarketOrderIgnoresLimitPriceButRespectsAvailableLiquidity(TestReport report) {
        OrderBook book = new OrderBook();
        List<Trade> trades = new ArrayList<>();
        book.setTradeListener(trades::add);

        book.submit("SELLER", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(9999.00), 5, 0.0);
        /**
         * A MARKET buy has no price of its own -- it must match regardless of
         * how far away the resting price is, unlike a LIMIT order would.
         */
        book.submit("BUYER", OrderSide.BUY, OrderType.MARKET, 0, 5, 1.0);

        report.checkEquals(trades.size(), 1L, "a market order matches even against a resting order far from the current price");
        report.checkEquals(trades.get(0).price(), 9999.00, 1e-9, "trade still executes at the resting order's price, not some other price");
    }

    private void testMarketOrderWithNoLiquidityGoesUnfilled(TestReport report) {
        OrderBook book = new OrderBook();
        List<Trade> trades = new ArrayList<>();
        book.setTradeListener(trades::add);

        // No resting sell orders at all -- a market buy has nothing to match against.
        book.submit("BUYER", OrderSide.BUY, OrderType.MARKET, 0, 10, 0.0);

        report.checkEquals(trades.size(), 0L, "a market order with zero available liquidity produces no trades");
        report.checkEquals(book.size(), 0L,
                "an unfilled MARKET order does NOT get left resting in the book (only LIMIT orders rest) -- "
                        + "this models real markets, where an unfilled market order simply expires");
    }

    private void testCancel(TestReport report) {
        OrderBook book = new OrderBook();
        long id = book.submit("T1", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(100.00), 10, 0.0);
        report.checkEquals(book.size(), 1L, "order is resting before cancellation");

        boolean cancelled = book.cancel(id);
        report.check(cancelled, "cancel() returns true for a real, currently-resting order");
        report.checkEquals(book.size(), 0L, "order is gone from the book after cancellation");
        report.check(book.bestBid() == null, "best bid is cleared once the only resting order is cancelled");
    }

    private void testCancelNonexistentOrderReturnsFalse(TestReport report) {
        OrderBook book = new OrderBook();
        report.check(!book.cancel(999999L),
                "cancelling an order id that was never submitted returns false rather than throwing");

        long id = book.submit("T1", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(100.00), 10, 0.0);
        book.cancel(id);
        report.check(!book.cancel(id),
                "cancelling the same order id twice returns false the second time (already gone)");
    }

    private void testDepthAndImbalance(TestReport report) {
        OrderBook book = new OrderBook();

        // 3 buy levels totalling 3+4+5=12, 2 sell levels totalling 6+7=13.
        book.submit("B1", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(99.75), 3, 0.0);
        book.submit("B2", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(99.50), 4, 0.0);
        book.submit("B3", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(99.25), 5, 0.0);
        book.submit("S1", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(100.00), 6, 0.0);
        book.submit("S2", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(100.25), 7, 0.0);

        report.checkEquals(book.depth(3), 25L,
                "depth(3) sums resting quantity across the top 3 levels on each side (12 bid + 13 ask = 25)");
        report.checkEquals(book.depth(1), 9L,
                "depth(1) only counts the single best level on each side (3 bid + 6 ask = 9)");

        // imbalance = (bidDepth - askDepth) / (bidDepth + askDepth), using top-3 levels: (12-13)/25 = -0.04
        report.checkEquals(book.imbalance(3), -1.0 / 25.0, 1e-9,
                "order book imbalance over top 3 levels matches the hand-computed value (12 bid, 13 ask)");
    }

    private void testBookNeverCrosses(TestReport report) {
        /**
         * If the matching algorithm is correct, after any sequence of
         * submissions the best bid must always be strictly less than the
         * best ask -- otherwise there would have been a trade instead.
         */
        OrderBook book = new OrderBook();
        book.submit("B1", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(100.00), 5, 0.0);
        book.submit("S1", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(100.50), 5, 0.0);

        /**
         * A buy that could partially cross: it should eat into the ask level down to
         * the extent it can, and never leave a resting bid >= the best ask.
         */
        book.submit("B2", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(100.75), 2, 0.0);

        Double bestBid = book.bestBid();
        Double bestAsk = book.bestAsk();
        if (bestBid != null && bestAsk != null) {
            report.check(bestBid < bestAsk,
                    "after matching resolves, the book never leaves a crossed best bid/ask (bid=" + bestBid + ", ask=" + bestAsk + ")");
        } else {
            report.check(true, "one side of the book emptied out entirely -- trivially non-crossed");
        }
    }
}
