package com.flashcrash;

import com.flashcrash.analytics.VPINCalculator;
import com.flashcrash.benchmark.PaperBenchmark;
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

        /** Flagship single run (no risk controls); with detailed diagnostics */
        System.out.println(">>> Flagship run (seed=42, no risk controls) <<<\n");
        Scenario.Output flagship = Scenario.run(42, false, false);
        RunResult fr = flagship.result;
        System.out.println(fr);
        exportFlagshipCsvs(flagship);
        printBenchmarkComparison(fr);
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
}
