package com.salmanmalvasi.studentcc.integrations.studentcc;

import org.junit.Test;
import static org.junit.Assert.*;

public class SharedPreferencesStudentCCStatisticsCacheTest {
    private static final StudentCCClassScope SCOPE = new StudentCCClassScope("CH", "CH2026", "CH2026CLASS1");

    @Test public void publicClassTallySurvivesCacheRecreationEvenWithoutCurrentStageMarks() {
        StudentCCSharingStoreTest.TestPreferences preferences = new StudentCCSharingStoreTest.TestPreferences();
        SharedPreferencesStudentCCStatisticsCache cache = new SharedPreferencesStudentCCStatisticsCache(preferences);
        StudentCCClassStatistics statistics = new StudentCCClassStatistics(SCOPE, 0, 1, null, null, null, 0, 3);
        cache.write("synthetic-cache", new StudentCCStatisticsCache.Entry(statistics, 1000));
        cache = new SharedPreferencesStudentCCStatisticsCache(preferences);
        StudentCCStatisticsCache.Entry restored = cache.read("synthetic-cache", SCOPE);
        assertNotNull(restored);
        assertFalse(restored.statistics.hasValues());
        assertEquals(Integer.valueOf(3), restored.statistics.cgpaAtLeastNineCount);
    }

    @Test public void legacyCacheKeepsMissingPublicTallyUnknownInsteadOfZero() {
        StudentCCSharingStoreTest.TestPreferences preferences = new StudentCCSharingStoreTest.TestPreferences();
        SharedPreferencesStudentCCStatisticsCache cache = new SharedPreferencesStudentCCStatisticsCache(preferences);
        StudentCCClassStatistics statistics = new StudentCCClassStatistics(SCOPE, 1, 1, 42.0, 0.0, 0.0, 1000);
        cache.write("synthetic-legacy-cache", new StudentCCStatisticsCache.Entry(statistics, 2000));
        StudentCCStatisticsCache.Entry restored = new SharedPreferencesStudentCCStatisticsCache(preferences)
                .read("synthetic-legacy-cache", SCOPE);
        assertNotNull(restored);
        assertEquals(42.0, restored.statistics.mean, 0.0);
        assertNull(restored.statistics.cgpaAtLeastNineCount);
    }
}
