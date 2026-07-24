package com.flashcrash;

public class RunResult {
    public long seed;
    public double openPrice;
    public double bottomPrice;
    public double finalPrice;
    public double timeOfBottomSec;
    public double maxDrawdownPct;
    public boolean crashOccurred;      // drawdown >= PaperBenchmark.CRASH_PRICE_DROP_PCT_MIN
    public double recoveryTimeSec = -1; // time from bottom to back within 1% of open, or -1
    public double peakVpin;
    public double hftInventoryHalfLifeMinutes;
    public int hftMaxAbsAggregateInventory;
    public int luldTriggerCount;
    public int vpinHaltTriggerCount;
    public int totalTrades;
    public int sellProgramExecutedQty;
    public double sellProgramMaxParticipationPct;

    @Override
    public String toString() {
        return String.format(
            "seed=%d drawdown=%.2f%% crash=%s bottomT=%.0fs recovery=%.0fs peakVPIN=%.3f " +
            "invHalfLife=%.2fmin maxHftInv=%d luldTrig=%d vpinTrig=%d trades=%d sellExec=%d maxPart=%.1f%%",
            seed, maxDrawdownPct, crashOccurred, timeOfBottomSec, recoveryTimeSec, peakVpin,
            hftInventoryHalfLifeMinutes, hftMaxAbsAggregateInventory, luldTriggerCount, vpinHaltTriggerCount,
            totalTrades, sellProgramExecutedQty, sellProgramMaxParticipationPct);
    }
}
