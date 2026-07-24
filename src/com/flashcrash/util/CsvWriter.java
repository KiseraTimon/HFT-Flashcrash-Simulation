package com.flashcrash.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public final class CsvWriter {
    private CsvWriter() {}

    public static void writeSeries(String path, String[] header, List<double[]> rows) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(path)))) {
            pw.println(String.join(",", header));
            for (double[] row : rows) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(row[i]);
                }
                pw.println(sb);
            }
        }
    }
}
