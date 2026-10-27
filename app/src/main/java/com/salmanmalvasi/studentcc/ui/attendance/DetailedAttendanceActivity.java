package com.salmanmalvasi.studentcc.ui.attendance;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;

public class DetailedAttendanceActivity extends AppCompatActivity {
    public static final String EXTRA_COURSE_ID = "course_id";
    public static final String EXTRA_COURSE_CODE = "course_code";
    public static final String EXTRA_COURSE_TITLE = "course_title";

    private final CompositeDisposable bag = new CompositeDisposable();
    private DetailedAttendanceAdapter adapter;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detailed_attendance);

        // Set up toolbar
        String courseTitle = getIntent().getStringExtra(EXTRA_COURSE_TITLE);
        String courseCode = getIntent().getStringExtra(EXTRA_COURSE_CODE);
        if (courseTitle != null && !courseTitle.isEmpty()) {
            setTitle("Attendance: " + courseTitle);
        } else if (courseCode != null && !courseCode.isEmpty()) {
            setTitle("Attendance: " + courseCode);
        } else {
            setTitle("Detailed Attendance");
        }

        // Set up RecyclerView
        RecyclerView rv = findViewById(R.id.recycler_attendance);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DetailedAttendanceAdapter();
        rv.setAdapter(adapter);

        // Load course names to display human-readable names on records
        bag.add(AppDatabase.getInstance(this).coursesDao().getAllCourses()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(courses -> {
                    java.util.Map<Integer, String> namesMap = new java.util.HashMap<>();
                    for (com.salmanmalvasi.studentcc.models.Course c : courses) {
                        if (c != null) {
                            String name = (c.title != null && !c.title.isEmpty()) ? c.title : c.code;
                            namesMap.put(c.id, name);
                            int cId = getIntent().getIntExtra(EXTRA_COURSE_ID, -1);
                            if (cId != -1 && c.id == cId && (courseTitle == null || courseTitle.isEmpty())) {
                                setTitle("Attendance: " + name);
                            }
                        }
                    }
                    adapter.setCourseNames(namesMap);
                }, err -> {}));

        // Load data
        loadAttendanceData();
    }

    private void loadAttendanceData() {
        int courseId = getIntent().getIntExtra(EXTRA_COURSE_ID, -1);
        String courseCode = getIntent().getStringExtra(EXTRA_COURSE_CODE);

        if (courseId != -1) {
            // Load by course ID
            bag.add(AppDatabase.getInstance(this).attendanceRecordDao().getForCourse(courseId)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            adapter::submitList,
                            throwable -> {
                                Toast.makeText(this, "Error loading attendance data", Toast.LENGTH_SHORT).show();
                            }
                    ));
        } else if (courseCode != null && !courseCode.isEmpty()) {
            // Load by course code (find course ID first)
            bag.add(AppDatabase.getInstance(this).coursesDao().getCourseIdByCode(courseCode)
                    .subscribeOn(Schedulers.io())
                    .flatMap(id -> AppDatabase.getInstance(this).attendanceRecordDao().getForCourse(id))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            adapter::submitList,
                            throwable -> {
                                Toast.makeText(this, "Error loading attendance data", Toast.LENGTH_SHORT).show();
                            }
                    ));
        } else {
            // Load all attendance records
            bag.add(AppDatabase.getInstance(this).attendanceRecordDao().getAll()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            records -> {
                                if (records.isEmpty()) {
                                    Toast.makeText(this, "No detailed attendance data found. Try downloading data from VTOP first.", Toast.LENGTH_LONG).show();
                                } else {
                                    adapter.submitList(records);
                                }
                            },
                            throwable -> {
                                Toast.makeText(this, "Error loading attendance data: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                    ));
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        bag.clear();
    }
}
