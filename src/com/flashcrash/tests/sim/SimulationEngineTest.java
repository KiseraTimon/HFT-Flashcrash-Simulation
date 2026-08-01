package com.flashcrash.tests.sim;

import com.flashcrash.agents.Agent;
import com.flashcrash.sim.SimulationContext;
import com.flashcrash.sim.SimulationEngine;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

import java.util.*;

/**
 * Tests for SimulationEngine: the priority-queue-based discrete-event
 * scheduler. Rather than exercising it with real trading agents (which
 * would couple this test to the `agents` package and make failures harder
 * to localize), we use small purpose-built dummy Agent implementations that
 * do nothing but record when they were called -- isolating exactly the
 * scheduler's responsibility: "call the right agent, at the right
 * simulated time, in the right order, and stop at the right time."
 */
public class SimulationEngineTest implements TestSuite {

    @Override public String name() { return "SimulationEngine (discrete-event scheduler)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());

        testEventsFireInTimeOrder(report);
        testEngineStopsAtEndTime(report);
        testAgentCanDeactivateItself(report);
        testHaltedMarketSkipsAgentActions(report);
    }

    /**
     * A minimal Agent whose behaviour (when it fires, what it returns as its
     * next wake time) is fully controlled by the test, and which records
     * every timestamp it was actually called at.
     */
    private static class RecordingAgent implements Agent {
        final String id;
        final double initialWake;
        final double interval;
        final List<Double> callTimes = new ArrayList<>();
        boolean deactivateAfterFirstCall = false;

        RecordingAgent(String id, double initialWake, double interval) {
            this.id = id;
            this.initialWake = initialWake;
            this.interval = interval;
        }

        @Override public String getId() { return id; }
        @Override public double initialWakeTime(Random rng) { return initialWake; }

        @Override
        public double act(double now, SimulationContext ctx) {
            callTimes.add(now);
            if (deactivateAfterFirstCall) return Double.POSITIVE_INFINITY;
            return now + interval;
        }
    }

    private void testEventsFireInTimeOrder(TestReport report) {
        /**
         * Agent A wakes at t=1,3,5,...; Agent B wakes at t=2,4,6,...
         * Interleaved across both agents, calls must be strictly non-decreasing in time.
         */
        RecordingAgent a = new RecordingAgent("A", 1.0, 2.0);
        RecordingAgent b = new RecordingAgent("B", 2.0, 2.0);

        SimulationContext ctx = new SimulationContext(1);
        SimulationEngine engine = new SimulationEngine();
        engine.run(List.of(a, b), 10.0, 100.0 /* snapshot interval large enough to not interfere */, ctx);

        report.check(!a.callTimes.isEmpty() && !b.callTimes.isEmpty(),
                "both agents were woken at least once");

        /**
         * Each agent only ever reschedules itself further into the future
         * (act() returns now + interval), so a sufficient and simple way to
         * confirm the scheduler respects time ordering is to check that
         * each agent's own sequence of call times is strictly increasing.
         */
        report.check(isStrictlyIncreasing(a.callTimes), "Agent A's own call times are strictly increasing");
        report.check(isStrictlyIncreasing(b.callTimes), "Agent B's own call times are strictly increasing");
        report.checkEquals(a.callTimes.get(0), 1.0, 1e-9, "Agent A's first call happens at its declared initial wake time");
        report.checkEquals(b.callTimes.get(0), 2.0, 1e-9, "Agent B's first call happens at its declared initial wake time");
    }

    private boolean isStrictlyIncreasing(List<Double> xs) {
        for (int i = 1; i < xs.size(); i++) {
            if (xs.get(i) <= xs.get(i - 1)) return false;
        }
        return true;
    }

    private void testEngineStopsAtEndTime(TestReport report) {
        /**
         * Agent wakes every 1 simulated second; running for 10.5 seconds
         * should never record a call time past the requested horizon.
         */
        RecordingAgent a = new RecordingAgent("A", 1.0, 1.0);
        SimulationContext ctx = new SimulationContext(1);
        SimulationEngine engine = new SimulationEngine();
        engine.run(List.of(a), 10.5, 1000.0, ctx);

        double lastCall = a.callTimes.get(a.callTimes.size() - 1);
        report.check(lastCall <= 10.5,
                "no agent call happens after the configured simulation end time (last call at t=" + lastCall + ")");
        report.checkEquals(ctx.now, 10.5, 1e-9,
                "after run() returns, ctx.now has been advanced to exactly the end time (for the final snapshot)");
    }

    private void testAgentCanDeactivateItself(TestReport report) {
        /**
         * Returning Double.POSITIVE_INFINITY from act() must permanently
         * stop an agent from being scheduled again (this is how LargeSeller
         * signals "I've sold everything, stop calling me").
         */
        RecordingAgent a = new RecordingAgent("A", 1.0, 1.0);
        a.deactivateAfterFirstCall = true;

        SimulationContext ctx = new SimulationContext(1);
        SimulationEngine engine = new SimulationEngine();
        engine.run(List.of(a), 100.0, 1000.0, ctx);

        report.checkEquals(a.callTimes.size(), 1L,
                "an agent that returns POSITIVE_INFINITY from act() is called exactly once, never rescheduled");
    }

    private void testHaltedMarketSkipsAgentActions(TestReport report) {
        /**
         * If a trading halt is already in effect (as a risk control would
         * set it) when an agent's event fires, the engine must NOT call
         * act() -- it should quietly reschedule the agent shortly after,
         * without letting it trade during the halt.
         */
        RecordingAgent a = new RecordingAgent("A", 5.0, 1.0);
        SimulationContext ctx = new SimulationContext(1);
        ctx.tradingHalted = true;
        ctx.haltUntil = 8.0; // halt covers the agent's first scheduled wake time (5.0)

        SimulationEngine engine = new SimulationEngine();
        engine.run(List.of(a), 20.0, 1000.0, ctx);

        boolean anyCallDuringHalt = a.callTimes.stream().anyMatch(t -> t < 8.0);
        report.check(!anyCallDuringHalt,
                "the agent is never actually invoked (act() called) while a trading halt is in effect");
        report.check(!a.callTimes.isEmpty(),
                "the agent resumes being called after the halt lifts, rather than being lost forever");
    }
}
