package com.salmanmalvasi.studentcc.helpers;

import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class MarksReadStateTest {
    @Test public void changedJsonScoreReappearsUnreadDespiteTheSameSignature() {
        Mark old = mark(1, "CAT 1", 40.0); old.isRead = true; old.signature = 123;
        Mark changed = mark(1, "CAT 1", 45.0); changed.signature = 123;
        preserve(changed, old);
        assertFalse(changed.isRead);
    }

    @Test public void identicalQuickSyncDoesNotCreateAFalseUpdate() {
        Mark old = mark(1, "CAT 1", 40.0); old.isRead = true;
        Mark identical = mark(1, "CAT 1", 40.0);
        preserve(identical, old);
        assertTrue(identical.isRead);
        Mark next = mark(1, "CAT 1", 40.0);
        preserve(next, identical);
        assertTrue(next.isRead);
    }

    @Test public void readingAnUpdatedAssessmentAcknowledgesOnlyThatVersion() {
        Mark old = mark(1, "CAT 1", 40.0); old.isRead = true;
        Mark updated = mark(1, "CAT 1", 45.0);
        preserve(updated, old); assertFalse(updated.isRead);
        updated.isRead = true; // Represents the student opening the displayed updated assessment.
        Mark same = mark(1, "CAT 1", 45.0); preserve(same, updated); assertTrue(same.isRead);
        Mark later = mark(1, "CAT 1", 46.0); preserve(later, same); assertFalse(later.isRead);
    }

    @Test public void switchingParserSignaturesAndHarmlessFormattingDoesNotCreateAnUpdate() {
        Mark old = mark(1, " CAT 1 ", 40.0); old.isRead = true; old.signature = 123;
        old.status = "Present";
        Mark identical = mark(1, "CAT   1", 40.0); identical.signature = 456;
        identical.status = " present ";
        preserve(identical, old);
        assertTrue(identical.isRead);
    }

    @Test public void newlyReleasedZeroScoreIsAnUpdateFromUnreleasedMarks() {
        Mark old = mark(1, "FAT", null); old.weightage = null; old.isRead = true;
        Mark zero = mark(1, "FAT", 0.0); zero.weightage = 0.0;
        preserve(zero, old);
        assertFalse(zero.isRead);
    }

    @Test public void weightedScoreMaximumAndStatusCorrectionsAreUpdates() {
        Mark old = mark(1, "CAT 1", 40.0); old.isRead = true;
        Mark weighted = mark(1, "CAT 1", 40.0); weighted.weightage = 13.0; preserve(weighted, old); assertFalse(weighted.isRead);
        Mark maximum = mark(1, "CAT 1", 40.0); maximum.maxScore = 60.0; preserve(maximum, old); assertFalse(maximum.isRead);
        Mark weightedMaximum = mark(1, "CAT 1", 40.0); weightedMaximum.maxWeightage = 20.0; preserve(weightedMaximum, old); assertFalse(weightedMaximum.isRead);
        Mark status = mark(1, "CAT 1", 40.0); status.status = "Absent"; preserve(status, old); assertFalse(status.isRead);
    }

    @Test public void ClassAverageChangesDoNotFlagTheStudentsOwnMarksAsUpdated() {
        Mark old = mark(1, "CAT 1", 40.0); old.isRead = true; old.average = 30.0;
        Mark refreshed = mark(1, "CAT 1", 40.0); refreshed.average = 33.0;
        preserve(refreshed, old);
        assertTrue(refreshed.isRead);
    }

    @Test public void existingUnreadAndNewAssessmentsStayUnread() {
        Mark old = mark(1, "CAT 1", 40.0);
        Mark same = mark(1, "CAT 1", 40.0); preserve(same, old); assertFalse(same.isRead);
        old.isRead = true;
        Mark added = mark(1, "CAT 2", 40.0); preserve(added, old); assertFalse(added.isRead);
    }

    @Test public void upgradedCourseTypeMetadataKeepsReadStateForTheSameRealClass() {
        Course oldCourse = course(1, "theory", "CH2026", "CH_CLASS_1");
        Course newCourse = course(7, "theory only", "CH2026", "CH_CLASS_1");
        Mark old = mark(1, "CAT 1", 40.0); old.isRead = true;
        Mark incoming = mark(7, "CAT 1", 40.0);
        MarksReadState.preserve(Collections.singletonList(incoming), Collections.singletonList(old),
                Collections.singletonList(newCourse), Collections.singletonList(oldCourse));
        assertTrue(incoming.isRead);
    }

    @Test public void DifferentClassSemesterAndComponentNeverInheritReadMarks() {
        Course oldCourse = course(1, "theory", "CH2026", "CH_CLASS_1");
        Mark old = mark(1, "CAT 1", 40.0); old.isRead = true;
        for (Course replacement : Arrays.asList(course(1, "lab", "CH2026", "CH_CLASS_1"),
                course(1, "theory", "CH2027", "CH_CLASS_1"), course(1, "theory", "CH2026", "CH_CLASS_2"))) {
            Mark incoming = mark(1, "CAT 1", 40.0);
            MarksReadState.preserve(Collections.singletonList(incoming), Collections.singletonList(old),
                    Collections.singletonList(replacement), Collections.singletonList(oldCourse));
            assertFalse(incoming.isRead);
        }
    }

    @Test public void ambiguousDuplicateAssessmentsDoNotLoseTheirUpdateIndicator() {
        Mark old = mark(1, "CAT 1", 40.0); old.isRead = true;
        Mark duplicate = mark(1, "CAT 1", 42.0);
        Mark incoming = mark(1, "CAT 1", 40.0);
        MarksReadState.preserve(Collections.singletonList(incoming), Arrays.asList(old, duplicate));
        assertFalse(incoming.isRead);
    }

    private static void preserve(Mark incoming, Mark old) {
        MarksReadState.preserve(Collections.singletonList(incoming), Collections.singletonList(old));
    }

    private static Mark mark(int courseId, String title, Double score) {
        Mark result = new Mark(); result.courseId = courseId; result.title = title; result.score = score;
        result.maxScore = 50.0; result.weightage = 12.0; result.maxWeightage = 15.0; result.status = "Present";
        return result;
    }

    private static Course course(int id, String type, String semester, String classId) {
        Course result = new Course(); result.id = id; result.code = "CSE1001"; result.type = type;
        result.semesterId = semester; result.vtopClassId = classId; return result;
    }
}
