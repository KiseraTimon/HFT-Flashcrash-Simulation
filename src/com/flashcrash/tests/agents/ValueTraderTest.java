package com.flashcrash.tests.agents;

import com.flashcrash.agents.ValueTrader;
import com.flashcrash.core.*;
import com.flashcrash.sim.SimulationContext;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Tests for ValueTrader: compares the live market price to the shared
 * "fundamental value" (see FundamentalValueProcess) and trades to close
 * the gap. This is the agent responsible for stopping a simulated crash
 * from spiralling to zero.
 *
 * Why? Context:
 *      When these agents were first wired together (in the `feat/orchestration-*` branch),
 *      the simulated market didn't behave anything like a real one — first the price barely
 *      moved at all (0.1% drawdown vs. the real event's > 5%), then after a liquidity fix it
 *      spiralled to zero (drawdown "1451%," i.e. price went negative), then after a floor was
 *      added it still crashed to nearly nothing with no recovery (99.97%, no rebound) because
 *      nothing was anchoring price to a fundamental value. `feat/agents-fundamental-value
 *      -process` and `feat/agents-value-trader` were built specifically in response to that
 *      last failure. If you're wondering why the agent list looks the way it does, that
 *      sequence is why — it wasn't designed top-down, it was built until the simulated market
 *      actually behaved like a market.
 */
public class ValueTraderTest implements TestSuite {

    @Override public String name() { return "ValueTrader"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());
    }

    private void testBuysWhenPriceIsFarBelowFundamental(TestReport report) {
        SimulationContext ctx = new SimulationContext(51);

        /**
         * Market is trading around 1000, but "true value" is 1300 -- price
         * looks cheap, so the value trader should buy.
         */
        ctx.book.submit("COUNTERPARTY", OrderSide.SELL, OrderType.LIMIT, MarketConstants.priceToTicks(1000.25), 50, 0.0);
        ctx.book.submit("COUNTERPARTY", OrderSide.BUY, OrderType.LIMIT, MarketConstants.priceToTicks(999.75), 50, 0.0);
        ctx.fundamentalValue = 1300.0;

        ValueTrader trader = new ValueTrader("VALUE-TEST", 1.0, /*thresholdPct=*/ 1.0, /*sizePerPct=*/ 5.0, /*maxQty=*/ 40);
        trader.act(0.0, ctx);

        report.check(ctx.position("VALUE-TEST") > 0,
                "when price is far below fundamental value, the value trader ends up net LONG (it bought the 'cheap' asset)");
    }

}
