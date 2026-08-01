package com.flashcrash.tests.framework;

/**
 * Contract every test suite in this project implements. There is no JUnit
 * (or any other external dependency) here on purpose; the production
 * code has a stated design goal of "pure Java standard library only," and
 * a hand-rolled, five-minute-to-understand test harness keeps that promise
 * intact for the test code too, instead of quietly reintroducing a
 * dependency through the back door.
 *
 * A "test suite" is simply: a name (for reporting) and a method that runs
 * a series of checks against a {@link TestReport}. See RunAllTests for how
 * suites are collected and executed.
 */
public interface TestSuite {

    /** Human-readable name shown in the console report, e.g. "OrderBook". */
    String name();

    /**
     * Runs every check owned by this suite, recording each pass/fail into
     * {@code report}. Implementations should never throw on an expected
     * failure -- report it via {@code report.check(...)} instead -- but if
     * a genuinely unexpected exception escapes (a real bug, not a failed
     * assertion), RunAllTests catches it per-suite so one broken suite
     * doesn't stop the rest of the report from running.
     */
    void run(TestReport report);
}
