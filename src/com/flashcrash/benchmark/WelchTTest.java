package com.flashcrash.benchmark;

import java.util.List;

/**
 * ALGORITHM 9: Welch's unequal-variances t-test, used to determine whether
 * a difference in outcomes (e.g. max drawdown) between two Monte Carlo
 * samples (with vs. without a given risk control) is statistically
 * distinguishable from noise, without assuming the two samples have equal
 * variance (a poor assumption here, since a working circuit breaker should
 * also reduce the *variance* of outcomes, not just the mean).
 */
public final class WelchTTest {
    private WelchTTest() {}

    public static class Result {
        public final double meanA, meanB, t, degreesOfFreedom;
        Result(double meanA, double meanB, double t, double df) {
            this.meanA = meanA; this.meanB = meanB; this.t = t; this.degreesOfFreedom = df;
        }
        @Override public String toString() {
            return String.format("meanA=%.4f meanB=%.4f t=%.3f df=%.1f", meanA, meanB, t, degreesOfFreedom);
        }
    }

    public static Result test(List<Double> a, List<Double> b) {
        double meanA = mean(a), meanB = mean(b);
        double varA = variance(a, meanA), varB = variance(b, meanB);
        int nA = a.size(), nB = b.size();

        double se = Math.sqrt(varA / nA + varB / nB);
        double t = se < 1e-12 ? 0.0 : (meanA - meanB) / se;

        double num = Math.pow(varA / nA + varB / nB, 2);
        double den = Math.pow(varA / nA, 2) / (nA - 1) + Math.pow(varB / nB, 2) / (nB - 1);
        double df = den < 1e-12 ? (nA + nB - 2) : num / den;

        return new Result(meanA, meanB, t, df);
    }

    private static double mean(List<Double> xs) {
        double s = 0;
        for (double x : xs) s += x;
        return s / xs.size();
    }

    private static double variance(List<Double> xs, double mean) {
        double s = 0;
        for (double x : xs) s += (x - mean) * (x - mean);
        return s / Math.max(1, xs.size() - 1);
    }
}
