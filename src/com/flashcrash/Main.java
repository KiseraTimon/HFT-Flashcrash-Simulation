package com.flashcrash;

import com.flashcrash.analytics.HotPotatoNetworkAnalyzer;
import com.flashcrash.analytics.VPINCalculator;
import com.flashcrash.benchmark.PaperBenchmark;
import com.flashcrash.ml.FeatureExtractor;
import com.flashcrash.ml.LogisticRegression;
import com.flashcrash.util.CsvWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        System.out.println("=========================================================================");
        System.out.println(" FLASH CRASH REPLICATION & MITIGATION STUDY");
        System.out.println(" Benchmark: Kirilenko, Kyle, Samadi & Tuzun (2017), Journal of Finance 72(3)");
        System.out.println(" + SEC-CFTC (2010) Findings Regarding the Market Events of May 6, 2010");
        System.out.println(" + Easley, Lopez de Prado & O'Hara (2012), Review of Financial Studies 25(5)");
        System.out.println("=========================================================================\n");

        /** 1. Flagship single run (no risk controls); with detailed diagnostics */
        System.out.println(">>> Flagship run (seed=42, no risk controls) <<<\n");
        Scenario.Output flagship = Scenario.run(42, false, false);
        RunResult fr = flagship.result;
        System.out.println(fr);
        exportFlagshipCsvs(flagship);
        printBenchmarkComparison(fr);

        /** 2. Hot-potato network analysis around the crash window */
        System.out.println("\n>>> Hot-Potato Network Analysis (ALGORITHM 6) <<<\n");
        analyzeHotPotato(flagship);

        /** 3. ML early-warning classifier (ALGORITHM 8) */
        System.out.println("\n>>> Early-Warning Classifier (ALGORITHM 8: logistic regression) <<<\n");
        trainClassifier(flagship);
    }

    // Flagship CSVs Export Helper
    private static void exportFlagshipCsvs(Scenario.Output out) throws IOException {
        var ctx = out.ctx;
        List<double[]> priceRows = new ArrayList<>();
        for (int i = 0; i < ctx.sampleTimes.size(); i++) {
            priceRows.add(new double[]{
                    ctx.sampleTimes.get(i), ctx.midPriceSeries.get(i),
                    ctx.bestBidSeries.get(i), ctx.bestAskSeries.get(i),
                    ctx.hftAggregateInventorySeries.get(i), ctx.imbalanceSeries.get(i)
            });
        }
        CsvWriter.writeSeries("data/flagship_timeseries.csv",
                new String[]{"time_sec", "mid_price", "best_bid", "best_ask", "hft_agg_inventory", "imbalance"},
                priceRows);

        VPINCalculator vpinCalc = new VPINCalculator(150, 15);
        List<VPINCalculator.VpinPoint> vpin = vpinCalc.compute(ctx.tradeLog);
        List<double[]> vpinRows = new ArrayList<>();
        for (var p : vpin) vpinRows.add(new double[]{p.time, p.vpin});
        CsvWriter.writeSeries("data/flagship_vpin.csv", new String[]{"time_sec", "vpin"}, vpinRows);

        System.out.println("[written data/flagship_timeseries.csv, data/flagship_vpin.csv]");
    }

    // Benchmark Comparison Helper
    private static void printBenchmarkComparison(RunResult r) {
        System.out.println("\n--- Benchmark comparison: simulation vs. published figures ---");
        System.out.printf("%-45s %15s %15s%n", "Metric", "Simulated", "Published");
        System.out.printf("%-45s %14.2f%% %14.2f%%%n", "Max intraday drawdown",
                r.maxDrawdownPct, PaperBenchmark.CRASH_PRICE_DROP_PCT_MIN);
        System.out.printf("%-45s %13.2fmin %13.1fmin%n", "HFT inventory mean-reversion half-life",
                r.hftInventoryHalfLifeMinutes, PaperBenchmark.HFT_INVENTORY_HALFLIFE_MINUTES);
        System.out.printf("%-45s %15d %15d%n", "Max aggregate HFT inventory (scaled units)",
                r.hftMaxAbsAggregateInventory, (int) Math.round(PaperBenchmark.HFT_TYPICAL_MAX_INVENTORY_CONTRACTS * Scenario.SCALE));
        System.out.printf("%-45s %14.1f%% %14.1f%%%n", "Sell program max participation rate",
                r.sellProgramMaxParticipationPct, PaperBenchmark.SELL_PROGRAM_PARTICIPATION_MAX_PCT);
        System.out.printf("%-45s %15d %15d%n", "Sell program size (scaled contracts)",
                r.sellProgramExecutedQty, (int) Math.round(PaperBenchmark.SELL_PROGRAM_CONTRACTS * Scenario.SCALE));
        System.out.println("(Note: contract counts are scaled by a documented factor of " + Scenario.SCALE
                + " relative to the literal SEC-CFTC figures; percentages and time-based");
        System.out.println(" quantities are unscaled and directly comparable.)");
    }

    private static void analyzeHotPotato(Scenario.Output out) {
        var ctx = out.ctx;
        HotPotatoNetworkAnalyzer analyzer = new HotPotatoNetworkAnalyzer();
        double windowStart = Scenario.SELL_PROGRAM_START_SEC;
        double windowEnd = Scenario.SELL_PROGRAM_START_SEC + 600; // first 10 min of the sell program

        var turnover = analyzer.turnoverRatios(ctx.tradeLog, windowStart, windowEnd);
        System.out.println("Top 8 traders by turnover ratio (grossVolume / (1+|netPositionChange|)) during the stress window:");
        System.out.printf("%-12s %12s %12s %10s%n", "Trader", "GrossVol", "NetChange", "Turnover");
        for (int i = 0; i < Math.min(8, turnover.size()); i++) {
            var t = turnover.get(i);
            System.out.printf("%-12s %12d %12d %10.2f%n", t.traderId, t.grossVolume, t.netPositionChange, t.turnoverRatio);
        }

        var sccs = analyzer.stronglyConnectedComponents(ctx.tradeLog, windowStart, windowEnd);
        int nontrivial = 0;
        for (var scc : sccs) if (scc.size() > 1) nontrivial++;
        System.out.printf("%nTarjan SCC: %d non-trivial strongly-connected components found among %d traders " +
                        "(cycles of contracts changing hands within a closed group -- the hot-potato signature).%n",
                nontrivial, sccs.size());
    }

    // ML Classifier & Model
    private static void trainClassifier(Scenario.Output flagshipOut) {
        /** Logic:
         * Pool data across several independent runs so the classifier sees many
         * distinct crash episodes rather than relying on the single flagship run,
         * whose one crash near the end of the horizon starved a naive chronological
         * train/test split of positive test examples. Each run is still split
         * train(first 70%)/test(last 30%) *within itself* to avoid look-ahead leakage,
         * then the per-run splits are concatenated across runs.
         */
        FeatureExtractor extractor = new FeatureExtractor(2.0, 30.0, 20); // predict a 2% drawdown within 30s
        List<double[]> trainX = new ArrayList<>(), testX = new ArrayList<>();
        List<Integer> trainY = new ArrayList<>(), testY = new ArrayList<>();

        int nRunsForTraining = 15;
        for (int i = 0; i < nRunsForTraining; i++) {
            Scenario.Output out = (i == 0) ? flagshipOut : Scenario.run(5000 + i, false, false);
            FeatureExtractor.Dataset ds = extractor.build(out.ctx);
            if (ds.features.size() < 20) continue;
            int split = (int) (ds.features.size() * 0.7);
            trainX.addAll(ds.features.subList(0, split));
            trainY.addAll(ds.labels.subList(0, split));
            testX.addAll(ds.features.subList(split, ds.features.size()));
            testY.addAll(ds.labels.subList(split, ds.labels.size()));
        }

        if (trainX.size() < 50) {
            System.out.println("Not enough pooled samples to train a classifier.");
            return;
        }

        long positives = trainY.stream().filter(y -> y == 1).count();
        long testPositives = testY.stream().filter(y -> y == 1).count();
        System.out.printf("Pooled across %d runs -> training samples: %d (%.1f%% positive) | " +
                        "Test samples: %d (%.1f%% positive)%n",
                nRunsForTraining, trainX.size(), 100.0 * positives / trainX.size(),
                testX.size(), 100.0 * testPositives / testX.size());

        LogisticRegression model = new LogisticRegression(0.01);
        model.fit(trainX, trainY, 2000, 0.1);

        LogisticRegression.Metrics trainMetrics = model.evaluate(trainX, trainY, 0.5);
        LogisticRegression.Metrics testMetrics = model.evaluate(testX, testY, 0.5);
        System.out.println("Train metrics (threshold=0.5): " + trainMetrics);
        System.out.println("Test  metrics (threshold=0.5): " + testMetrics);

        double bestT = model.bestF1Threshold(trainX, trainY);
        LogisticRegression.Metrics testAtBest = model.evaluate(testX, testY, bestT);
        System.out.printf("Test  metrics (F1-optimal threshold=%.2f, chosen on train set): %s%n", bestT, testAtBest);
        System.out.println("(Crash events are rare -- under 1% of samples -- so accuracy at the default 0.5");
        System.out.println(" cutoff is misleadingly high; AUC and the F1-optimal threshold are the informative numbers.)");

        double[] w = model.getWeights();
        String[] names = {"VPIN", "orderBookImbalance", "trailingVolatility", "hftAggInventory", "priceChangeRate"};
        System.out.println("Standardized feature weights (larger |w| = more predictive, post-standardization):");
        for (int i = 0; i < w.length; i++) {
            System.out.printf("  %-22s %+.4f%n", names[i], w[i]);
        }
    }
}
