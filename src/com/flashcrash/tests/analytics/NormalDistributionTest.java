package com.flashcrash.tests.analytics;

import com.flashcrash.analytics.NormalDistribution;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Tests for NormalDistribution.cdf(): the Abramowitz & Stegun numerical
 * approximation of the standard normal cumulative distribution function
 * used by VPIN's Bulk Volume Classification step. We check it against
 * well-known textbook values (the standard normal table every statistics
 * student has memorized fragments of) rather than re-deriving the formula,
 * since the whole point is to confirm the *implementation* matches the
 * *known correct answer*.
 */
public class NormalDistributionTest implements TestSuite {

    @Override public String name() { return "NormalDistribution (CDF approximation)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());

        /**
         * The documented max error of this approximation is about 1.5e-7,
         * so we use a slightly looser tolerance (1e-6) to be safe.
         */
        double tol = 1e-6;

        report.checkEquals(NormalDistribution.cdf(0.0), 0.5, tol,
                "CDF at z=0 is exactly 0.5 (the median of the standard normal distribution)");

        // Standard textbook values for the standard normal CDF.
        report.checkEquals(NormalDistribution.cdf(1.0), 0.8413, 1e-4,
                "CDF at z=1.0 matches the standard normal table value (~0.8413)");
        report.checkEquals(NormalDistribution.cdf(1.96), 0.9750, 1e-4,
                "CDF at z=1.96 matches the standard normal table value (~0.9750, the familiar 95% two-sided cutoff)");
        report.checkEquals(NormalDistribution.cdf(-1.0), 0.1587, 1e-4,
                "CDF at z=-1.0 matches the standard normal table value (~0.1587)");

        /**
         * Symmetry: Phi(x) + Phi(-x) = 1 for any x, since the standard
         * normal distribution is symmetric around zero.
         */
        double[] testPoints = {0.5, 1.5, 2.3, 3.0};
        for (double x : testPoints) {
            double sum = NormalDistribution.cdf(x) + NormalDistribution.cdf(-x);
            report.checkEquals(sum, 1.0, tol, "Phi(" + x + ") + Phi(-" + x + ") = 1 (symmetry around zero)");
        }

        // Tail behaviour: far out in either tail, the CDF should approach 0 or 1.
        report.check(NormalDistribution.cdf(6.0) > 0.999999,
                "far in the right tail (z=6), CDF is essentially 1");
        report.check(NormalDistribution.cdf(-6.0) < 0.000001,
                "far in the left tail (z=-6), CDF is essentially 0");

        // Monotonicity: the CDF must never decrease as z increases.
        double prev = NormalDistribution.cdf(-5.0);
        boolean monotonic = true;
        for (double z = -4.5; z <= 5.0; z += 0.5) {
            double cur = NormalDistribution.cdf(z);
            if (cur < prev) monotonic = false;
            prev = cur;
        }
        report.check(monotonic, "CDF is monotonically non-decreasing across a scan from z=-5 to z=5");
    }
}
