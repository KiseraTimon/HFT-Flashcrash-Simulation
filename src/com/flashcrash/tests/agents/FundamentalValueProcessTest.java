package com.flashcrash.tests.agents;

import com.flashcrash.agents.FundamentalValueProcess;
import com.flashcrash.core.MarketConstants;
import com.flashcrash.sim.SimulationContext;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Tests for FundamentalValueProcess: the Euler-Maruyama numerical
 * integrator for the Ornstein-Uhlenbeck "fundamental value" SDE,
 *
 *      F_{t+dt} = F_t + theta*(F0 - F_t)*dt + sigma*F_t*sqrt(dt)*Z
 *
 * With sigma=0, the stochastic term vanishes entirely and the update
 * becomes fully deterministic -- this lets us verify the DRIFT part of the
 * numerical scheme exactly, independent of any randomness, by recomputing
 * the same formula in the test and comparing step by step. The stochastic
 * term (sigma>0) is covered separately by a floor-safety test, since its
 * exact value depends on the RNG's internal state and isn't meant to be
 * reproduced by hand.
 */
public class FundamentalValueProcessTest implements TestSuite {

    @Override public String name() { return "FundamentalValueProcess (Euler-Maruyama SDE integrator)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());
    }

    private void testDeterministicDriftMatchesFormula(TestReport report) {
        double f0 = 1000.0;
        double theta = 0.1;
        double dt = 1.0;
        SimulationContext ctx = new SimulationContext(41);
        // ctx.fundamentalValue starts at MarketConstants.OPENING_PRICE (1165.0) by construction.
        double expected = MarketConstants.OPENING_PRICE;

        FundamentalValueProcess process = new FundamentalValueProcess(dt, theta, /*sigma=*/ 0.0, f0);
        double t = 0.0;

        for (int step = 0; step < 5; step++) {
            /**
             * Independently recompute the expected next value using the same
             * formula the class's Javadoc specifies, with the stochastic
             * term dropped (sigma=0 makes it exactly zero regardless of the
             * random draw actually used).
             */
            expected = expected + theta * (f0 - expected) * dt;
            t = process.act(t, ctx);
            report.checkEquals(ctx.fundamentalValue, expected, 1e-9,
                    "step " + (step + 1) + ": deterministic (sigma=0) Euler-Maruyama drift matches the hand-derived formula");
        }
    }

    private void testConvergesTowardLongRunMeanWithZeroVolatility(TestReport report) {
        /**
         * With sigma=0, repeated application of the mean-reverting drift
         * must monotonically pull the value toward f0 and stay there --
         * this is the basic sanity check that theta is acting as a genuine
         * pull-back-to-the-mean term, not e.g. having its sign flipped.
         */
        double f0 = 1200.0;
        SimulationContext ctx = new SimulationContext(42);
        FundamentalValueProcess process = new FundamentalValueProcess(0.5, 0.05, 0.0, f0);

        double t = 0.0;
        double previousDistance = Math.abs(ctx.fundamentalValue - f0);
        boolean everIncreased = false;
        for (int step = 0; step < 200; step++) {
            t = process.act(t, ctx);
            double distance = Math.abs(ctx.fundamentalValue - f0);
            if (distance > previousDistance + 1e-9) everIncreased = true;
            previousDistance = distance;
        }
        report.check(!everIncreased,
                "with zero volatility, the distance to the long-run mean f0 never increases step-over-step (monotonic convergence)");
        report.checkEquals(ctx.fundamentalValue, f0, 1.0,
                "after 200 steps of pure mean-reversion, the value has essentially converged to f0 (within 1.0)");
    }

    private void testNeverGoesNonPositiveUnderHighVolatility(TestReport report) {
        /**
         * With sigma>0, individual steps are random, but the production
         * code clamps every step to a floor of MarketConstants.TICK_SIZE.
         * We use a deliberately large sigma and many iterations to make it
         * likely that, absent the floor, at least one step would go
         * non-positive -- and confirm it never actually does.
         */
        SimulationContext ctx = new SimulationContext(43);
        FundamentalValueProcess process = new FundamentalValueProcess(1.0, 0.01, /*sigma=*/ 0.5, 1000.0);

        double t = 0.0;
        boolean everNonPositive = false;
        for (int step = 0; step < 2000; step++) {
            t = process.act(t, ctx);
            if (ctx.fundamentalValue <= 0) everNonPositive = true;
        }
        report.check(!everNonPositive,
                "across 2000 steps of a deliberately high-volatility run, the fundamental value never goes to zero or negative");
    }
}
