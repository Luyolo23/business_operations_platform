package com.capaciti.ops.forecast;

/** Ordinary least squares fit y = intercept + slope * x. Pure maths, unit tested. */
public record LinearTrend(double slope, double intercept) {

    public static LinearTrend fit(double[] y) {
        int n = y.length;
        if (n == 0) return new LinearTrend(0, 0);
        if (n == 1) return new LinearTrend(0, y[0]);
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += y[i];
            sumXY += i * y[i];
            sumXX += (double) i * i;
        }
        double denom = n * sumXX - sumX * sumX;
        double slope = denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
        double intercept = (sumY - slope * sumX) / n;
        return new LinearTrend(slope, intercept);
    }

    public double predict(double x) { return intercept + slope * x; }
}
