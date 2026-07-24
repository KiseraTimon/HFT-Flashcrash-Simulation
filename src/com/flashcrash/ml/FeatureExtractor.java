package com.flashcrash.ml;

import com.flashcrash.analytics.VPINCalculator;
import com.flashcrash.sim.SimulationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a labelled dataset from a completed simulation run for the
 * early-warning classifier: at each sampled instant t we compute a feature
 * vector from information available up to t, and label it 1 if a "crash"
 * (a drawdown exceeding {@code crashThresholdPct} from the price at t)
 * occurs anywhere in the next {@code horizonSeconds}, else 0. This is a
 * standard event-prediction framing (used e.g. in early-warning systems
 * for epidemics, power grids, and markets alike): predict the event before
 * it is visible in price alone.
 *
 * Features: [VPIN, order-book imbalance, trailing realized volatility,
 *            aggregate |HFT inventory|, short-term price-change rate]
 */
public class FeatureExtractor {

    public static class Dataset {
        public final List<double[]> features = new ArrayList<>();
        public final List<Integer> labels = new ArrayList<>();
        public final List<Double> times = new ArrayList<>();
    }

    private final double crashThresholdPct;
    private final double horizonSeconds;
    private final int volWindow;

    public FeatureExtractor(double crashThresholdPct, double horizonSeconds, int volWindow) {
        this.crashThresholdPct = crashThresholdPct;
        this.horizonSeconds = horizonSeconds;
        this.volWindow = volWindow;
    }

    public Dataset build(SimulationContext ctx) {
        Dataset ds = new Dataset();
        int n = ctx.sampleTimes.size();
        if (n < volWindow + 5) return ds;

        double samplingInterval = ctx.sampleTimes.get(1) - ctx.sampleTimes.get(0);
        int horizonSamples = Math.max(1, (int) Math.round(horizonSeconds / samplingInterval));

        VPINCalculator vpinCalc = new VPINCalculator(500, 20);
        List<VPINCalculator.VpinPoint> vpinPoints = vpinCalc.compute(ctx.tradeLog);

        for (int i = volWindow; i < n - horizonSamples; i++) {
            double t = ctx.sampleTimes.get(i);
            double price = ctx.midPriceSeries.get(i);

            // trailing realized volatility: stdev of log returns over volWindow samples
            double sumSq = 0;
            int cnt = 0;
            for (int j = i - volWindow + 1; j <= i; j++) {
                double p0 = ctx.midPriceSeries.get(j - 1);
                double p1 = ctx.midPriceSeries.get(j);
                if (p0 > 0 && p1 > 0) {
                    double r = Math.log(p1 / p0);
                    sumSq += r * r;
                    cnt++;
                }
            }
            double vol = cnt > 0 ? Math.sqrt(sumSq / cnt) : 0.0;

            double imbalance = ctx.imbalanceSeries.get(i);
            double hftInv = ctx.hftAggregateInventorySeries.get(i);

            double priceChangeRate = (price - ctx.midPriceSeries.get(i - volWindow)) / ctx.midPriceSeries.get(i - volWindow);

            double vpin = nearestVpinBefore(vpinPoints, t);

            double[] feat = new double[]{vpin, imbalance, vol, hftInv, priceChangeRate};

            // label: does price fall more than crashThresholdPct from `price` within horizon?
            int label = 0;
            double minFuture = price;
            for (int k = i + 1; k <= i + horizonSamples && k < n; k++) {
                minFuture = Math.min(minFuture, ctx.midPriceSeries.get(k));
            }
            double drawdownPct = (price - minFuture) / price * 100.0;
            if (drawdownPct >= crashThresholdPct) label = 1;

            ds.features.add(feat);
            ds.labels.add(label);
            ds.times.add(t);
        }
        return ds;
    }

    private double nearestVpinBefore(List<VPINCalculator.VpinPoint> points, double t) {
        double last = 0.0;
        for (VPINCalculator.VpinPoint p : points) {
            if (p.time > t) break;
            last = p.vpin;
        }
        return last;
    }
}
