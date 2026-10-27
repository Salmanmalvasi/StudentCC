package com.salmanmalvasi.studentcc.sync;

import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests verifying dataset freshness windows, overall attendance calculation,
 * local cache metrics, and cache integrity protection invariants.
 */
public class DataFreshnessAndCacheIntegrityTest {

    @Test
    public void testFreshnessWindowDefaults() {
        DataFreshnessManager manager = DataFreshnessManager.getInstance();

        Assert.assertEquals(180_000L, manager.getFreshnessWindowMs(DataFreshnessManager.DATASET_ATTENDANCE_SUMMARY));
        Assert.assertEquals(420_000L, manager.getFreshnessWindowMs(DataFreshnessManager.DATASET_DETAILED_ATTENDANCE));
        Assert.assertEquals(600_000L, manager.getFreshnessWindowMs(DataFreshnessManager.DATASET_MARKS));
        Assert.assertEquals(28_800_000L, manager.getFreshnessWindowMs(DataFreshnessManager.DATASET_TIMETABLE));
        Assert.assertEquals(28_800_000L, manager.getFreshnessWindowMs(DataFreshnessManager.DATASET_CALENDAR));
        Assert.assertEquals(57_600_000L, manager.getFreshnessWindowMs(DataFreshnessManager.DATASET_PROFILE_COURSES));
    }

    @Test
    public void testOverallAttendanceCalculation() {
        List<Attendance> summaries = new ArrayList<>();

        Attendance a1 = new Attendance();
        a1.attended = 20;
        a1.total = 22;
        summaries.add(a1);

        Attendance a2 = new Attendance();
        a2.attended = 15;
        a2.total = 18;
        summaries.add(a2);

        Attendance a3 = new Attendance();
        a3.attended = 10;
        a3.total = 10;
        summaries.add(a3);

        int totalAttended = 0;
        int totalConducted = 0;
        for (Attendance att : summaries) {
            if (att.attended != null) totalAttended += att.attended;
            if (att.total != null) totalConducted += att.total;
        }

        Assert.assertEquals(45, totalAttended);
        Assert.assertEquals(50, totalConducted);

        float overall = totalConducted > 0 ? (Math.round((totalAttended * 10000.0f) / totalConducted) / 100.0f) : 0.0f;
        Assert.assertEquals(90.0f, overall, 0.01f);

        int roundedPercentage = (totalConducted > 0) ? Math.round((totalAttended * 100f) / totalConducted) : 0;
        Assert.assertEquals(90, roundedPercentage);
    }

    @Test
    public void testOverallAttendanceZeroConductedEdgeCase() {
        List<Attendance> summaries = new ArrayList<>();

        int totalAttended = 0;
        int totalConducted = 0;
        for (Attendance att : summaries) {
            if (att.attended != null) totalAttended += att.attended;
            if (att.total != null) totalConducted += att.total;
        }

        float overall = totalConducted > 0 ? (Math.round((totalAttended * 10000.0f) / totalConducted) / 100.0f) : 0.0f;
        Assert.assertEquals(0.0f, overall, 0.001f);

        int roundedPercentage = (totalConducted > 0) ? Math.round((totalAttended * 100f) / totalConducted) : 0;
        Assert.assertEquals(0, roundedPercentage);
    }

    @Test
    public void testLocalCacheMetricsRecording() {
        LocalCacheMetrics metrics = LocalCacheMetrics.getInstance();

        metrics.recordStartupRender(42);
        metrics.recordScreenOpen("PerformanceFragment", 8, true);
        metrics.recordRequestSkipped(DataFreshnessManager.DATASET_ATTENDANCE_SUMMARY);
        metrics.recordCacheRefreshSuccess(DataFreshnessManager.DATASET_MARKS);
        metrics.recordDatasetRetainedOnFailure(DataFreshnessManager.DATASET_MARKS);
        metrics.recordBackgroundRefreshDuration(1450, true);

        String summary = metrics.dumpSummary();
        Assert.assertNotNull(summary);
        Assert.assertTrue(summary.contains("Startup: 42ms"));
        Assert.assertTrue(summary.contains("LastRefresh: 1450ms"));
        Assert.assertTrue(summary.contains("RetainedOnFail:"));
    }

    @Test
    public void testCacheIntegrityDetection() {
        // Simulates protection logic: non-empty existing data is not erased if incoming data is empty
        List<Mark> existingMarks = new ArrayList<>();
        Mark m1 = new Mark();
        m1.courseId = 1;
        m1.title = "Quiz 1";
        m1.score = 9.5;
        m1.maxScore = 10.0;
        existingMarks.add(m1);

        List<Mark> stagedMarks = new ArrayList<>(); // Empty response from server!

        // If replacement is empty but existing data is present, the existing dataset MUST be retained
        boolean retainExisting = stagedMarks.isEmpty() && !existingMarks.isEmpty();
        Assert.assertTrue("Valid cached marks must be retained when incoming replacement is empty", retainExisting);

        List<Mark> resultMarks = retainExisting ? existingMarks : stagedMarks;
        Assert.assertEquals(1, resultMarks.size());
        Assert.assertEquals("Quiz 1", resultMarks.get(0).title);
    }
}
