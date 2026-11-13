package com.salmanmalvasi.studentcc.integrations.unicc;

/** Aggregate internal-mark statistics returned by UniCC; never an official grade. */
public final class UniCCClassStatistics {
    /** Current public UniCC source aggregates internal marks out of 60. Its API omits this metadata. */
    public static final int CURRENT_MARKS_BASIS = 60;

    public final double mean;
    public final double variance;
    public final double sd;
    public final int count;
    public final int marksBasis = CURRENT_MARKS_BASIS;

    UniCCClassStatistics(double mean, double variance, double sd, int count) {
        if (!Double.isFinite(mean) || !Double.isFinite(variance) || !Double.isFinite(sd)
                || mean < 0 || mean > CURRENT_MARKS_BASIS || variance < 0 || variance > 900
                || sd < 0 || sd > 30 || count < 1
                || Math.abs(sd * sd - variance) > Math.max(0.01, variance * 0.001)) {
            throw new IllegalArgumentException("Invalid UniCC internal-mark statistics");
        }
        this.mean = mean;
        this.variance = variance;
        this.sd = sd;
        this.count = count;
    }
}
