package com.salmanmalvasi.studentcc.integrations.studentcc;

import org.junit.Test;
import static org.junit.Assert.*;

public class StudentCCSharingProofTest {
    @Test public void consentWithoutSemesterOrSyncProofCannotAuthorizeCachedTotals() {
        assertFalse(StudentCCSharingManager.hasVerifiedMarksSnapshot("owner-a", "", "owner-a", ""));
        assertFalse(StudentCCSharingManager.hasVerifiedMarksSnapshot("owner-a", "CH2026", "", ""));
        assertFalse(StudentCCSharingManager.hasVerifiedMarksSnapshot("owner-a", "CH2026", "owner-a", ""));
        assertFalse(StudentCCSharingManager.hasVerifiedMarksSnapshot("", "CH2026", "", "CH2026"));
    }

    @Test public void accountOrSemesterSwitchRequiresProofForThatExactDataset() {
        assertFalse(StudentCCSharingManager.hasVerifiedMarksSnapshot("owner-b", "CH2026", "owner-a", "CH2026"));
        assertFalse(StudentCCSharingManager.hasVerifiedMarksSnapshot("owner-a", "CH2026", "owner-a", "CH2025"));
        assertTrue(StudentCCSharingManager.hasVerifiedMarksSnapshot("owner-a", "CH2026", "owner-a", "CH2026"));
    }

    @Test public void academicProofCanBeVerifiedBeforeCourseSemesterIsSelected() {
        assertFalse(StudentCCSharingManager.hasVerifiedMarksSnapshot("owner-a", "", "", ""));
        assertEquals(Boolean.TRUE, StudentCCAcademicSummary.verifiedThreshold("owner-a", "owner-a", true, 100L, 200L));
        assertNull(StudentCCAcademicSummary.verifiedThreshold("owner-a", "", null, 0L, 200L));
    }
}
