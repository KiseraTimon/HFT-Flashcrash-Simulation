package com.flashcrash;

import java.io.IOException;

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

    }
}
