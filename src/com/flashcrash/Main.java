package com.flashcrash;

import com.flashcrash.analytics.VPINCalculator;
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
    }

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
}
