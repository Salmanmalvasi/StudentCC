package com.salmanmalvasi.studentcc.integrations.studentcc;

/** Converts an authoritative CGPA to the disclosed threshold; missing values never mean below nine. */
public final class StudentCCAcademicSummary {
    private StudentCCAcademicSummary() {}

    public static Boolean threshold(Double cgpa) {
        return cgpa == null || !Double.isFinite(cgpa) || cgpa < 0 || cgpa > 10 ? null : cgpa >= 9.0;
    }

    static Boolean verifiedThreshold(String currentOwner, String proofOwner, Boolean threshold,
                                     long verifiedAt, long now) {
        if (currentOwner == null || currentOwner.isEmpty() || !currentOwner.equals(proofOwner)
                || verifiedAt <= 0 || verifiedAt > now) return null;
        return threshold;
    }
}
