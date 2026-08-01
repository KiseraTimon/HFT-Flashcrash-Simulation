package com.flashcrash.tests.analytics;

import com.flashcrash.analytics.InventoryHalfLifeEstimator;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for InventoryHalfLifeEstimator: fits an AR(1) model via OLS to an
 * inventory time series and converts the fitted coefficient into a
 * half-life. We test this with a NOISELESS, perfectly deterministic AR(1)
 * series (x_t = phi * x_{t-1}, no random term at all) with a KNOWN phi --
 * since the data then lies exactly on a straight line through the origin,
 * OLS should recover that exact phi (and an intercept of ~0), which lets us
 * check the half-life formula's arithmetic precisely rather than just its
 * general shape.
 */
public class InventoryHalfLifeEstimatorTest implements TestSuite {

    @Override public String name() { return "InventoryHalfLifeEstimator (AR(1)/OLS)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());
    }

    private List<Double> generateDeterministicAR1(double x0, double phi, int n) {
        List<Double> series = new ArrayList<>();
        double x = x0;
        series.add(x);
        for (int i = 1; i < n; i++) {
            x = phi * x;
            series.add(x);
        }
        return series;
    }

}
