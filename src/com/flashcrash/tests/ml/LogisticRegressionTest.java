package com.flashcrash.tests.ml;

import com.flashcrash.ml.LogisticRegression;
import com.flashcrash.tests.framework.TestReport;
import com.flashcrash.tests.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for LogisticRegression: the from-scratch batch-gradient-descent
 * classifier used as the early-warning model for crash onset. Rather than
 * checking exact learned weights (which depend on the optimizer's exact
 * trajectory and aren't a meaningful thing to hand-verify), we train it on
 * a small, obviously linearly-separable synthetic dataset and check the
 * PREDICTIONS it produces -- the actual thing the rest of the project
 * depends on this class getting right.
 */
public class LogisticRegressionTest implements TestSuite {

    @Override public String name() { return "LogisticRegression (from-scratch gradient descent)"; }

    @Override
    public void run(TestReport report) {
        report.enterSuite(name());
    }

    /**
     * A simple 1-feature dataset: label = 1 whenever the feature exceeds 0,
     * label = 0 otherwise, with a comfortable margin around the boundary so
     * a linear classifier should have no trouble separating it.
     */
    private void testLearnsAnObviouslySeparableDataset(TestReport report) {
        List<double[]> features = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        for (int i = -10; i <= 10; i++) {
            if (i == 0) continue; // skip the boundary itself, ambiguous by construction
            features.add(new double[]{i});
            labels.add(i > 0 ? 1 : 0);
        }

        LogisticRegression model = new LogisticRegression(0.001 /* light L2 regularization */);
        model.fit(features, labels, 2000, 0.5);

        report.check(model.predictProba(new double[]{10}) > 0.9,
                "a strongly positive example gets a high predicted probability (>0.9)");
        report.check(model.predictProba(new double[]{-10}) < 0.1,
                "a strongly negative example gets a low predicted probability (<0.1)");

        LogisticRegression.Metrics metrics = model.evaluate(features, labels, 0.5);
        report.check(metrics.accuracy > 0.9,
                "training accuracy on an obviously separable dataset is high (actual: " + metrics.accuracy + ")");
    }

    private void testAucIsNearPerfectOnSeparableData(TestReport report) {
        List<double[]> features = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        for (int i = -20; i <= 20; i++) {
            if (i == 0) continue;
            features.add(new double[]{i, i * 0.5}); // two correlated features
            labels.add(i > 0 ? 1 : 0);
        }

        LogisticRegression model = new LogisticRegression(0.001);
        model.fit(features, labels, 2000, 0.3);
        LogisticRegression.Metrics metrics = model.evaluate(features, labels, 0.5);

        /**
         * AUC of 1.0 means the model ranks every positive example above
         * every negative example -- the theoretical best possible score.
         */
        report.check(metrics.auc > 0.95,
                "AUC (a threshold-independent ranking metric) is near the theoretical maximum of 1.0 "
                        + "on cleanly separable data (actual: " + metrics.auc + ")");
    }
}
