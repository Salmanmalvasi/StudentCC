package tk.therealsuji.vtopchennai.database;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.interfaces.AttendanceDao;
import tk.therealsuji.vtopchennai.interfaces.CoursesDao;
import tk.therealsuji.vtopchennai.models.Attendance;
import tk.therealsuji.vtopchennai.models.Course;

import static org.junit.Assert.*;

/**
 * Instrumented tests for AttendanceDao.
 * These tests run on an Android device or emulator and use an in-memory database.
 */
@RunWith(AndroidJUnit4.class)
public class AttendanceDaoTest {

    private AppDatabase database;
    private AttendanceDao attendanceDao;
    private CoursesDao coursesDao;

    @Before
    public void setUp() {
        // Create an in-memory database for testing
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries() // For testing purposes only
                .build();

        attendanceDao = database.attendanceDao();
        coursesDao = database.coursesDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void testInsertAndRetrieveAttendance() throws Exception {
        // First, create a course (required due to foreign key constraint)
        Course course = createTestCourse(1, "CSE101", "Data Structures");
        coursesDao.insert(Arrays.asList(course)).blockingAwait();

        // Create attendance record
        Attendance attendance = new Attendance();
        attendance.courseId = 1;
        attendance.attended = 8;
        attendance.total = 10;
        attendance.percentage = 80;

        // Insert attendance
        attendanceDao.insert(Arrays.asList(attendance)).blockingAwait();

        // Retrieve and verify
        Attendance retrieved = attendanceDao.getAttendanceByCourseId(1).blockingGet();
        assertNotNull(retrieved);
        assertEquals(Integer.valueOf(1), retrieved.courseId);
        assertEquals(Integer.valueOf(8), retrieved.attended);
        assertEquals(Integer.valueOf(10), retrieved.total);
        assertEquals(Integer.valueOf(80), retrieved.percentage);
    }

    @Test
    public void testInsertMultipleAttendanceRecords() throws Exception {
        // Create courses
        Course course1 = createTestCourse(1, "CSE101", "Data Structures");
        Course course2 = createTestCourse(2, "MTH101", "Calculus");
        Course course3 = createTestCourse(3, "PHY101", "Physics");
        coursesDao.insert(Arrays.asList(course1, course2, course3)).blockingAwait();

        // Create attendance records
        Attendance att1 = createAttendance(1, 8, 10, 80);
        Attendance att2 = createAttendance(2, 15, 20, 75);
        Attendance att3 = createAttendance(3, 18, 20, 90);

        // Insert all
        attendanceDao.insert(Arrays.asList(att1, att2, att3)).blockingAwait();

        // Verify each
        assertEquals(Integer.valueOf(80), attendanceDao.getAttendanceByCourseId(1).blockingGet().percentage);
        assertEquals(Integer.valueOf(75), attendanceDao.getAttendanceByCourseId(2).blockingGet().percentage);
        assertEquals(Integer.valueOf(90), attendanceDao.getAttendanceByCourseId(3).blockingGet().percentage);
    }

    @Test
    public void testUpdateTotals() throws Exception {
        // Setup
        Course course = createTestCourse(1, "CSE101", "Data Structures");
        coursesDao.insert(Arrays.asList(course)).blockingAwait();

        Attendance attendance = createAttendance(1, 8, 10, 80);
        attendanceDao.insert(Arrays.asList(attendance)).blockingAwait();

        // Update attendance (student missed 2 classes)
        int newTotal = 12;
        int newPercentage = 66; // 8/12 = 66.67% -> 66% with floor
        attendanceDao.updateTotals(1, newTotal, newPercentage).blockingAwait();

        // Verify update
        Attendance updated = attendanceDao.getAttendanceByCourseId(1).blockingGet();
        assertEquals(Integer.valueOf(8), updated.attended); // Attended stays same
        assertEquals(Integer.valueOf(12), updated.total);
        assertEquals(Integer.valueOf(66), updated.percentage);
    }

    @Test
    public void testUpdateTotals_MultipleUpdates() throws Exception {
        // Setup
        Course course = createTestCourse(1, "CSE101", "Data Structures");
        coursesDao.insert(Arrays.asList(course)).blockingAwait();

        Attendance attendance = createAttendance(1, 10, 10, 100);
        attendanceDao.insert(Arrays.asList(attendance)).blockingAwait();

        // Simulate multiple updates (attendance calculator scenario)
        // After missing 1 class: 10/11 = 90.9% -> 90%
        attendanceDao.updateTotals(1, 11, 90).blockingAwait();
        assertEquals(Integer.valueOf(90), attendanceDao.getAttendanceByCourseId(1).blockingGet().percentage);

        // After missing 2 more classes: 10/13 = 76.9% -> 76%
        attendanceDao.updateTotals(1, 13, 76).blockingAwait();
        assertEquals(Integer.valueOf(76), attendanceDao.getAttendanceByCourseId(1).blockingGet().percentage);

        // After missing 7 more classes: 10/20 = 50%
        attendanceDao.updateTotals(1, 20, 50).blockingAwait();
        assertEquals(Integer.valueOf(50), attendanceDao.getAttendanceByCourseId(1).blockingGet().percentage);
    }

    @Test
    public void testDeleteAll() throws Exception {
        // Setup - add multiple attendance records
        Course course1 = createTestCourse(1, "CSE101", "Data Structures");
        Course course2 = createTestCourse(2, "MTH101", "Calculus");
        coursesDao.insert(Arrays.asList(course1, course2)).blockingAwait();

        Attendance att1 = createAttendance(1, 8, 10, 80);
        Attendance att2 = createAttendance(2, 15, 20, 75);
        attendanceDao.insert(Arrays.asList(att1, att2)).blockingAwait();

        // Verify inserted
        assertNotNull(attendanceDao.getAttendanceByCourseId(1).blockingGet());
        assertNotNull(attendanceDao.getAttendanceByCourseId(2).blockingGet());

        // Delete all
        attendanceDao.delete().blockingAwait();

        // Verify deleted - should throw NoSuchElementException
        try {
            attendanceDao.getAttendanceByCourseId(1).blockingGet();
            fail("Expected NoSuchElementException");
        } catch (Exception e) {
            // Expected - no records found
            assertTrue(e.getMessage().contains("onError") || e instanceof java.util.NoSuchElementException);
        }
    }

    @Test
    public void testForeignKeyConstraint_CascadeDelete() throws Exception {
        // Setup
        Course course = createTestCourse(1, "CSE101", "Data Structures");
        coursesDao.insert(Arrays.asList(course)).blockingAwait();

        Attendance attendance = createAttendance(1, 8, 10, 80);
        attendanceDao.insert(Arrays.asList(attendance)).blockingAwait();

        // Verify attendance exists
        assertNotNull(attendanceDao.getAttendanceByCourseId(1).blockingGet());

        // Delete the parent course
        coursesDao.delete().blockingAwait();

        // Verify attendance is also deleted (CASCADE)
        try {
            attendanceDao.getAttendanceByCourseId(1).blockingGet();
            fail("Expected NoSuchElementException due to cascade delete");
        } catch (Exception e) {
            // Expected - attendance should be deleted when course is deleted
            assertTrue(e.getMessage().contains("onError") || e instanceof java.util.NoSuchElementException);
        }
    }

    @Test
    public void testUpdateTotals_NonExistentCourse() throws Exception {
        // Try to update attendance for a course that doesn't exist
        // This should complete without error, but not update anything
        attendanceDao.updateTotals(999, 20, 75).blockingAwait();

        // Verify nothing was created
        try {
            attendanceDao.getAttendanceByCourseId(999).blockingGet();
            fail("Expected NoSuchElementException");
        } catch (Exception e) {
            // Expected - no record should exist
            assertTrue(e.getMessage().contains("onError") || e instanceof java.util.NoSuchElementException);
        }
    }

    @Test
    public void testAttendanceCalculatorIntegration() throws Exception {
        // Test a realistic attendance calculator scenario
        Course course = createTestCourse(1, "CSE101", "Data Structures");
        coursesDao.insert(Arrays.asList(course)).blockingAwait();

        // Initial attendance: 18/20 = 90%
        Attendance attendance = createAttendance(1, 18, 20, 90);
        attendanceDao.insert(Arrays.asList(attendance)).blockingAwait();

        // Simulate attendance calculator: student will miss 10 classes
        // New total: 20 + 10 = 30
        // Percentage: 18/30 = 60%
        int newTotal = 30;
        int newPercentage = (int) Math.floor((18 * 100.0) / 30); // 60%

        attendanceDao.updateTotals(1, newTotal, newPercentage).blockingAwait();

        // Verify calculation
        Attendance updated = attendanceDao.getAttendanceByCourseId(1).blockingGet();
        assertEquals(Integer.valueOf(18), updated.attended);
        assertEquals(Integer.valueOf(30), updated.total);
        assertEquals(Integer.valueOf(60), updated.percentage);
    }

    @Test
    public void testZeroAttendance() throws Exception {
        // Edge case: Student has 0 attendance
        Course course = createTestCourse(1, "CSE101", "Data Structures");
        coursesDao.insert(Arrays.asList(course)).blockingAwait();

        Attendance attendance = createAttendance(1, 0, 10, 0);
        attendanceDao.insert(Arrays.asList(attendance)).blockingAwait();

        Attendance retrieved = attendanceDao.getAttendanceByCourseId(1).blockingGet();
        assertEquals(Integer.valueOf(0), retrieved.attended);
        assertEquals(Integer.valueOf(0), retrieved.percentage);
    }

    @Test
    public void testPerfectAttendance() throws Exception {
        // Edge case: Student has 100% attendance
        Course course = createTestCourse(1, "CSE101", "Data Structures");
        coursesDao.insert(Arrays.asList(course)).blockingAwait();

        Attendance attendance = createAttendance(1, 20, 20, 100);
        attendanceDao.insert(Arrays.asList(attendance)).blockingAwait();

        Attendance retrieved = attendanceDao.getAttendanceByCourseId(1).blockingGet();
        assertEquals(Integer.valueOf(20), retrieved.attended);
        assertEquals(Integer.valueOf(20), retrieved.total);
        assertEquals(Integer.valueOf(100), retrieved.percentage);
    }

    // ========== Helper Methods ==========

    private Course createTestCourse(int id, String code, String name) {
        Course course = new Course();
        course.id = id;
        course.courseCode = code;
        course.courseName = name;
        course.courseType = "Theory";
        course.slot = "A1+TA1";
        course.courseMode = "Online";
        course.credits = 4;
        return course;
    }

    private Attendance createAttendance(int courseId, int attended, int total, int percentage) {
        Attendance attendance = new Attendance();
        attendance.courseId = courseId;
        attendance.attended = attended;
        attendance.total = total;
        attendance.percentage = percentage;
        return attendance;
    }
}
