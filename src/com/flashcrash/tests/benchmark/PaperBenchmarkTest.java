package com.flashcrash.tests.benchmark;

import com.flashcrash.benchmark.PaperBenchmark;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

/**
 * Tests for PaperBenchmark: not an algorithm, just a set of published
 * reference constants each of this project's results is checked against.
 * There's no "logic" to test here, but that's exactly why a lightweight
 * sanity check is worth having: this class is the single source of truth
 * for what "correct" means throughout the rest of the project (see
 * Main.printBenchmarkComparison), so an accidental typo here (an extra
 * zero, a misplaced decimal point) would silently corrupt every downstream
 * comparison without any other test being able to catch it.
 */
public class PaperBenchmarkTest implements TestSuite {

    @Override public String name() { return "PaperBenchmark (reference constants sanity check)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());

        report.checkEquals(PaperBenchmark.SELL_PROGRAM_CONTRACTS, 75_000L,
                "the sell program size matches the SEC-CFTC report's published figure (75,000 E-mini contracts)");
        report.check(PaperBenchmark.SELL_PROGRAM_NOTIONAL_USD > 0,
                "sell program notional value is a positive dollar amount");
        report.check(PaperBenchmark.SELL_PROGRAM_PARTICIPATION_MAX_PCT > 0 && PaperBenchmark.SELL_PROGRAM_PARTICIPATION_MAX_PCT <= 100,
                "sell program participation rate is a valid percentage (0, 100]");
        report.check(PaperBenchmark.SELL_PROGRAM_SHARE_OF_DAY_PCT > 0 && PaperBenchmark.SELL_PROGRAM_SHARE_OF_DAY_PCT < PaperBenchmark.SELL_PROGRAM_PARTICIPATION_MAX_PCT,
                "the sell program's share of the FULL DAY's volume (1.3%) is smaller than its share during its "
                        + "OWN execution window (<=9%) -- this ordering must hold, since the execution window is a "
                        + "small fraction of the trading day");

        report.check(PaperBenchmark.HFT_TYPICAL_MAX_INVENTORY_CONTRACTS > 0,
                "typical HFT max inventory is a positive contract count");
        report.check(PaperBenchmark.HFT_TYPICAL_MAX_INVENTORY_CONTRACTS < PaperBenchmark.SELL_PROGRAM_CONTRACTS,
                "a single HFT's typical inventory cap (~3,000) is much smaller than the sell program's total size "
                        + "(75,000) -- this is the core reason the 'hot potato' mechanism exists in the first place: "
                        + "no single market maker could absorb the whole program alone");
        report.check(PaperBenchmark.HFT_INVENTORY_HALFLIFE_MINUTES > 0,
                "inventory half-life is a positive duration");

        report.check(PaperBenchmark.CRASH_PRICE_DROP_PCT_MIN > 0 && PaperBenchmark.CRASH_PRICE_DROP_PCT_MIN < 100,
                "the crash threshold is a plausible percentage drop");
        report.check(PaperBenchmark.BUY_SIDE_LIQUIDITY_DROP_PCT > 0 && PaperBenchmark.BUY_SIDE_LIQUIDITY_DROP_PCT <= 100,
                "buy-side liquidity drop is a valid percentage");

        report.check(PaperBenchmark.INTRADAY_VOLATILITY_CRASH_DAY_PCT > PaperBenchmark.INTRADAY_VOLATILITY_NORMAL_DAY_PCT,
                "recorded intraday volatility on the crash day is higher than on a normal day (as it must be, by definition)");
    }
}
