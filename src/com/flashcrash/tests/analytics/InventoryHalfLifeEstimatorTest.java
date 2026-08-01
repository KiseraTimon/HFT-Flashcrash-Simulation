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

        testRecoversKnownDecayRate(report);
        testExplosiveSeriesReturnsUndefinedHalfLife(report);
        testTooShortSeriesReturnsUndefined(report);
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

    private void testRecoversKnownDecayRate(TestReport report) {
        double phi = 0.9;
        List<Double> series = generateDeterministicAR1(100.0, phi, 50);

        InventoryHalfLifeEstimator estimator = new InventoryHalfLifeEstimator();
        double samplingIntervalSeconds = 2.0;
        InventoryHalfLifeEstimator.Result result = estimator.estimate(series, samplingIntervalSeconds);

        report.checkEquals(result.phi, phi, 1e-6,
                "OLS recovers the exact AR(1) coefficient from a noiseless synthetic series (phi=0.9)");
        report.checkEquals(result.intercept, 0.0, 1e-6,
                "intercept is essentially zero for a series that decays exactly toward zero with no offset");

        // Hand-derived expected half-life: H = ln(0.5) / ln(phi) samples.
        double expectedHalfLifeSamples = Math.log(0.5) / Math.log(phi); // ~6.5788 samples
        report.checkEquals(result.halfLifeSamples, expectedHalfLifeSamples, 1e-4,
                "half-life in samples matches the closed-form formula ln(0.5)/ln(phi)");
        report.checkEquals(result.halfLifeSeconds, expectedHalfLifeSamples * samplingIntervalSeconds, 1e-3,
                "half-life in seconds correctly multiplies the sample-count half-life by the sampling interval");
    }

    private void testExplosiveSeriesReturnsUndefinedHalfLife(TestReport report) {
        /**
         * phi > 1 means the series is growing, not mean-reverting at all --
         * "half-life" is not a meaningful concept here, and the estimator
         * should say so explicitly (NaN) rather than returning a nonsense number.
         */
        List<Double> series = generateDeterministicAR1(1.0, 1.5, 20);

        InventoryHalfLifeEstimator estimator = new InventoryHalfLifeEstimator();
        InventoryHalfLifeEstimator.Result result = estimator.estimate(series, 1.0);

        report.checkEquals(result.phi, 1.5, 1e-6, "OLS still correctly recovers phi=1.5 for an explosive series");
        report.check(Double.isNaN(result.halfLifeSamples),
                "half-life is reported as NaN (undefined) for a non-mean-reverting, explosive series (phi>=1)");
    }

    private void testTooShortSeriesReturnsUndefined(TestReport report) {
        /**
         * The regression needs at least a couple of (x_{t-1}, x_t) pairs to
         * fit anything meaningful; the production code explicitly guards
         * against series shorter than 3 points.
         */
        InventoryHalfLifeEstimator estimator = new InventoryHalfLifeEstimator();
        InventoryHalfLifeEstimator.Result result = estimator.estimate(List.of(1.0, 2.0), 1.0);

        report.check(Double.isNaN(result.phi), "a series with fewer than 3 points returns NaN rather than a spurious fit");
        report.check(Double.isNaN(result.halfLifeSeconds), "half-life in seconds is also NaN for a too-short series");
    }
}
