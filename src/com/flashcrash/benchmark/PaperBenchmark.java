package com.flashcrash.benchmark;

/**
 * Reference figures drawn from the published record of the May 6, 2010
 * "Flash Crash", used to calibrate and validate the simulation:
 *
 * [1] Kirilenko, A., Kyle, A.S., Samadi, M., & Tuzun, T. (2017).
 *     "The Flash Crash: High-Frequency Trading in an Electronic Market."
 *     The Journal of Finance, 72(3), 967-998.
 *       - HFT aggregate inventories rarely exceeded ~3,000 E-mini contracts
 *         and mean-reverted to zero with a half-life of roughly two minutes.
 *       - Intraday (high-low) volatility on May 6 was 9.82%, about 6.4x the
 *         1.54% average of the three preceding trading days.
 *
 * [2] U.S. CFTC & SEC (2010). "Findings Regarding the Market Events of
 *     May 6, 2010." Report of the Staffs of the CFTC and SEC to the Joint
 *     Advisory Committee on Emerging Regulatory Issues.
 *       - A mutual-fund sell program executed 75,000 E-mini contracts
 *         (~$4.1B notional) via a percentage-of-volume algorithm that
 *         disregarded price, completing in ~20 minutes.
 *       - The 75,000 contracts were ~1.3% of the day's total E-mini volume
 *         (5.7M contracts) but under 9% of volume during their own
 *         execution window.
 *       - E-mini buy-side liquidity fell ~55% (from ~$6.0B to ~$2.65B)
 *         around the time of the crash.
 *       - Prices fell more than 5% within minutes before rebounding.
 *
 * [3] Easley, D., Lopez de Prado, M.M., & O'Hara, M. (2012). "Flow Toxicity
 *     and Liquidity in a High-Frequency World." Review of Financial
 *     Studies, 25(5), 1457-1493.
 *       - VPIN (order-flow toxicity) reached its historical high shortly
 *         before the crash, ahead of the price decline becoming visible.
 *
 * [4] Avellaneda, M., & Stoikov, S. (2008). "High-Frequency Trading in a
 *     Limit Order Book." Quantitative Finance, 8(3), 217-224.
 *       - Source of the inventory-skewed market-making model used by
 *         HFTMarketMaker.
 */
public final class PaperBenchmark {
    private PaperBenchmark() {}

    public static final int SELL_PROGRAM_CONTRACTS = 75_000;
    public static final double SELL_PROGRAM_NOTIONAL_USD = 4.1e9;
    public static final double SELL_PROGRAM_PARTICIPATION_MAX_PCT = 9.0;
    public static final double SELL_PROGRAM_SHARE_OF_DAY_PCT = 1.3;
    public static final double SELL_PROGRAM_DURATION_MINUTES = 20.0;

    public static final int HFT_TYPICAL_MAX_INVENTORY_CONTRACTS = 3_000;
    public static final double HFT_INVENTORY_HALFLIFE_MINUTES = 2.0;

    public static final double CRASH_PRICE_DROP_PCT_MIN = 5.0;
    public static final double BUY_SIDE_LIQUIDITY_DROP_PCT = 55.0;

    public static final double INTRADAY_VOLATILITY_CRASH_DAY_PCT = 9.82;
    public static final double INTRADAY_VOLATILITY_NORMAL_DAY_PCT = 1.54;
}
