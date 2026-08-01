package com.flashcrash.tests.orchestration;

import com.flashcrash.RunResult;
import com.flashcrash.Scenario;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * An end-to-end smoke/regression test for Scenario; the class that wires
 * every other module together into one runnable simulated trading session.
 * This is deliberately NOT a unit test: it runs the real, full simulation
 * (same code path as `java -cp out com.flashcrash.Main`) and checks that
 * the output is internally sane and, for the flagship seed, lands in the
 * plausible range documented in README.md.
 *
 * This is exactly the "regression threshold" test presenting itself
 * as the single highest-leverage automated check for this
 * project, given its actual failure history: several of the bugs found
 * during development (near-zero price impact from an infinitely liquid
 * book; a price spiral through zero; an unbounded crash with no recovery)
 * would all have been caught immediately by an assertion like this one,
 * instead of requiring eyeballing of the console output.
 */
public class ScenarioSmokeTest implements TestSuite {

    @Override public String name() { return "Scenario (end-to-end integration smoke test)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());

        testFlagshipSeedProducesSaneAndPlausibleResults(report);
        testRunsCleanlyWithAllRiskControlCombinations(report);
    }

    private void testFlagshipSeedProducesSaneAndPlausibleResults(TestReport report) {
        /**
        // Same seed (42) and configuration (no risk controls) as the
        // documented "flagship run" in README.md, so this test doubles as
        // a check that nobody has silently changed the calibration.
         */
        Scenario.Output out = Scenario.run(42, false, false);
        RunResult r = out.result;

        // Basic sanity: the simulation actually did something
        report.check(r.totalTrades > 0, "the flagship run produces a nonzero number of trades");
        report.check(r.openPrice > 0, "opening price is a sane positive number");
        report.check(r.finalPrice > 0, "final price is a sane positive number (in particular, NOT the zero/negative "
                + "price-spiral failure mode this project hit during development)");
        report.check(r.sellProgramExecutedQty > 0, "the large seller actually executes at least part of its program");

        // Plausibility range, cross-checked against README.md's documented flagship result (~8.2% drawdown)
        report.check(r.maxDrawdownPct > 0 && r.maxDrawdownPct < 50,
                "max drawdown lands in a plausible range (>0%, <50%) -- NOT the near-100% or 1000%+ figures seen "
                        + "during the project's price-spiral debugging (actual: " + r.maxDrawdownPct + "%)");
        report.check(r.maxDrawdownPct > 3.0,
                "max drawdown is large enough to be a genuine stress event, not just background noise "
                        + "(actual: " + r.maxDrawdownPct + "%, README documents ~8.2% for this exact seed)");

        // Internal consistency between related fields
        report.check(r.sellProgramExecutedQty <= 3000 /* Scenario.SCALE-adjusted total program size */ + 1,
                "the seller never executes more than its configured total order size");
        report.check(r.sellProgramMaxParticipationPct >= 0 && r.sellProgramMaxParticipationPct <= 100,
                "participation rate is a valid percentage");
        report.check(r.hftMaxAbsAggregateInventory >= 0,
                "aggregate HFT inventory is never negative (it's defined as a sum of absolute values)");
    }

    private void testRunsCleanlyWithAllRiskControlCombinations(TestReport report) {
        /**
         * Each risk-control combination is a genuinely different code path
         * through Scenario (different RiskControl instances registered with
         * the engine) -- confirm none of the four combinations used in
         * Main's Monte Carlo comparison throws or produces a nonsensical result.
         */
        boolean[][] combos = {{false, false}, {true, false}, {false, true}, {true, true}};
        String[] labels = {"baseline", "LULD only", "VPIN only", "LULD+VPIN"};

        for (int i = 0; i < combos.length; i++) {
            try {
                Scenario.Output out = Scenario.run(999, combos[i][0], combos[i][1]);
                boolean sane = out.result.finalPrice > 0 && out.result.totalTrades > 0;
                report.check(sane, "configuration '" + labels[i] + "' runs to completion and produces a sane result");
            } catch (Exception e) {
                report.check(false, "configuration '" + labels[i] + "' threw an unexpected exception: " + e);
            }
        }
    }
}
