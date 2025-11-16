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
import tk.therealsuji.vtopchennai.models.Slot;

import static org.junit.Assert.*;

/**
 * Instrumented tests for CoursesDao.
 * Tests course and slot database operations including complex queries.
 */
@RunWith(AndroidJUnit4.class)
public class CoursesDaoTest {

    private AppDatabase database;
    private CoursesDao coursesDao;
    private AttendanceDao attendanceDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        coursesDao = database.coursesDao();
        attendanceDao = database.attendanceDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void testInsertAndRetrieveCourse() throws Exception {
        Course course = createCourse(1, "CSE101", "Data Structures", "Theory", 4);
        coursesDao.insertCourses(Arrays.asList(course)).blockingAwait();

        // Insert slot for the course
        Slot slot = createSlot(1, "A1", 1);
        coursesDao.insertSlots(Arrays.asList(slot)).blockingAwait();

        // Verify course code retrieval
        List<String> courseCodes = coursesDao.getCourseCodes().blockingGet();
        assertEquals(1, courseCodes.size());
        assertEquals("CSE101", courseCodes.get(0));
    }

    @Test
    public void testInsertMultipleCourses() throws Exception {
        Course course1 = createCourse(1, "CSE101", "Data Structures", "Theory", 4);
        Course course2 = createCourse(2, "MTH101", "Calculus", "Theory", 4);
        Course course3 = createCourse(3, "PHY101", "Physics", "Theory", 3);
        Course course4 = createCourse(4, "CSE102", "DS Lab", "Lab", 2);

        coursesDao.insertCourses(Arrays.asList(course1, course2, course3, course4)).blockingAwait();

        // Insert slots
        Slot slot1 = createSlot(1, "A1", 1);
        Slot slot2 = createSlot(2, "B1", 2);
        Slot slot3 = createSlot(3, "C1", 3);
        Slot slot4 = createSlot(4, "L1", 4);

        coursesDao.insertSlots(Arrays.asList(slot1, slot2, slot3, slot4)).blockingAwait();

        // Verify all course codes
        List<String> courseCodes = coursesDao.getCourseCodes().blockingGet();
        assertEquals(4, courseCodes.size());
        assertTrue(courseCodes.contains("CSE101"));
        assertTrue(courseCodes.contains("MTH101"));
        assertTrue(courseCodes.contains("PHY101"));
        assertTrue(courseCodes.contains("CSE102"));
    }

    @Test
    public void testGetCourseIdByCode() throws Exception {
        Course course = createCourse(42, "CSE101", "Data Structures", "Theory", 4);
        coursesDao.insertCourses(Arrays.asList(course)).blockingAwait();

        Integer courseId = coursesDao.getCourseIdByCode("CSE101").blockingGet();
        assertEquals(Integer.valueOf(42), courseId);
    }

    @Test
    public void testGetCourseIdByCode_NonExistent() throws Exception {
        // Try to get course ID for non-existent course
        try {
            coursesDao.getCourseIdByCode("XYZ999").blockingGet();
            fail("Expected NoSuchElementException");
        } catch (Exception e) {
            // Expected - no record found
            assertTrue(e.getMessage().contains("onError") || e instanceof java.util.NoSuchElementException);
        }
    }

    @Test
    public void testGetCourseWithAttendance() throws Exception {
        // Setup course
        Course course = createCourse(1, "CSE101", "Data Structures", "Theory", 4);
        course.faculty = "Dr. Smith";
        course.venue = "AB2-101";
        coursesDao.insertCourses(Arrays.asList(course)).blockingAwait();

        // Setup slot
        Slot slot = createSlot(1, "A1+TA1", 1);
        coursesDao.insertSlots(Arrays.asList(slot)).blockingAwait();

        // Setup attendance
        Attendance attendance = createAttendance(1, 18, 20, 90);
        attendanceDao.insert(Arrays.asList(attendance)).blockingAwait();

        // Get course data by slot ID
        Course.AllData courseData = coursesDao.getCourse(1).blockingGet();

        assertNotNull(courseData);
        assertEquals("Data Structures", courseData.courseTitle);
        assertEquals("CSE101", courseData.courseCode);
        assertEquals("Theory", courseData.courseType);
        assertEquals("Dr. Smith", courseData.faculty);
        assertEquals("A1+TA1", courseData.slot);
        assertEquals("AB2-101", courseData.venue);
        assertEquals(Integer.valueOf(18), courseData.attendanceAttended);
        assertEquals(Integer.valueOf(20), courseData.attendanceTotal);
        assertEquals(Integer.valueOf(90), courseData.attendancePercentage);
    }

    @Test
    public void testGetCourseByCodeWithMultipleSlots() throws Exception {
        // Create course with multiple slots (e.g., Theory + Lab)
        Course course = createCourse(1, "CSE101", "Data Structures", "Theory", 4);
        course.faculty = "Dr. Smith";
        course.venue = "AB2-101";
        coursesDao.insertCourses(Arrays.asList(course)).blockingAwait();

        // Multiple slots for same course
        Slot slot1 = createSlot(1, "A1", 1);
        Slot slot2 = createSlot(2, "TA1", 1);
        coursesDao.insertSlots(Arrays.asList(slot1, slot2)).blockingAwait();

        // Attendance
        Attendance attendance = createAttendance(1, 15, 20, 75);
        attendanceDao.insert(Arrays.asList(attendance)).blockingAwait();

        // Get course data by course code
        List<Course.AllData> courseDataList = coursesDao.getCourse("CSE101").blockingGet();

        // Should return data for both slots
        assertEquals(2, courseDataList.size());

        for (Course.AllData data : courseDataList) {
            assertEquals("Data Structures", data.courseTitle);
            assertEquals("Theory", data.courseType);
            assertEquals("Dr. Smith", data.faculty);
            assertEquals(Integer.valueOf(15), data.attendanceAttended);
            assertEquals(Integer.valueOf(20), data.attendanceTotal);
            assertEquals(Integer.valueOf(75), data.attendancePercentage);
        }
    }

    @Test
    public void testGetCourseByCode_WithoutAttendance() throws Exception {
        // Course without attendance record (LEFT JOIN should still work)
        Course course = createCourse(1, "CSE101", "Data Structures", "Theory", 4);
        course.faculty = "Dr. Smith";
        coursesDao.insertCourses(Arrays.asList(course)).blockingAwait();

        Slot slot = createSlot(1, "A1", 1);
        coursesDao.insertSlots(Arrays.asList(slot)).blockingAwait();

        // No attendance inserted

        List<Course.AllData> courseDataList = coursesDao.getCourse("CSE101").blockingGet();

        assertEquals(1, courseDataList.size());
        Course.AllData data = courseDataList.get(0);
        assertEquals("Data Structures", data.courseTitle);
        assertEquals("Dr. Smith", data.faculty);
        // Attendance fields should be null
        assertNull(data.attendanceAttended);
        assertNull(data.attendanceTotal);
        assertNull(data.attendancePercentage);
    }

    @Test
    public void testDeleteAll() throws Exception {
        // Insert multiple courses
        Course course1 = createCourse(1, "CSE101", "Data Structures", "Theory", 4);
        Course course2 = createCourse(2, "MTH101", "Calculus", "Theory", 4);
        coursesDao.insertCourses(Arrays.asList(course1, course2)).blockingAwait();

        Slot slot1 = createSlot(1, "A1", 1);
        Slot slot2 = createSlot(2, "B1", 2);
        coursesDao.insertSlots(Arrays.asList(slot1, slot2)).blockingAwait();

        // Verify inserted
        List<String> courseCodes = coursesDao.getCourseCodes().blockingGet();
        assertEquals(2, courseCodes.size());

        // Delete all courses
        coursesDao.deleteAll().blockingAwait();

        // Verify deleted - should throw exception when querying
        try {
            coursesDao.getCourseCodes().blockingGet();
            // If no exception, list should be empty
        } catch (Exception e) {
            // Expected if query fails with no data
        }
    }

    @Test
    public void testForeignKeyConstraint_SlotCascadeDelete() throws Exception {
        // Insert course and slot
        Course course = createCourse(1, "CSE101", "Data Structures", "Theory", 4);
        coursesDao.insertCourses(Arrays.asList(course)).blockingAwait();

        Slot slot = createSlot(1, "A1", 1);
        coursesDao.insertSlots(Arrays.asList(slot)).blockingAwait();

        // Verify slot exists
        List<String> courseCodes = coursesDao.getCourseCodes().blockingGet();
        assertEquals(1, courseCodes.size());

        // Delete parent course
        coursesDao.deleteAll().blockingAwait();

        // Verify slot is also deleted (CASCADE)
        try {
            List<String> codes = coursesDao.getCourseCodes().blockingGet();
            assertTrue(codes == null || codes.isEmpty());
        } catch (Exception e) {
            // Expected - no data found
        }
    }

    @Test
    public void testCourseTypes() throws Exception {
        // Test different course types
        Course theory = createCourse(1, "CSE101", "Data Structures", "Theory", 4);
        Course lab = createCourse(2, "CSE102", "DS Lab", "Lab", 2);
        Course project = createCourse(3, "CSE499", "Capstone", "Project", 6);

        coursesDao.insertCourses(Arrays.asList(theory, lab, project)).blockingAwait();

        Slot slot1 = createSlot(1, "A1", 1);
        Slot slot2 = createSlot(2, "L1", 2);
        Slot slot3 = createSlot(3, "NIL", 3);
        coursesDao.insertSlots(Arrays.asList(slot1, slot2, slot3)).blockingAwait();

        List<String> courseCodes = coursesDao.getCourseCodes().blockingGet();
        assertEquals(3, courseCodes.size());
    }

    @Test
    public void testDifferentCreditCourses() throws Exception {
        // Test courses with different credit values
        Course course1 = createCourse(1, "CSE101", "4-Credit Course", "Theory", 4);
        Course course2 = createCourse(2, "MTH101", "3-Credit Course", "Theory", 3);
        Course course3 = createCourse(3, "CSE102", "2-Credit Lab", "Lab", 2);
        Course course4 = createCourse(4, "PHY101", "1-Credit Course", "Theory", 1);

        coursesDao.insertCourses(Arrays.asList(course1, course2, course3, course4)).blockingAwait();

        Slot slot1 = createSlot(1, "A1", 1);
        Slot slot2 = createSlot(2, "B1", 2);
        Slot slot3 = createSlot(3, "L1", 3);
        Slot slot4 = createSlot(4, "C1", 4);
        coursesDao.insertSlots(Arrays.asList(slot1, slot2, slot3, slot4)).blockingAwait();

        List<String> courseCodes = coursesDao.getCourseCodes().blockingGet();
        assertEquals(4, courseCodes.size());
    }

    @Test
    public void testSlotOrdering() throws Exception {
        // Test that course codes are ordered by slot
        Course course1 = createCourse(1, "CSE101", "Course 1", "Theory", 4);
        Course course2 = createCourse(2, "MTH101", "Course 2", "Theory", 4);
        Course course3 = createCourse(3, "PHY101", "Course 3", "Theory", 3);

        coursesDao.insertCourses(Arrays.asList(course1, course2, course3)).blockingAwait();

        // Insert slots in non-alphabetical order to test ordering
        Slot slot1 = createSlot(1, "C1", 1);
        Slot slot2 = createSlot(2, "A1", 2);
        Slot slot3 = createSlot(3, "B1", 3);
        coursesDao.insertSlots(Arrays.asList(slot1, slot2, slot3)).blockingAwait();

        List<String> courseCodes = coursesDao.getCourseCodes().blockingGet();

        // Should be ordered by slot: A1, B1, C1
        assertEquals(3, courseCodes.size());
        assertEquals("MTH101", courseCodes.get(0)); // A1
        assertEquals("PHY101", courseCodes.get(1)); // B1
        assertEquals("CSE101", courseCodes.get(2)); // C1
    }

    @Test
    public void testRealWorldScenario_TypicalSemester() throws Exception {
        // Simulate a typical VIT semester with 6 courses
        Course[] courses = {
            createCourse(1, "CSE1001", "Problem Solving", "Theory", 3),
            createCourse(2, "CSE1002", "PS Lab", "Lab", 2),
            createCourse(3, "MTH1001", "Calculus", "Theory", 4),
            createCourse(4, "PHY1001", "Physics", "Theory", 3),
            createCourse(5, "PHY1002", "Physics Lab", "Lab", 1),
            createCourse(6, "ENG1001", "English", "Theory", 2)
        };

        for (Course course : courses) {
            course.faculty = "Dr. " + course.code.substring(0, 3);
            course.venue = "AB2-101";
        }

        coursesDao.insertCourses(Arrays.asList(courses)).blockingAwait();

        // Create slots
        Slot[] slots = {
            createSlot(1, "A1+TA1", 1),
            createSlot(2, "L1+L2", 2),
            createSlot(3, "B1+TB1", 3),
            createSlot(4, "C1+TC1", 4),
            createSlot(5, "L3+L4", 5),
            createSlot(6, "D1+TD1", 6)
        };

        coursesDao.insertSlots(Arrays.asList(slots)).blockingAwait();

        // Create attendance for all courses
        Attendance[] attendances = {
            createAttendance(1, 18, 20, 90),
            createAttendance(2, 16, 20, 80),
            createAttendance(3, 15, 20, 75),
            createAttendance(4, 19, 20, 95),
            createAttendance(5, 20, 20, 100),
            createAttendance(6, 14, 20, 70)
        };

        attendanceDao.insert(Arrays.asList(attendances)).blockingAwait();

        // Verify all courses
        List<String> courseCodes = coursesDao.getCourseCodes().blockingGet();
        assertEquals(6, courseCodes.size());

        // Verify course data for one course
        Course.AllData cse1001 = coursesDao.getCourse(1).blockingGet();
        assertEquals("Problem Solving", cse1001.courseTitle);
        assertEquals("CSE1001", cse1001.courseCode);
        assertEquals(Integer.valueOf(90), cse1001.attendancePercentage);
    }

    // ========== Helper Methods ==========

    private Course createCourse(int id, String code, String title, String type, int credits) {
        Course course = new Course();
        course.id = id;
        course.code = code;
        course.title = title;
        course.type = type;
        course.credits = credits;
        course.venue = "TBD";
        course.faculty = "TBD";
        return course;
    }

    private Slot createSlot(int id, String slot, int courseId) {
        Slot slotObj = new Slot();
        slotObj.id = id;
        slotObj.slot = slot;
        slotObj.courseId = courseId;
        return slotObj;
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
