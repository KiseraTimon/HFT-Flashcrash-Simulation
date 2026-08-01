package com.flashcrash.tests.framework;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, dependency-free stand-in for a JUnit assertion + reporting API.
 * A single TestReport instance is shared across every suite for the whole
 * run, so the final summary covers the entire project.
 *
 * Usage inside a suite:
 * <pre>
 *   report.enterSuite("OrderBook");
 *   report.check(book.bestBid() == null, "empty book has no best bid");
 *   report.checkEquals(book.midPrice(), 1165.0, 1e-9, "mid-price of empty book falls back to last trade price");
 * </pre>
 */
public class TestReport {

    private int passCount = 0;
    private int failCount = 0;
    private final List<String> failures = new ArrayList<>();
    private String currentSuite = "(unnamed)";

    /** Called once per suite before its checks run, purely for readable failure messages. */
    public void enterSuite(String suiteName) {
        this.currentSuite = suiteName;
    }

    /** The most general check: pass a boolean you've already computed, plus a description of what it means. */
    public void check(boolean condition, String description) {
        if (condition) {
            passCount++;
        } else {
            failCount++;
            failures.add("[" + currentSuite + "] " + description);
        }
    }

    /** Numeric equality within an explicit tolerance -- required for anything involving doubles. */
    public void checkEquals(double actual, double expected, double tolerance, String description) {
        boolean ok = Math.abs(actual - expected) <= tolerance;
        String detail = String.format("%s (expected=%.6f, actual=%.6f, tolerance=%.6f)",
                description, expected, actual, tolerance);
        check(ok, detail);
    }

    /** Exact integer/long equality (no tolerance needed -- these are counts, ids, or discrete quantities). */
    public void checkEquals(long actual, long expected, String description) {
        String detail = String.format("%s (expected=%d, actual=%d)", description, expected, actual);
        check(actual == expected, detail);
    }

    /** String/object equality via .equals(), with null-safety. */
    public void checkEquals(Object actual, Object expected, String description) {
        boolean ok = (actual == null) ? (expected == null) : actual.equals(expected);
        check(ok, description + " (expected=" + expected + ", actual=" + actual + ")");
    }

    /** Records an unexpected exception as a single failed check, so one broken suite doesn't crash the whole run. */
    public void recordException(String suiteName, Throwable t) {
        failCount++;
        failures.add("[" + suiteName + "] UNEXPECTED EXCEPTION: " + t);
    }

    public int passCount() { return passCount; }
    public int failCount() { return failCount; }
    public int totalCount() { return passCount + failCount; }
    public List<String> failures() { return failures; }

    /** Prints the final pass/fail tally and, if any, the full list of failure descriptions. */
    public void printSummary() {
        System.out.println();
        System.out.println("*****");
        System.out.printf(" TEST SUMMARY: %d / %d checks passed%n", passCount, totalCount());
        System.out.println("*****");
        if (!failures.isEmpty()) {
            System.out.println("Failures:");
            for (String f : failures) {
                System.out.println("  - " + f);
            }
        } else {
            System.out.println("All checks passed.");
        }
    }
}
