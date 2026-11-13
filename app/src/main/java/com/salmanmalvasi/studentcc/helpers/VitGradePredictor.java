package com.salmanmalvasi.studentcc.helpers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Conditional performance-grade previews using VIT Chennai's relative bands and pass rules.
 * The app's relative S minimum is 80, as requested for the user's campus practice. The linked
 * regulations publish a 90 minimum; absolute grading retains its published 90/80/... thresholds.
 *
 * Relative previews assume the actual theory class has more than ten students. A contributor
 * sample is not the class strength. Internal /60 previews assume each student's FAT performance
 * stays at their internal percentage; they never claim to know the missing examination marks.
 * Official grading uses rounded-up final totals for every student, a full class distribution,
 * pass criteria and an S-grade rank exception. An aggregate contributor pool cannot reproduce
 * all those inputs, so even /100 results remain estimates.
 *
 * Source: https://chennai.vit.ac.in/files/Academic-Regulations-5.0.pdf, sections 9.4 and 9.5.
 */
public final class VitGradePredictor {
    private static final int RELATIVE_S_MINIMUM = 80;
    private static final int ABSOLUTE_S_MINIMUM = 90;
    public enum Status { AVAILABLE, INCOMPLETE_TOTAL, INSUFFICIENT_SAMPLE, INVALID_DATA }
    public enum PassStatus { UNKNOWN, PASSED, FAILED }

    public static final class Prediction {
        public final Status status;
        public final String grade;
        public final boolean projected;
        public final double totalOutOf100;
        /** Rounded lower marks limits. Values greater than 100 mean that band cannot be reached. */
        public final Map<String, Integer> lowerBoundaries;
        public final boolean assumesPassRequirements;
        /** Same-percentage internal projection implies a theory FAT below its mandatory 40%. */
        public final boolean projectedFatBelowMinimum;
        /** The full-cohort top-three/top-five-percent S exception is absent from this preview. */
        public final boolean specialSNotEstimated;

        private Prediction(Status status, String grade, boolean projected, double totalOutOf100,
                           Map<String, Integer> boundaries, boolean assumesPass, boolean specialS,
                           boolean projectedFatBelowMinimum) {
            this.status = status;
            this.grade = grade;
            this.projected = projected;
            this.totalOutOf100 = totalOutOf100;
            this.lowerBoundaries = Collections.unmodifiableMap(new LinkedHashMap<>(boundaries));
            this.assumesPassRequirements = assumesPass;
            this.projectedFatBelowMinimum = projectedFatBelowMinimum;
            this.specialSNotEstimated = specialS;
        }

        public boolean isAvailable() { return status == Status.AVAILABLE; }
    }

    private VitGradePredictor() {}

    public static Prediction relative(Double personalTotal, int marksBasis, double mean,
                                      double sd, int contributors) {
        return relative(personalTotal, marksBasis, mean, sd, contributors, PassStatus.UNKNOWN);
    }

    public static Prediction relative(Double personalTotal, int marksBasis, double mean,
                                      double sd, int contributors, PassStatus passStatus) {
        Status totalStatus = validateTotal(personalTotal, marksBasis);
        if (totalStatus != Status.AVAILABLE) return unavailable(totalStatus, marksBasis);
        if (passStatus == null) return unavailable(Status.INVALID_DATA, marksBasis);
        // A failed mandatory FAT/lab condition overrides every grade band, independent of peers.
        double projectedTotal = Math.ceil(personalTotal * 100.0 / marksBasis);
        if (passStatus == PassStatus.FAILED) {
            return available("F", marksBasis, projectedTotal, Collections.emptyMap(), false, true);
        }
        // Under the stated projection, the unrounded FAT percentage equals the internal percentage.
        // Rounding a projected grand total up to 40 does not satisfy a FAT below 40%.
        if (marksBasis == 60 && personalTotal < 24.0) {
            return new Prediction(Status.AVAILABLE, "F", true, projectedTotal,
                    Collections.emptyMap(), false, true, true);
        }
        double maximumSd = marksBasis / 2.0;
        if (!Double.isFinite(mean) || !Double.isFinite(sd) || mean < 0 || mean > marksBasis
                || sd < 0 || sd > maximumSd || contributors < 0) {
            return unavailable(Status.INVALID_DATA, marksBasis);
        }
        // With one contributor or no spread the relative bands collapse; suppress false certainty.
        if (contributors < 2 || sd == 0) return unavailable(Status.INSUFFICIENT_SAMPLE, marksBasis);
        double scaledMean = mean * 100.0 / marksBasis;
        double scaledSd = sd * 100.0 / marksBasis;
        LinkedHashMap<String, Integer> boundaries = new LinkedHashMap<>();
        boundaries.put("S", Math.max(RELATIVE_S_MINIMUM, roundedBoundary(scaledMean + 1.5 * scaledSd)));
        boundaries.put("A", roundedBoundary(scaledMean + 0.5 * scaledSd));
        boundaries.put("B", roundedBoundary(scaledMean - 0.5 * scaledSd));
        boundaries.put("C", roundedBoundary(scaledMean - scaledSd));
        boundaries.put("D", roundedBoundary(scaledMean - 1.5 * scaledSd));
        // VIT permits E at 50 even when a high class mean would otherwise put it below E.
        boundaries.put("E", Math.min(50, roundedBoundary(scaledMean - 2.0 * scaledSd)));
        return available(gradeAt(projectedTotal, boundaries), marksBasis, projectedTotal,
                boundaries, passStatus == PassStatus.UNKNOWN, true);
    }

    /** For lab/project/soft-skills courses, or a known theory class strength of ten or fewer. */
    public static Prediction absolute(Double personalTotal, int marksBasis) {
        return absolute(personalTotal, marksBasis, PassStatus.UNKNOWN);
    }

    public static Prediction absolute(Double personalTotal, int marksBasis, PassStatus passStatus) {
        Status totalStatus = validateTotal(personalTotal, marksBasis);
        if (totalStatus != Status.AVAILABLE) return unavailable(totalStatus, marksBasis);
        if (passStatus == null) return unavailable(Status.INVALID_DATA, marksBasis);
        double projectedTotal = Math.ceil(personalTotal * 100.0 / marksBasis);
        LinkedHashMap<String, Integer> boundaries = new LinkedHashMap<>();
        boundaries.put("S", ABSOLUTE_S_MINIMUM);
        boundaries.put("A", 80);
        boundaries.put("B", 70);
        boundaries.put("C", 60);
        boundaries.put("D", 55);
        boundaries.put("E", 50);
        return available(passStatus == PassStatus.FAILED ? "F" : gradeAt(projectedTotal, boundaries),
                marksBasis, projectedTotal, boundaries, passStatus == PassStatus.UNKNOWN, false);
    }

    private static Status validateTotal(Double total, int basis) {
        if (basis != 60 && basis != 100) return Status.INVALID_DATA;
        if (total == null) return Status.INCOMPLETE_TOTAL;
        if (!Double.isFinite(total) || total < 0 || total > basis) return Status.INVALID_DATA;
        return Status.AVAILABLE;
    }

    private static int roundedBoundary(double marks) {
        return (int) Math.max(0, Math.round(marks));
    }

    private static String gradeAt(double total, Map<String, Integer> boundaries) {
        for (Map.Entry<String, Integer> boundary : boundaries.entrySet()) {
            if (total >= boundary.getValue()) return boundary.getKey();
        }
        return "F";
    }

    private static Prediction available(String grade, int basis, double total,
                                        Map<String, Integer> boundaries, boolean assumesPass, boolean specialS) {
        return new Prediction(Status.AVAILABLE, grade, basis == 60, total, boundaries, assumesPass, specialS, false);
    }

    private static Prediction unavailable(Status status, int basis) {
        return new Prediction(status, null, basis == 60, Double.NaN, Collections.emptyMap(), false, false, false);
    }
}
