package com.flashcrash.tests.benchmark;

import com.flashcrash.benchmark.WelchTTest;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

import java.util.List;

/**
 * Tests for WelchTTest: the statistical test used to check whether the
 * Monte Carlo comparison between risk-control configurations (e.g.
 * "baseline" vs. "VPIN preemptive halt") reflects a real difference or
 * just noise. We verify it against a small sample pair whose t-statistic
 * and degrees-of-freedom can be computed by hand, plus a couple of edge
 * cases (identical samples, zero variance) that a naive implementation
 * could easily mishandle with a divide-by-zero.
 */
public class WelchTTestTest implements TestSuite {

    @Override public String name() { return "WelchTTest"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());
    }

    private void testMatchesHandComputedStatistic(TestReport report) {
        /**
         * Sample A: [2,4,6] -> mean=4, sample variance=4
         * Sample B: [1,2,3] -> mean=2, sample variance=1
         * Hand-derived:
         *          se = sqrt(4/3 + 1/3) = sqrt(5/3) ~= 1.290994
         *          t  = (4-2) / 1.290994 ~= 1.549193
         *          df (Welch-Satterthwaite) ~= 2.941176
         */
        List<Double> a = List.of(2.0, 4.0, 6.0);
        List<Double> b = List.of(1.0, 2.0, 3.0);

        WelchTTest.Result result = WelchTTest.test(a, b);

        report.checkEquals(result.meanA, 4.0, 1e-9, "mean of sample A matches (2+4+6)/3=4");
        report.checkEquals(result.meanB, 2.0, 1e-9, "mean of sample B matches (1+2+3)/3=2");
        report.checkEquals(result.t, 1.549193, 1e-4, "t-statistic matches the hand-derived value for these two samples");
        report.checkEquals(result.degreesOfFreedom, 2.941176, 1e-3,
                "Welch-Satterthwaite degrees of freedom matches the hand-derived value");
    }

    private void testIdenticalSamplesGiveZeroTStatistic(TestReport report) {
        /**
         * Two samples with zero variance and the same mean -- a naive
         * implementation might divide 0/0 here. The production code
         * explicitly guards against a near-zero standard error.
         */
        List<Double> a = List.of(5.0, 5.0, 5.0);
        List<Double> b = List.of(5.0, 5.0, 5.0);

        WelchTTest.Result result = WelchTTest.test(a, b);
        report.checkEquals(result.t, 0.0, 1e-9,
                "two identical, zero-variance samples produce a t-statistic of exactly 0.0 (not NaN or infinity)");
    }

    private void testSwappingSampleOrderFlipsTheSignOfT(TestReport report) {
        List<Double> a = List.of(10.0, 12.0, 14.0);
        List<Double> b = List.of(1.0, 2.0, 3.0);

        WelchTTest.Result forward = WelchTTest.test(a, b);
        WelchTTest.Result reversed = WelchTTest.test(b, a);

        report.checkEquals(reversed.t, -forward.t, 1e-9,
                "swapping the order of the two samples flips the sign of t (since it's just meanA-meanB over a "
                        + "positive standard error), but leaves its magnitude unchanged");
        report.checkEquals(reversed.degreesOfFreedom, forward.degreesOfFreedom, 1e-9,
                "degrees of freedom is symmetric in the two samples and does not depend on their order");
    }
}
