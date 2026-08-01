package com.flashcrash.tests.ml;

import com.flashcrash.ml.FeatureExtractor;
import com.flashcrash.sim.SimulationContext;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Tests for FeatureExtractor: turns a completed simulation's recorded time
 * series into labelled (features, crash-within-horizon) training examples.
 *
 * Rather than running a full simulation (which would make it hard to know
 * in advance exactly which samples SHOULD be labelled as a coming crash), we
 * directly hand-construct a SimulationContext's recorded series with an
 * engineered, instantaneous price drop at a known index. This lets us
 * predict exactly which samples the extractor should label 1 (a crash is
 * coming within the horizon) vs. 0, and verify it does so precisely.
 */
public class FeatureExtractorTest implements TestSuite {

    @Override public String name() { return "FeatureExtractor (crash labeling)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());

        testLabelsSamplesPrecedingAnEngineeredCrash(report);
        testFeatureVectorFieldsAlignWithRecordedSeries(report);
        testTooShortContextProducesEmptyDataset(report);
    }

    /**
     * Builds a synthetic context: price is flat at 100.0 for samples 0-19,
     * then INSTANTLY drops to 80.0 (a 20% crash) at sample 20, and stays
     * flat at 80.0 for samples 20-39. With sampling interval 1.0s and a
     * horizonSeconds of 5 (=5 samples), a sample at index i should be
     * labelled a coming crash (label=1) exactly when the drop at index 20
     * falls within (i, i+5] -- i.e. for i in [15, 19].
     */
    private SimulationContext buildEngineeredCrashContext() {
        SimulationContext ctx = new SimulationContext(81);
        for (int i = 0; i < 40; i++) {
            ctx.sampleTimes.add((double) i);
            ctx.midPriceSeries.add(i < 20 ? 100.0 : 80.0);
            ctx.bestBidSeries.add(0.0);
            ctx.bestAskSeries.add(0.0);
            ctx.hftAggregateInventorySeries.add(i * 2); // arbitrary but deterministic, used for alignment check
            ctx.imbalanceSeries.add(i * 0.01);           // arbitrary but deterministic, used for alignment check
        }
        return ctx;
    }

    private void testLabelsSamplesPrecedingAnEngineeredCrash(TestReport report) {
        SimulationContext ctx = buildEngineeredCrashContext();
        FeatureExtractor extractor = new FeatureExtractor(/*crashThresholdPct=*/ 5.0, /*horizonSeconds=*/ 5.0, /*volWindow=*/ 5);
        FeatureExtractor.Dataset ds = extractor.build(ctx);

        report.check(!ds.features.isEmpty(), "the extractor produces a non-empty dataset from a well-formed context");

        int mismatches = 0;
        for (int pos = 0; pos < ds.times.size(); pos++) {
            double sampleTime = ds.times.get(pos);
            int i = (int) Math.round(sampleTime); // sampleTime == original index, since sampling interval is 1.0
            boolean expectedCrashAhead = (i >= 15 && i <= 19);
            boolean actualLabel = ds.labels.get(pos) == 1;
            if (expectedCrashAhead != actualLabel) mismatches++;
        }
        report.checkEquals(mismatches, 0L,
                "every sample is labeled exactly as expected: 1 for the 5 samples immediately preceding the "
                        + "engineered crash (indices 15-19), 0 everywhere else");
    }

    private void testFeatureVectorFieldsAlignWithRecordedSeries(TestReport report) {
        SimulationContext ctx = buildEngineeredCrashContext();
        FeatureExtractor extractor = new FeatureExtractor(5.0, 5.0, 5);
        FeatureExtractor.Dataset ds = extractor.build(ctx);

        /**
         * Per the class's documented feature order: [VPIN, imbalance, volatility, hftInventory, priceChangeRate].
         * Find the dataset row corresponding to original index i=25 and check
         * the imbalance/hftInventory fields were copied from the right place,
         * not off-by-one or transposed with another field.
         */
        int targetIndex = 25;
        int pos = -1;
        for (int p = 0; p < ds.times.size(); p++) {
            if (Math.round(ds.times.get(p)) == targetIndex) { pos = p; break; }
        }
        report.check(pos >= 0, "the dataset includes a row for original sample index 25");
        if (pos >= 0) {
            double[] feat = ds.features.get(pos);
            report.checkEquals(feat[1], 25 * 0.01, 1e-9,
                    "feature index 1 (order-book imbalance) matches ctx.imbalanceSeries at the corresponding original index");
            report.checkEquals(feat[3], 25 * 2.0, 1e-9,
                    "feature index 3 (aggregate HFT inventory) matches ctx.hftAggregateInventorySeries at the corresponding original index");
            report.checkEquals(feat[0], 0.0, 1e-9,
                    "feature index 0 (VPIN) defaults to 0.0 when the trade log is empty (no VPIN reading available yet)");
        }
    }

    private void testTooShortContextProducesEmptyDataset(TestReport report) {
        SimulationContext ctx = new SimulationContext(82);

        // Only 3 samples recorded -- far short of the volWindow=5 the extractor needs.
        for (int i = 0; i < 3; i++) {
            ctx.sampleTimes.add((double) i);
            ctx.midPriceSeries.add(100.0);
            ctx.bestBidSeries.add(0.0);
            ctx.bestAskSeries.add(0.0);
            ctx.hftAggregateInventorySeries.add(0);
            ctx.imbalanceSeries.add(0.0);
        }
        FeatureExtractor extractor = new FeatureExtractor(5.0, 5.0, 5);
        FeatureExtractor.Dataset ds = extractor.build(ctx);
        report.check(ds.features.isEmpty(),
                "a context with too few recorded samples produces an empty dataset rather than throwing or misbehaving");
    }
}
