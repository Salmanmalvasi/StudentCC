package com.salmanmalvasi.studentcc.integrations.studentcc;

import org.junit.Test;

import static org.junit.Assert.*;

public class StudentCCAcademicSummaryTest {
    @Test public void validCgpaThresholdIncludesExactlyNineAndKeepsBelowNineSeparate() {
        assertEquals(Boolean.FALSE, StudentCCAcademicSummary.threshold(0.0));
        assertEquals(Boolean.FALSE, StudentCCAcademicSummary.threshold(8.999));
        assertEquals(Boolean.TRUE, StudentCCAcademicSummary.threshold(9.0));
        assertEquals(Boolean.TRUE, StudentCCAcademicSummary.threshold(10.0));
    }

    @Test public void missingInvalidAndOutOfRangeValuesAreUnknownRatherThanBelowNine() {
        for (Double value : new Double[] {null, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, -0.01, 10.01}) assertNull(StudentCCAcademicSummary.threshold(value));
    }

    @Test public void proofForAnEarlierUserCannotAuthorizeCurrentUsersAcademicSummary() {
        assertNull(StudentCCAcademicSummary.verifiedThreshold("user-b", "user-a", true, 100L, 200L));
        assertNull(StudentCCAcademicSummary.verifiedThreshold("", "", false, 100L, 200L));
        assertEquals(Boolean.TRUE, StudentCCAcademicSummary.verifiedThreshold("user-a", "user-a", true, 100L, 200L));
    }

    @Test public void unverifiedMissingOrFutureProofNeverInventsAResult() {
        assertNull(StudentCCAcademicSummary.verifiedThreshold("user-a", "user-a", true, 0L, 200L));
        assertNull(StudentCCAcademicSummary.verifiedThreshold("user-a", "user-a", false, 300L, 200L));
        assertNull(StudentCCAcademicSummary.verifiedThreshold("user-a", "user-a", null, 100L, 200L));
    }
}
