package com.flashcrash.tests.util;

import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;
import com.flashcrash.util.CsvWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for CsvWriter: the small utility Main uses to export the flagship
 * run's time series (price, VPIN, etc.) for external plotting. We write to
 * a real temporary file and read the bytes back rather than mocking
 * anything, since the entire point of this class is its file I/O -- a
 * test that didn't touch the filesystem wouldn't actually be testing it.
 */
public class CsvWriterTest implements TestSuite {

    @Override public String name() { return "CsvWriter"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());
    }

    private void testWritesHeaderAndRowsCorrectly(TestReport report) {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("csvwriter_test", ".csv");
            tempFile.deleteOnExit();

            List<double[]> rows = new ArrayList<>();
            rows.add(new double[]{0.0, 1165.0, 0.5});
            rows.add(new double[]{1.0, 1166.25, -0.2});

            CsvWriter.writeSeries(tempFile.getAbsolutePath(), new String[]{"time_sec", "mid_price", "imbalance"}, rows);

            List<String> lines = Files.readAllLines(tempFile.toPath());
            report.checkEquals(lines.size(), 3L, "output file has exactly 1 header line + 2 data rows");
            report.checkEquals(lines.get(0), "time_sec,mid_price,imbalance", "header line matches the provided column names exactly");
            report.checkEquals(lines.get(1), "0.0,1165.0,0.5", "first data row is written with comma separation and no extra formatting");
            report.checkEquals(lines.get(2), "1.0,1166.25,-0.2", "second data row is written correctly, including a negative value");
        } catch (IOException e) {
            report.check(false, "CsvWriter.writeSeries() did not throw an IOException during normal operation: " + e);
        } finally {
            if (tempFile != null) tempFile.delete();
        }
    }
}
