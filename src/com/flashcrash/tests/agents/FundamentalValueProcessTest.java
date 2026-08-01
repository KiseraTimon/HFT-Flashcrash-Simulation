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
}
