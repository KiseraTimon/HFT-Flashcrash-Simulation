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
}
