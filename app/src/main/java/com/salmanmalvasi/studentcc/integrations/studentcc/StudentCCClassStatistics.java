package com.salmanmalvasi.studentcc.integrations.studentcc;

/** Aggregate data; individual contributed totals are never returned by the public statistics API. */
public final class StudentCCClassStatistics {
    public final StudentCCClassScope scope;
    public final Double mean, variance, sd;
    public final int count, minContributors, marksBasis;
    /** Null means a legacy response did not provide the public class tally. */
    public final Integer cgpaAtLeastNineCount;
    public final long updatedAtMillis;

    StudentCCClassStatistics(StudentCCClassScope scope, int count, int minimum, Double mean,
                             Double variance, Double sd, long updatedAtMillis) {
        this(scope, count, minimum, mean, variance, sd, updatedAtMillis, null);
    }
    StudentCCClassStatistics(StudentCCClassScope scope, int count, int minimum, Double mean,
                             Double variance, Double sd, long updatedAtMillis, Integer cgpaAtLeastNineCount) {
        if (scope == null || count < 0 || minimum != 1 || updatedAtMillis < 0) throw new IllegalArgumentException("Invalid aggregate metadata");
        if (cgpaAtLeastNineCount != null && cgpaAtLeastNineCount < 0) throw new IllegalArgumentException("Invalid class CGPA tally");
        if (count >= minimum) {
            if (!finite(mean) || !finite(variance) || !finite(sd) || mean < 0 || mean > scope.marksBasis
                    || variance < 0 || variance > scope.marksBasis * scope.marksBasis / 4.0
                    || sd < 0 || sd > scope.marksBasis / 2.0
                    || (count == 1 && (variance != 0 || sd != 0))
                    || Math.abs(sd * sd - variance) > Math.max(0.01, variance * 0.001) || updatedAtMillis == 0) {
                throw new IllegalArgumentException("Invalid aggregate values");
            }
        } else if (mean != null || variance != null || sd != null) throw new IllegalArgumentException("Empty cohorts have no aggregate values");
        this.scope = scope; this.count = count; this.minContributors = minimum;
        this.mean = mean; this.variance = variance; this.sd = sd;
        this.marksBasis = scope.marksBasis; this.updatedAtMillis = updatedAtMillis;
        this.cgpaAtLeastNineCount = cgpaAtLeastNineCount;
    }

    public boolean hasValues() { return count >= minContributors && mean != null; }
    private static boolean finite(Double value) { return value != null && Double.isFinite(value); }
}
