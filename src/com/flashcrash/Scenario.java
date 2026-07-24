package com.flashcrash;

import com.flashcrash.agents.*;
import com.flashcrash.analytics.InventoryHalfLifeEstimator;
import com.flashcrash.analytics.VPINCalculator;
import com.flashcrash.benchmark.PaperBenchmark;
import com.flashcrash.core.MarketConstants;
import com.flashcrash.core.OrderSide;
import com.flashcrash.core.OrderType;
import com.flashcrash.risk.LuldCircuitBreaker;
import com.flashcrash.risk.VpinPreemptiveHalt;
import com.flashcrash.sim.SimulationContext;
import com.flashcrash.sim.SimulationEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds and runs one simulated "trading session" calibrated to the May 6,
 * 2010 Flash Crash. Because our toy market has dozens of agents instead of
 * the 15,422 real trading accounts and a few hundred contracts/sec instead
 * of the E-mini's full depth, ALL contract quantities are scaled down by a
 * documented factor SCALE relative to the literal figures in the SEC-CFTC
 * report and what Kirilenko had. Percentages, participation ratios,
 * and time-based quantities (half-lives, durations) are NOT scaled and are
 * directly comparable to the published numbers -- this is the standard
 * calibration approach in agent-based computational-economics models: match
 * relative/statistical "stylized facts", not absolute headline counts.
 */
public class Scenario {

    public static final double SCALE = 1.0 / 25.0;

    public static final double SIM_DURATION_SEC = 1800;      // 30 simulated minutes
    public static final double SELL_PROGRAM_START_SEC = 300; // program begins 5 min in
    public static final double SNAPSHOT_INTERVAL_SEC = 1.0;

    public static class Output {
        public RunResult result;
        public SimulationContext ctx;
        public LargeSeller largeSeller;
        public LuldCircuitBreaker luld;
        public VpinPreemptiveHalt vpinHalt;
    }

    public static Output run(long seed, boolean useLuld, boolean useVpinHalt) {
        SimulationContext ctx = new SimulationContext(seed);
        Random rng = ctx.rng;

        List<Agent> agents = new ArrayList<>();

        // Fundamental value anchor (Euler-Maruyama OU process)
        agents.add(new FundamentalValueProcess(0.5, 0.02, 0.01, MarketConstants.OPENING_PRICE));

        // Noise traders: baseline Poisson order flow (thin, finite resting depth)
        int nNoise = 20;
        for (int i = 0; i < nNoise; i++) {
            agents.add(new NoiseTrader("NOISE-" + i, 0.3, 3, 6, 3));
        }

        // Momentum traders: feedback-loop / momentum-ignition risk
        int nMomentum = 4;
        for (int i = 0; i < nMomentum; i++) {
            agents.add(new MomentumTrader("MOM-" + i, 0.25, 5, 15, 0.08, 3));
        }

        // HFT market makers: Avellaneda-Stoikov inventory-skewed quoting
        int nHft = 10;
        int hftCapPerAgent = 12; // aggregate cap ~120 = 3000 * SCALE, matching the paper's ~3,000-contract figure
        for (int i = 0; i < nHft; i++) {
            String id = "HFT-" + i;
            ctx.hftTraderIds.add(id);
            agents.add(new HFTMarketMaker(id, 3.0, 0.05, 1.0, 1.0, 1.0, 1, hftCapPerAgent));
        }

        // Value traders: mean-reversion force anchoring price to fundamental value
        int nValue = 20;
        for (int i = 0; i < nValue; i++) {
            agents.add(new ValueTrader("VALUE-" + i, 1.0, 0.1, 20.0, 50));
        }

        // The large seller: percentage-of-volume execution algorithm
        int sellQty = (int) Math.round(PaperBenchmark.SELL_PROGRAM_CONTRACTS * SCALE); // 3,000
        LargeSeller largeSeller = new LargeSeller("BIGSELLER", sellQty,
                PaperBenchmark.SELL_PROGRAM_PARTICIPATION_MAX_PCT / 100.0,
                SELL_PROGRAM_START_SEC, 100, 2.0);
        agents.add(largeSeller);

        // Risk controls
        SimulationEngine engine = new SimulationEngine();
        LuldCircuitBreaker luld = null;
        VpinPreemptiveHalt vpinHalt = null;
        if (useLuld) {
            luld = new LuldCircuitBreaker(3.0, 30.0, 300.0); // 3% band over 5-min reference, 30s halt
            engine.addRiskControl(luld);
        }
        if (useVpinHalt) {
            vpinHalt = new VpinPreemptiveHalt(150, 15, 0.45, 20.0, 5.0);
            engine.addRiskControl(vpinHalt);
        }

        // seeding the book with initial resting liquidity so early market orders have something to hit
        seedInitialBook(ctx, rng);

        engine.run(agents, SIM_DURATION_SEC, SNAPSHOT_INTERVAL_SEC, ctx);

        RunResult result = analyze(ctx, seed, largeSeller, luld, vpinHalt, sellQty);

        Output out = new Output();
        out.result = result;
        out.ctx = ctx;
        out.largeSeller = largeSeller;
        out.luld = luld;
        out.vpinHalt = vpinHalt;
        return out;
    }

    private static void seedInitialBook(SimulationContext ctx, Random rng) {
        long midTicks = MarketConstants.priceToTicks(MarketConstants.OPENING_PRICE);
        for (int i = 1; i <= 20; i++) {
            ctx.book.submit("SEED", OrderSide.BUY, OrderType.LIMIT, midTicks - i, 5 + rng.nextInt(5), 0.0);
            ctx.book.submit("SEED", OrderSide.SELL, OrderType.LIMIT, midTicks + i, 5 + rng.nextInt(5), 0.0);
        }
    }

    private static RunResult analyze(SimulationContext ctx, long seed, LargeSeller seller,
                                      LuldCircuitBreaker luld, VpinPreemptiveHalt vpinHalt, int sellQty) {
        RunResult r = new RunResult();
        r.seed = seed;
        r.openPrice = ctx.midPriceSeries.get(0);
        r.finalPrice = ctx.midPriceSeries.get(ctx.midPriceSeries.size() - 1);

        double runningPeak = r.openPrice;
        double maxDrawdown = 0;
        double bottomPrice = r.openPrice;
        double timeOfBottom = 0;
        for (int i = 0; i < ctx.sampleTimes.size(); i++) {
            double p = ctx.midPriceSeries.get(i);
            runningPeak = Math.max(runningPeak, p);
            double dd = (runningPeak - p) / runningPeak * 100.0;
            if (dd > maxDrawdown) {
                maxDrawdown = dd;
                bottomPrice = p;
                timeOfBottom = ctx.sampleTimes.get(i);
            }
        }
        r.maxDrawdownPct = maxDrawdown;
        r.bottomPrice = bottomPrice;
        r.timeOfBottomSec = timeOfBottom;
        r.crashOccurred = maxDrawdown >= PaperBenchmark.CRASH_PRICE_DROP_PCT_MIN;

        // recovery: first time after the bottom that price returns within 1% of open
        r.recoveryTimeSec = -1;
        for (int i = 0; i < ctx.sampleTimes.size(); i++) {
            if (ctx.sampleTimes.get(i) < timeOfBottom) continue;
            double p = ctx.midPriceSeries.get(i);
            if (Math.abs(p - r.openPrice) / r.openPrice <= 0.01) {
                r.recoveryTimeSec = ctx.sampleTimes.get(i) - timeOfBottom;
                break;
            }
        }

        VPINCalculator vpinCalc = new VPINCalculator(150, 15);
        List<VPINCalculator.VpinPoint> vpinSeries = vpinCalc.compute(ctx.tradeLog);
        double peakVpin = 0;
        for (VPINCalculator.VpinPoint p : vpinSeries) peakVpin = Math.max(peakVpin, p.vpin);
        r.peakVpin = peakVpin;

        InventoryHalfLifeEstimator halfLifeEstimator = new InventoryHalfLifeEstimator();
        List<Double> invSeries = new ArrayList<>();
        int maxAbsInv = 0;
        for (int v : ctx.hftAggregateInventorySeries) maxAbsInv = Math.max(maxAbsInv, v);
        for (int v : ctx.hftAggregateInventorySeries) invSeries.add((double) v);
        InventoryHalfLifeEstimator.Result hl = halfLifeEstimator.estimate(invSeries, SNAPSHOT_INTERVAL_SEC);
        r.hftInventoryHalfLifeMinutes = hl.halfLifeSeconds / 60.0;
        r.hftMaxAbsAggregateInventory = maxAbsInv;

        r.luldTriggerCount = luld == null ? 0 : luld.triggerCount;
        r.vpinHaltTriggerCount = vpinHalt == null ? 0 : vpinHalt.triggerCount;
        r.totalTrades = ctx.tradeLog.size();
        r.sellProgramExecutedQty = seller.getExecutedQty();

        // approximate max participation: executed / total market volume around execution window
        int windowVolume = 0;
        for (var t : ctx.tradeLog) {
            if (t.timestamp >= SELL_PROGRAM_START_SEC) windowVolume += t.quantity;
        }
        r.sellProgramMaxParticipationPct = windowVolume == 0 ? 0
                : (seller.getExecutedQty() * 100.0) / windowVolume;

        return r;
    }
}
