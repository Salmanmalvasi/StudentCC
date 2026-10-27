package com.salmanmalvasi.studentcc.sync;

import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.AttendanceRecord;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Slot;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests verifying relational integrity and consistency validation of staged data.
 */
public class StagedSyncDataValidationTest {

    @Test
    public void testValidDataPassesConsistencyCheck() {
        StagedSyncData data = new StagedSyncData("2024-01");

        Course c1 = new Course();
        c1.id = 101;
        c1.code = "CSE1001";
        c1.title = "Problem Solving";
        data.courses.add(c1);

        Slot s1 = new Slot();
        s1.id = 1;
        s1.slot = "L1";
        s1.courseId = 101;
        data.slots.add(s1);

        Attendance a1 = new Attendance();
        a1.courseId = 101;
        a1.attended = 20;
        a1.total = 22;
        a1.percentage = 90;
        data.attendanceSummaries.add(a1);

        AttendanceRecord r1 = new AttendanceRecord();
        r1.courseId = 101;
        r1.dateString = "12-Aug-2024";
        r1.status = "Present";
        data.attendanceRecords.add(r1);

        String error = data.validateConsistency();
        Assert.assertNull("Valid data should pass consistency validation", error);
    }

    @Test
    public void testOrphanAttendanceRecordFailsValidation() {
        StagedSyncData data = new StagedSyncData("2024-01");

        Course c1 = new Course();
        c1.id = 101;
        c1.code = "CSE1001";
        data.courses.add(c1);

        AttendanceRecord orphan = new AttendanceRecord();
        orphan.courseId = 999; // Does not exist in courses!
        orphan.dateString = "12-Aug-2024";
        orphan.status = "Present";
        data.attendanceRecords.add(orphan);

        String error = data.validateConsistency();
        Assert.assertNotNull("Orphan attendance record must fail validation", error);
        Assert.assertTrue(error.contains("999"));
    }

    @Test
    public void testEmptyCoursesWithPopulatedAttendanceFailsValidation() {
        StagedSyncData data = new StagedSyncData("2024-01");

        Attendance a1 = new Attendance();
        a1.courseId = 101;
        data.attendanceSummaries.add(a1);

        String error = data.validateConsistency();
        Assert.assertNotNull("Attendance without courses must fail validation", error);
    }

    @Test
    public void testOrphanSlotFailsValidation() {
        StagedSyncData data = new StagedSyncData("2024-01");

        Course c1 = new Course();
        c1.id = 101;
        data.courses.add(c1);

        Slot s1 = new Slot();
        s1.id = 1;
        s1.slot = "A1";
        s1.courseId = 555; // Does not exist
        data.slots.add(s1);

        String error = data.validateConsistency();
        Assert.assertNotNull("Orphan slot must fail validation", error);
        Assert.assertTrue(error.contains("555"));
    }

    @Test
    public void testNullableMarkAllowedWhenCourseIdIsNull() {
        StagedSyncData data = new StagedSyncData("2024-01");

        Course c1 = new Course();
        c1.id = 101;
        data.courses.add(c1);

        com.salmanmalvasi.studentcc.models.Mark mNullCourse = new com.salmanmalvasi.studentcc.models.Mark();
        mNullCourse.id = 1;
        mNullCourse.courseId = null; // Nullable relationship allowed by schema
        mNullCourse.title = "Assessment with null course";
        data.marks.add(mNullCourse);

        String error = data.validateConsistency();
        Assert.assertNull("Marks with null courseId must be allowed (SET_NULL relationship)", error);
    }

    @Test
    public void testOrphanMarkFailsValidationWhenCourseIdNonNull() {
        StagedSyncData data = new StagedSyncData("2024-01");

        Course c1 = new Course();
        c1.id = 101;
        data.courses.add(c1);

        com.salmanmalvasi.studentcc.models.Mark mOrphan = new com.salmanmalvasi.studentcc.models.Mark();
        mOrphan.id = 2;
        mOrphan.courseId = 888; // Does not exist
        mOrphan.title = "CAT 1";
        data.marks.add(mOrphan);

        String error = data.validateConsistency();
        Assert.assertNotNull("Non-null orphan mark courseId must fail validation", error);
        Assert.assertTrue(error.contains("888"));
    }
}
