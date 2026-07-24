package com.flashcrash.ml;

import java.util.List;

/**
 * ALGORITHM 8: Logistic regression via batch gradient descent, implemented
 * from first principles (no external ML libraries):
 *
 *      p(y=1|x) = sigma(w.x + b),   sigma(z) = 1/(1+e^-z)
 *      L(w,b)   = -1/N * sum[ y*log(p) + (1-y)*log(1-p) ]  + (lambda/2N)*||w||^2
 *
 * Gradients:
 *      dL/dw_j = 1/N * sum[ (p_i - y_i) * x_ij ] + (lambda/N) * w_j
 *      dL/db   = 1/N * sum[ (p_i - y_i) ]
 *
 * Features are standardized (zero mean, unit variance) before training,
 * which both speeds convergence and makes the learned weights comparable
 * in magnitude (a crude feature-importance signal).
 */
public class LogisticRegression {
    private double[] weights;
    private double bias;
    private double[] featureMean;
    private double[] featureStd;
    private final double l2Lambda;

    public LogisticRegression(double l2Lambda) {
        this.l2Lambda = l2Lambda;
    }

    private double sigmoid(double z) {
        if (z >= 0) {
            double e = Math.exp(-z);
            return 1.0 / (1.0 + e);
        } else {
            double e = Math.exp(z);
            return e / (1.0 + e);
        }
    }

    public void fit(List<double[]> rawFeatures, List<Integer> labels, int epochs, double learningRate) {
        int n = rawFeatures.size();
        int d = rawFeatures.get(0).length;
        featureMean = new double[d];
        featureStd = new double[d];
        for (double[] row : rawFeatures)
            for (int j = 0; j < d; j++) featureMean[j] += row[j];
        for (int j = 0; j < d; j++) featureMean[j] /= n;

        for (double[] row : rawFeatures)
            for (int j = 0; j < d; j++) featureStd[j] += Math.pow(row[j] - featureMean[j], 2);
        for (int j = 0; j < d; j++) {
            featureStd[j] = Math.sqrt(featureStd[j] / n);
            if (featureStd[j] < 1e-9) featureStd[j] = 1.0;
        }

        double[][] X = new double[n][d];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < d; j++)
                X[i][j] = (rawFeatures.get(i)[j] - featureMean[j]) / featureStd[j];

        weights = new double[d];
        bias = 0.0;

        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] gradW = new double[d];
            double gradB = 0.0;
            for (int i = 0; i < n; i++) {
                double z = bias;
                for (int j = 0; j < d; j++) z += weights[j] * X[i][j];
                double p = sigmoid(z);
                double error = p - labels.get(i);
                for (int j = 0; j < d; j++) gradW[j] += error * X[i][j];
                gradB += error;
            }
            for (int j = 0; j < d; j++) {
                gradW[j] = gradW[j] / n + (l2Lambda / n) * weights[j];
                weights[j] -= learningRate * gradW[j];
            }
            bias -= learningRate * (gradB / n);
        }
    }

    public double predictProba(double[] rawFeature) {
        int d = weights.length;
        double z = bias;
        for (int j = 0; j < d; j++) {
            double xs = (rawFeature[j] - featureMean[j]) / featureStd[j];
            z += weights[j] * xs;
        }
        return sigmoid(z);
    }

    public double[] getWeights() { return weights; }
    public double getBias() { return bias; }

    public static class Metrics {
        public double accuracy, precision, recall, f1, auc;
        @Override public String toString() {
            return String.format("accuracy=%.3f precision=%.3f recall=%.3f f1=%.3f auc=%.3f",
                    accuracy, precision, recall, f1, auc);
        }
    }

    public Metrics evaluate(List<double[]> features, List<Integer> labels, double threshold) {
        int tp = 0, fp = 0, tn = 0, fn = 0;
        double[] scores = new double[features.size()];
        for (int i = 0; i < features.size(); i++) {
            double p = predictProba(features.get(i));
            scores[i] = p;
            int pred = p >= threshold ? 1 : 0;
            int actual = labels.get(i);
            if (pred == 1 && actual == 1) tp++;
            else if (pred == 1 && actual == 0) fp++;
            else if (pred == 0 && actual == 0) tn++;
            else fn++;
        }
        Metrics m = new Metrics();
        m.accuracy = (tp + tn) / (double) features.size();
        m.precision = tp + fp == 0 ? 0 : tp / (double) (tp + fp);
        m.recall = tp + fn == 0 ? 0 : tp / (double) (tp + fn);
        m.f1 = (m.precision + m.recall == 0) ? 0 : 2 * m.precision * m.recall / (m.precision + m.recall);
        m.auc = computeAUC(scores, labels);
        return m;
    }

    /** Scans candidate thresholds and returns the one maximizing F1; useful under severe class
     *  imbalance (rare crash events), where the default 0.5 cutoff predicts "no crash" for everything. */
    public double bestF1Threshold(List<double[]> features, List<Integer> labels) {
        double best = 0.5, bestF1 = -1;
        for (double t = 0.01; t < 1.0; t += 0.01) {
            Metrics m = evaluate(features, labels, t);
            if (m.f1 > bestF1) { bestF1 = m.f1; best = t; }
        }
        return best;
    }

    /** Rank-based (Mann-Whitney U) AUC computation; O(n log n), no external stats library needed. */
    private double computeAUC(double[] scores, List<Integer> labels) {
        int n = scores.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(scores[a], scores[b]));

        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && scores[idx[j + 1]] == scores[idx[i]]) j++;
            double avgRank = (i + j) / 2.0 + 1;
            for (int k = i; k <= j; k++) ranks[idx[k]] = avgRank;
            i = j + 1;
        }
        double sumRanksPos = 0;
        int nPos = 0, nNeg = 0;
        for (int k = 0; k < n; k++) {
            if (labels.get(k) == 1) { sumRanksPos += ranks[k]; nPos++; }
            else nNeg++;
        }
        if (nPos == 0 || nNeg == 0) return Double.NaN;
        return (sumRanksPos - nPos * (nPos + 1) / 2.0) / ((double) nPos * nNeg);
    }
}
