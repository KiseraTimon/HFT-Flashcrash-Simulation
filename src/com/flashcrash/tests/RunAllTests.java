package com.flashcrash.tests;

import com.flashcrash.tests.agents.*;
import com.flashcrash.tests.analytics.*;
import com.flashcrash.tests.benchmark.*;
import com.flashcrash.tests.core.*;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;
import com.flashcrash.tests.ml.*;
import com.flashcrash.tests.orchestration.*;
import com.flashcrash.tests.risk.*;
import com.flashcrash.tests.sim.*;
import com.flashcrash.tests.util.*;

/**
 * Entry point that runs every test suite in the project and prints
 * a consolidated pass/fail report.
 *
 * Usage (from the project root, after the production sources have already
 * been compiled into `out`):
 * <pre>
 *   javac -d out $(find src -name "*.java")
 *   java -cp out com.flashcrash.tests.RunAllTests
 * </pre>
 *
 * Exits with status code 0 if every check passed, or 1 if any check
 * failed; suitable for wiring into a CI build-gate step.
 *
 * Suites are grouped by package below purely for readability; the order
 * they run in doesn't matter (each suite builds its own fresh state and
 * suites never share mutable objects with each other).
 */
public class RunAllTests {

    public static void main(String[] args) {
        TestReport report = new TestReport();

        TestSuite[] suites = {
                // core
                new CoreTypesTest(),
                new MarketConstantsTest(),
                new OrderBookTest(),

                // sim
                new SimulationContextTest(),
                new SimulationEngineTest(),

                // agents
                new NoiseTraderTest(),
                new MomentumTraderTest(),
                new HFTMarketMakerTest(),
                new LargeSellerTest(),
                new FundamentalValueProcessTest(),
                new ValueTraderTest(),

                // analytics
                new NormalDistributionTest(),
                new VPINCalculatorTest(),
                new InventoryHalfLifeEstimatorTest(),
                new HotPotatoNetworkAnalyzerTest(),

                // risk
                new LuldCircuitBreakerTest(),
                new VpinPreemptiveHaltTest(),

                // ml
                new LogisticRegressionTest(),
                new FeatureExtractorTest(),

                // benchmark
                new PaperBenchmarkTest(),
                new WelchTTestTest(),

                // util
                new CsvWriterTest(),

                // orchestration (end-to-end smoke/regression test)
                new ScenarioSmokeTest(),
        };

        System.out.println("*****");
        System.out.println(" RUNNING FULL TEST SUITE: " + suites.length + " suites");
        System.out.println("*****");

        for (TestSuite suite : suites) {
            int before = report.totalCount();
            try {
                suite.run(report);
            } catch (Throwable t) {
                /**
                 * A suite throwing (as opposed to reporting a failed check)
                 * is itself a bug -- record it and keep going, so one broken
                 * suite never prevents the rest of the project's tests from
                 * running and reporting their own results.
                 */
                report.recordException(suite.name(), t);
            }
            int checksRun = report.totalCount() - before;
            System.out.printf("  %-70s %d checks%n", suite.name(), checksRun);
        }

        report.printSummary();

        System.exit(report.failCount() == 0 ? 0 : 1);
    }
}
