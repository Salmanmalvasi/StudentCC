package com.salmanmalvasi.studentcc.helpers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.salmanmalvasi.studentcc.interfaces.AssignmentsDao;
import com.salmanmalvasi.studentcc.interfaces.AttendanceDao;
import com.salmanmalvasi.studentcc.interfaces.AttendanceRecordDao;
import com.salmanmalvasi.studentcc.interfaces.CalendarEventDao;
import com.salmanmalvasi.studentcc.interfaces.ClassStatsDao;
import com.salmanmalvasi.studentcc.interfaces.CoursesDao;
import com.salmanmalvasi.studentcc.interfaces.EventHubEventDao;
import com.salmanmalvasi.studentcc.interfaces.ExamsDao;
import com.salmanmalvasi.studentcc.interfaces.LaundryScheduleDao;
import com.salmanmalvasi.studentcc.interfaces.MarksDao;
import com.salmanmalvasi.studentcc.interfaces.MessExpenseDao;
import com.salmanmalvasi.studentcc.interfaces.MessMenuDao;
import com.salmanmalvasi.studentcc.interfaces.ReceiptsDao;
import com.salmanmalvasi.studentcc.interfaces.SpotlightDao;
import com.salmanmalvasi.studentcc.interfaces.StaffDao;
import com.salmanmalvasi.studentcc.interfaces.TimetableDao;
import com.salmanmalvasi.studentcc.models.Assignment;
import com.salmanmalvasi.studentcc.models.Attachment;
import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.AttendanceRecord;
import com.salmanmalvasi.studentcc.models.CalendarEvent;
import com.salmanmalvasi.studentcc.models.ClassStats;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.EventHubEvent;
import com.salmanmalvasi.studentcc.models.Exam;
import com.salmanmalvasi.studentcc.models.LaundrySchedule;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.models.MessExpense;
import com.salmanmalvasi.studentcc.models.MessMenu;
import com.salmanmalvasi.studentcc.models.Receipt;
import com.salmanmalvasi.studentcc.models.SkipRecommendation;
import com.salmanmalvasi.studentcc.models.Slot;
import com.salmanmalvasi.studentcc.interfaces.LeaveDao;
import com.salmanmalvasi.studentcc.models.Leave;
import com.salmanmalvasi.studentcc.models.Spotlight;
import com.salmanmalvasi.studentcc.models.Staff;
import com.salmanmalvasi.studentcc.models.Timetable;

@Database(
        entities = {
                Assignment.class,
                Attachment.class,
                Attendance.class,
                AttendanceRecord.class,
                CalendarEvent.class,
                ClassStats.class,
                Course.class,
                CumulativeMark.class,
                EventHubEvent.class,
                Exam.class,
                LaundrySchedule.class,
                Leave.class,
                Mark.class,
                MessExpense.class,
                MessMenu.class,
                Receipt.class,
                SkipRecommendation.class,
                Slot.class,
                Spotlight.class,
                Staff.class,
                Timetable.class
        },
    version = 13,
        autoMigrations = {
                @AutoMigration(from = 1, to = 2),
                @AutoMigration(from = 3, to = 4),
                @AutoMigration(from = 5, to = 6),
                @AutoMigration(from = 6, to = 7),
                @AutoMigration(from = 7, to = 8),
                @AutoMigration(from = 8, to = 9),
                @AutoMigration(from = 9, to = 10),
                @AutoMigration(from = 10, to = 11),
                @AutoMigration(from = 11, to = 12),
                @AutoMigration(from = 12, to = 13),
        }
)
public abstract class AppDatabase extends RoomDatabase {
    public static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    AppDatabase.class, "vit_student")
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build();
        }

        return instance;
    }

    public static synchronized void deleteDatabase(Context context) {
        if (instance != null) {
            instance.close();
        }

        instance = null;
        context.deleteDatabase("vit_student");
        context.deleteDatabase("vtop"); // Delete the deprecated database (used till < v4.0)
    }

    public abstract AssignmentsDao assignmentsDao();

    public abstract AttendanceDao attendanceDao();

    public abstract AttendanceRecordDao attendanceRecordDao();

    public abstract CalendarEventDao calendarEventDao();

    public abstract ClassStatsDao classStatsDao();

    public abstract CoursesDao coursesDao();

    public abstract ExamsDao examsDao();

    public abstract MarksDao marksDao();

    public abstract ReceiptsDao receiptsDao();

    public abstract SpotlightDao spotlightDao();

    public abstract StaffDao staffDao();

    public abstract TimetableDao timetableDao();

    public abstract LaundryScheduleDao laundryScheduleDao();

    public abstract MessMenuDao messMenuDao();

    public abstract MessExpenseDao messExpenseDao();

    public abstract LeaveDao leaveDao();

    public abstract EventHubEventDao eventHubEventDao();

    // Manual Migrations
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE receipts RENAME TO receipts_old");
            database.execSQL("CREATE TABLE receipts (number INTEGER NOT NULL PRIMARY KEY, amount REAL, date INTEGER)");
            database.execSQL("INSERT INTO receipts (number, amount) SELECT number, amount FROM receipts_old");
            database.execSQL("UPDATE receipts SET date = 0");
            database.execSQL("DROP TABLE receipts_old");
        }
    };
}
