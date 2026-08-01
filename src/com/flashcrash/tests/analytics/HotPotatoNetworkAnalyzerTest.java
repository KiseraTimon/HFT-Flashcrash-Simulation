package com.flashcrash.tests.analytics;

import com.flashcrash.analytics.HotPotatoNetworkAnalyzer;
import com.flashcrash.core.MarketConstants;
import com.flashcrash.core.Trade;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

import java.util.List;

/**
 * Tests for HotPotatoNetworkAnalyzer's two techniques:
 *  (a) turnover ratio (gross volume / (1 + |net position change|))
 *  (b) Tarjan's Strongly-Connected-Components algorithm on the directed
 *      seller->buyer trade-flow graph.
 *
 * Both are tested against small, hand-constructed toy scenarios where the
 * "correct answer" can be verified by direct arithmetic (turnover) or by
 * inspection (a deliberately constructed 3-node cycle for the SCC test).
 */
public class HotPotatoNetworkAnalyzerTest implements TestSuite {

    @Override public String name() { return "HotPotatoNetworkAnalyzer"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());

        testTurnoverRatioIdentifiesHotPotatoTrader(report);
        testTarjanFindsAKnownThreeNodeCycle(report);
        testTarjanDoesNotMergeUnconnectedTraders(report);
    }

    private Trade trade(String buyer, String seller, int qty, double timestamp) {
        return new Trade(1, 2, buyer, seller, MarketConstants.priceToTicks(100.0), qty, timestamp, true);
    }

    private void testTurnoverRatioIdentifiesHotPotatoTrader(TestReport report) {
        /**
         * Trader A buys 10 from B, then immediately sells the same 10 to C.
         * A's gross volume is 20 (10 bought + 10 sold) but A's NET position
         * change is 0 -- exactly the "hot potato" signature: lots of
         * trading, no actual risk absorbed. B and C, by contrast, each
         * trade once and are left holding a real net position change.
         */
        List<Trade> trades = List.of(
                trade("A", "B", 10, 0.0), // A buys from B
                trade("C", "A", 10, 1.0)  // A sells to C
        );

        HotPotatoNetworkAnalyzer analyzer = new HotPotatoNetworkAnalyzer();
        List<HotPotatoNetworkAnalyzer.TurnoverResult> results = analyzer.turnoverRatios(trades, 0.0, 10.0);

        HotPotatoNetworkAnalyzer.TurnoverResult resultA = findByTrader(results, "A");
        HotPotatoNetworkAnalyzer.TurnoverResult resultB = findByTrader(results, "B");

        report.checkEquals(resultA.grossVolume, 20L, "A's gross volume correctly sums both the buy and the sell (10+10=20)");
        report.checkEquals(resultA.netPositionChange, 0L, "A's net position change is 0 (bought 10, then sold the same 10)");

        // ratio = gross / (1 + |net|) = 20 / (1+0) = 20.0
        report.checkEquals(resultA.turnoverRatio, 20.0, 1e-9, "A's turnover ratio matches the hand-computed value 20/(1+0)=20.0");

        report.checkEquals(resultB.grossVolume, 10L, "B's gross volume is just the one trade (10)");
        report.checkEquals(resultB.netPositionChange, -10L, "B went short 10 (sold to A and never bought back)");

        report.check(resultA.turnoverRatio > resultB.turnoverRatio,
                "the trader with high gross volume but zero net change (A, the 'hot potato') "
                        + "correctly ranks ABOVE a trader with the same gross volume but a real net position change (B)");

        // The method's contract (per its own sort call) is descending order by turnover ratio.
        boolean sortedDescending = true;
        for (int i = 1; i < results.size(); i++) {
            if (results.get(i).turnoverRatio > results.get(i - 1).turnoverRatio) sortedDescending = false;
        }
        report.check(sortedDescending, "results are returned sorted by turnover ratio, highest first");
    }

    private HotPotatoNetworkAnalyzer.TurnoverResult findByTrader(
            List<HotPotatoNetworkAnalyzer.TurnoverResult> results, String traderId) {
        return results.stream().filter(r -> r.traderId.equals(traderId)).findFirst().orElseThrow();
    }

    private void testTarjanFindsAKnownThreeNodeCycle(TestReport report) {
        /**
         * Deliberately construct a closed loop of contracts: A -> B -> C -> A
         * (edges are seller -> buyer, i.e. "who did the contract flow to").
         * This is exactly the graph-theoretic signature of hot-potato
         * trading within a closed group, and Tarjan's algorithm must find
         * all three nodes as one strongly-connected component.
         */
        List<Trade> trades = List.of(
                trade("B", "A", 5, 0.0), // seller A -> buyer B
                trade("C", "B", 5, 1.0), // seller B -> buyer C
                trade("A", "C", 5, 2.0)  // seller C -> buyer A  (closes the loop)
        );

        HotPotatoNetworkAnalyzer analyzer = new HotPotatoNetworkAnalyzer();
        List<List<String>> sccs = analyzer.stronglyConnectedComponents(trades, 0.0, 10.0);

        List<String> theCycle = sccs.stream().filter(scc -> scc.size() == 3).findFirst().orElse(null);
        report.check(theCycle != null, "Tarjan's algorithm finds exactly one non-trivial (size>1) strongly-connected component");
        if (theCycle != null) {
            report.check(theCycle.contains("A") && theCycle.contains("B") && theCycle.contains("C"),
                    "the strongly-connected component found is precisely {A, B, C} -- the constructed cycle");
        }
    }

    private void testTarjanDoesNotMergeUnconnectedTraders(TestReport report) {
        /**
         * D sells to E once; there's no path back from E to D, so {D, E}
         * must NOT be reported as a strongly-connected component -- each
         * should be its own trivial (size-1) component. This guards against
         * an overly permissive implementation that treats any edge as a cycle.
         */
        List<Trade> trades = List.of(trade("E", "D", 5, 0.0));

        HotPotatoNetworkAnalyzer analyzer = new HotPotatoNetworkAnalyzer();
        List<List<String>> sccs = analyzer.stronglyConnectedComponents(trades, 0.0, 10.0);

        boolean anyNonTrivialComponent = sccs.stream().anyMatch(scc -> scc.size() > 1);
        report.check(!anyNonTrivialComponent,
                "a single one-directional trade between two traders produces no non-trivial strongly-connected "
                        + "component (there's no cycle -- D sold to E, but nothing flows back from E to D)");
    }
}
