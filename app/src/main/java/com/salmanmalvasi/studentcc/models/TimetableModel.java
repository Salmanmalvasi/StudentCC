package com.salmanmalvasi.studentcc.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable model representing a single timetable entry for display in calendar-style lists.
 */
public class TimetableModel {

    private final String startTime;
    private final String endTime;
    private final String subjectName;
    private final String facultyName;
    private final String room;
    private final ClassType classType;

    public enum ClassType {
        THEORY,
        LAB,
        TUTORIAL
    }

    public TimetableModel(@NonNull String startTime,
                          @NonNull String endTime,
                          @NonNull String subjectName,
                          @Nullable String facultyName,
                          @Nullable String room,
                          @NonNull ClassType classType) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.subjectName = subjectName;
        this.facultyName = facultyName;
        this.room = room;
        this.classType = classType;
    }

    @NonNull
    public String getStartTime() {
        return startTime;
    }

    @NonNull
    public String getEndTime() {
        return endTime;
    }

    @NonNull
    public String getSubjectName() {
        return subjectName;
    }

    @Nullable
    public String getFacultyName() {
        return facultyName;
    }

    @Nullable
    public String getRoom() {
        return room;
    }

    @NonNull
    public ClassType getClassType() {
        return classType;
    }
}
