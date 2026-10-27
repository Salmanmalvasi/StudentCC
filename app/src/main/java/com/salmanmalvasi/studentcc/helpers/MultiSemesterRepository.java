package com.salmanmalvasi.studentcc.helpers;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.salmanmalvasi.studentcc.models.AllSemestersAnalytics;
import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.models.SemesterSummary;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiSemesterRepository {
    private static final String PREF_NAME = "multi_semester_analytics_pref";
    private static final String KEY_SEMESTERS_DATA = "key_semesters_summary_data";
    private static final String KEY_LOW_DATA_MODE = "key_low_data_mode";
    private static final String KEY_DATA_VERSION = "key_data_version_v6";
    private static final int CURRENT_DATA_VERSION = 6;

    private final Context context;
    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    public MultiSemesterRepository(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        cleanLegacyData();
    }

    public static MultiSemesterRepository getInstance(Context context) {
        return new MultiSemesterRepository(context);
    }

    public void cleanLegacyData() {
        int version = preferences.getInt(KEY_DATA_VERSION, 0);
        if (version < CURRENT_DATA_VERSION) {
            preferences.edit()
                    .remove(KEY_SEMESTERS_DATA)
                    .putInt(KEY_DATA_VERSION, CURRENT_DATA_VERSION)
                    .apply();
            android.util.Log.i("MultiSemRepo", "Purged legacy fake multi-semester cache (v" + CURRENT_DATA_VERSION + ")");
        }
    }

    public static int getStudentAdmissionYear(String regNo) {
        if (regNo != null && regNo.trim().length() >= 2) {
            String clean = regNo.trim().toUpperCase(java.util.Locale.US);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d{2})").matcher(clean);
            if (matcher.find()) {
                try {
                    int yy = Integer.parseInt(matcher.group(1));
                    if (yy >= 15 && yy <= 35) {
                        return 2000 + yy;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    public static int extractSemesterStartYear(String semName) {
        if (semName == null) return -1;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(20\\d{2})");
        java.util.regex.Matcher matcher = pattern.matcher(semName);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    public List<String> getEligibleSemesterNames(String authorizedId) {
        if (authorizedId == null || authorizedId.isEmpty()) {
            authorizedId = SettingsRepository.getAuthorizedUsername(context);
        }
        int admissionYear = getStudentAdmissionYear(authorizedId);
        List<String> allSems = getAvailableSemesterNames();
        List<String> eligible = new ArrayList<>();

        for (String sem : allSems) {
            if (admissionYear > 0) {
                int semYear = extractSemesterStartYear(sem);
                if (semYear > 0 && semYear < admissionYear) {
                    continue; // Skip impossible semesters before admission
                }
            }
            if (!eligible.contains(sem)) {
                eligible.add(sem);
            }
        }
        return eligible;
    }

    public boolean isLowDataMode() {
        return preferences.getBoolean(KEY_LOW_DATA_MODE, true);
    }

    public void setLowDataMode(boolean enabled) {
        preferences.edit().putBoolean(KEY_LOW_DATA_MODE, enabled).apply();
    }

    public void saveSemesterSummaries(List<SemesterSummary> list) {
        if (list == null) return;
        String json = gson.toJson(list);
        preferences.edit().putString(KEY_SEMESTERS_DATA, json).apply();
    }

    public void upsertSemesterSummary(SemesterSummary summary) {
        if (summary == null || summary.semesterId == null) return;
        List<SemesterSummary> list = getSemesterSummaries();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            SemesterSummary existing = list.get(i);
            if (summary.semesterId.equalsIgnoreCase(existing.semesterId) ||
                    (summary.semesterName != null && summary.semesterName.equalsIgnoreCase(existing.semesterName))) {
                if ((summary.totalClasses == 0 && (summary.subjects == null || summary.subjects.isEmpty())) &&
                        (existing.totalClasses > 0 || (existing.subjects != null && !existing.subjects.isEmpty()))) {
                    return; // Preserve existing populated data
                }
                list.set(i, summary);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            list.add(summary);
        }
        saveSemesterSummaries(list);
    }

    public List<SemesterSummary> getSemesterSummaries() {
        cleanLegacyData();
        String json = preferences.getString(KEY_SEMESTERS_DATA, null);
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            Type type = new TypeToken<List<SemesterSummary>>() {}.getType();
            List<SemesterSummary> rawData = gson.fromJson(json, type);
            if (rawData == null) return new ArrayList<>();

            String username = SettingsRepository.getAuthorizedUsername(context);
            SharedPreferences sp = SettingsRepository.getSharedPreferences(context);
            String activeSemName = sp.getString("semester", "");
            int admissionYear = getStudentAdmissionYear(username);

            List<SemesterSummary> sanitized = new ArrayList<>();
            for (SemesterSummary sem : rawData) {
                if (sem == null) continue;
                if (admissionYear > 0) {
                    int semYear = extractSemesterStartYear(sem.semesterName);
                    if (semYear > 0 && semYear < admissionYear) {
                        continue; // Discard impossible semester before admission
                    }
                }
                // Exclude empty semesters that have neither classes nor subject records
                if (sem.totalClasses <= 0 && (sem.subjects == null || sem.subjects.isEmpty())) {
                    continue;
                }
                sanitized.add(sem);
            }
            return sanitized;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public boolean hasData() {
        List<SemesterSummary> list = getSemesterSummaries();
        return list != null && !list.isEmpty();
    }

    /**
     * Builds the current active semester summary directly from real Room database entities
     * and SharedPreferences.
     */
    public SemesterSummary buildCurrentSemesterSummary() {
        try {
            SharedPreferences sp = SettingsRepository.getSharedPreferences(context);
            String semName = sp.getString("semester", "Current Semester");
            String semId = sp.getString("semesterSubId", "CURRENT_SEM");
            float overallAtt = sp.getFloat("overallAttendance", 0f);
            int attendedClasses = sp.getInt("attendedClasses", 0);
            int totalClasses = sp.getInt("totalClasses", 0);

            SemesterSummary sem = new SemesterSummary(semId, semName);
            sem.attendedClasses = attendedClasses;
            sem.totalClasses = totalClasses;
            sem.attendancePercentage = overallAtt;

            AppDatabase db = AppDatabase.getInstance(context);
            List<Course> courses = null;
            try {
                courses = db.coursesDao().getAllCourses().blockingGet();
            } catch (Exception ignored) {}

            List<Attendance> attendances = null;
            try {
                attendances = db.attendanceDao().getAll().blockingGet();
            } catch (Exception ignored) {}

            Map<Integer, Attendance> attMap = new HashMap<>();
            if (attendances != null) {
                for (Attendance a : attendances) {
                    attMap.put(a.courseId, a);
                }
            }

            if (courses != null && !courses.isEmpty()) {
                sem.courseCount = courses.size();
                double highestScore = -1;
                double lowestScore = 9999;
                double marksSum = 0;
                int marksCount = 0;

                Map<String, Float> marksMap = new HashMap<>();
                try {
                    List<CumulativeMark> cumMarks = db.marksDao().getAllCumulativeMarks().blockingGet();
                    if (cumMarks != null) {
                        for (CumulativeMark cm : cumMarks) {
                            if (cm.courseCode != null && cm.grandTotal != null && cm.grandMax != null && cm.grandMax > 0) {
                                float pct = (float) ((cm.grandTotal / cm.grandMax) * 100.0);
                                marksMap.put(cm.courseCode.toUpperCase().trim(), pct);
                            }
                        }
                    }
                } catch (Exception ignored) {}

                for (Course c : courses) {
                    Attendance a = attMap.get(c.id);
                    float attPct = (a != null && a.percentage != null) ? a.percentage : 0f;
                    int attAttended = (a != null && a.attended != null) ? a.attended : 0;
                    int attTotal = (a != null && a.total != null) ? a.total : 0;

                    String code = (c.code != null) ? c.code.trim() : "";
                    String title = (c.title != null) ? c.title.trim() : code;

                    float markPct = marksMap.containsKey(code.toUpperCase()) ? marksMap.get(code.toUpperCase()) : 0f;
                    if (markPct <= 0f) {
                        try {
                            List<Mark.AllData> individualMarks = db.marksDao().getMarks(c.id).blockingGet();
                            if (individualMarks != null && !individualMarks.isEmpty()) {
                                double scoredWeight = 0;
                                double maxWeight = 0;
                                for (Mark.AllData m : individualMarks) {
                                    if (m.weightage != null && m.maxWeightage != null && m.maxWeightage > 0) {
                                        scoredWeight += m.weightage;
                                        maxWeight += m.maxWeightage;
                                    } else if (m.score != null && m.maxScore != null && m.maxScore > 0) {
                                        scoredWeight += m.score;
                                        maxWeight += m.maxScore;
                                    }
                                }
                                if (maxWeight > 0) {
                                    markPct = (float) ((scoredWeight / maxWeight) * 100.0);
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    SemesterSummary.SubjectStat sub = new SemesterSummary.SubjectStat(code, title, attPct, markPct);
                    sub.attended = attAttended;
                    sub.total = attTotal;
                    sem.subjects.add(sub);

                    if (markPct > 0) {
                        marksSum += markPct;
                        marksCount++;
                        if (markPct > highestScore) {
                            highestScore = markPct;
                            sem.highestSubjectCode = code;
                            sem.highestSubjectName = title;
                            sem.highestSubjectScore = markPct;
                        }
                        if (markPct < lowestScore) {
                            lowestScore = markPct;
                            sem.lowestSubjectCode = code;
                            sem.lowestSubjectName = title;
                            sem.lowestSubjectScore = markPct;
                        }
                    }
                }

                if (marksCount > 0) {
                    sem.averageMarksPercentage = (float) (marksSum / marksCount);
                }
            }
            return sem;
        } catch (Exception e) {
            android.util.Log.e("MultiSemRepo", "Error building current semester: " + e.getMessage());
            return null;
        }
    }

    /**
     * Computes the aggregated multi-semester analytics across all saved semesters.
     * Incorporates real current semester database records and student's official semesters.
     */
    /**
     * Retrieves the list of official semester names available from the student's VTOP session.
     */
    public List<String> getAvailableSemesterNames() {
        SharedPreferences sp = SettingsRepository.getSharedPreferences(context);
        String availableSemestersStr = sp.getString("availableSemesters", null);
        List<String> result = new ArrayList<>();
        if (availableSemestersStr != null && !availableSemestersStr.trim().isEmpty()) {
            String[] sems = availableSemestersStr.split(",");
            for (String s : sems) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    /**
     * Executes non-blocking background fetch of historical semester grades and attendance
     * using the authenticated VTOP session tokens.
     */
    public void syncHistoricalSemestersInBackground(String csrf, String authId, String cookieHeader, String userAgent) {
        if (csrf == null || csrf.isEmpty() || authId == null || authId.isEmpty() || cookieHeader == null || cookieHeader.isEmpty()) {
            return;
        }

        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SharedPreferences sp = SettingsRepository.getSharedPreferences(context);
                String activeSem = sp.getString("semester", "");
                String activeSemSubId = sp.getString("semesterSubId", "");

                Map<String, String> semMap = new HashMap<>();
                String semMapJson = sp.getString("semestersMapJson", null);
                if (semMapJson != null) {
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(semMapJson);
                        java.util.Iterator<String> keys = json.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            semMap.put(k, json.getString(k));
                        }
                    } catch (Exception ignored) {}
                }

                List<String> semNames = getEligibleSemesterNames(authId);
                if (semNames.isEmpty() && !semMap.isEmpty()) {
                    int admissionYear = getStudentAdmissionYear(authId);
                    for (String k : semMap.keySet()) {
                        int semYear = extractSemesterStartYear(k);
                        if (admissionYear <= 0 || semYear <= 0 || semYear >= admissionYear) {
                            semNames.add(k);
                        }
                    }
                }

                for (String semName : semNames) {
                    String semId = semMap.get(semName);
                    if (semId == null || semId.isEmpty()) continue;

                    if (semName.equalsIgnoreCase(activeSem) || semId.equalsIgnoreCase(activeSemSubId)) {
                        continue;
                    }

                    SemesterSummary summary = FastScraper.fetchSemesterGrades(csrf, authId, semId, semName, cookieHeader, userAgent);
                    if (summary != null && !summary.subjects.isEmpty()) {
                        upsertSemesterSummary(summary);
                    }
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ignored) {}
                }

                computeAnalytics();
                android.util.Log.i("MultiSemRepo", "Historical semesters background sync complete.");
            } catch (Exception e) {
                android.util.Log.w("MultiSemRepo", "Historical sync notice: " + e.getMessage());
            }
        });
    }

    /**
     * Computes the aggregated multi-semester analytics across all saved semesters.
     * Incorporates real current semester database records and student's official semesters.
     */
    public AllSemestersAnalytics computeAnalytics() {
        cleanLegacyData();

        // 1. Purge legacy empty shell summaries from SharedPreferences
        List<SemesterSummary> savedList = getSemesterSummaries();
        List<SemesterSummary> cleanedList = new ArrayList<>();
        for (SemesterSummary sem : savedList) {
            if (sem != null && (sem.totalClasses > 0 || (sem.subjects != null && !sem.subjects.isEmpty()))) {
                cleanedList.add(sem);
            }
        }
        if (cleanedList.size() != savedList.size()) {
            saveSemesterSummaries(cleanedList);
        }

        // 2. Incorporate real active semester from Room SQLite
        SemesterSummary currentRealSem = buildCurrentSemesterSummary();
        if (currentRealSem != null && (currentRealSem.totalClasses > 0 || currentRealSem.courseCount > 0)) {
            upsertSemesterSummary(currentRealSem);
        }

        // 3. Retrieve only valid, populated summaries
        List<SemesterSummary> list = new ArrayList<>();
        for (SemesterSummary sem : getSemesterSummaries()) {
            if (sem != null && (sem.totalClasses > 0 || (sem.subjects != null && !sem.subjects.isEmpty()))) {
                list.add(sem);
            }
        }

        AllSemestersAnalytics analytics = new AllSemestersAnalytics();
        analytics.semesterSummaries = new ArrayList<>(list);

        int totalAttended = 0;
        int totalConducted = 0;
        double marksSum = 0;
        int marksCount = 0;

        String highestSubjectName = "";
        String highestSubjectCode = "";
        double highestSubjectScore = -1;
        String highestSubjectSem = "";

        String lowestSubjectName = "";
        String lowestSubjectCode = "";
        double lowestSubjectScore = 9999;
        String lowestSubjectSem = "";

        String lowestSemName = "";
        float lowestSemAtt = 101f;

        String highestSemName = "";
        float highestSemAtt = -1f;

        for (SemesterSummary sem : list) {
            if (sem.totalClasses > 0) {
                totalAttended += sem.attendedClasses;
                totalConducted += sem.totalClasses;
            }

            if (sem.averageMarksPercentage > 0) {
                marksSum += sem.averageMarksPercentage;
                marksCount++;
            }

            // Track lowest semester attendance (only consider semesters with actual classes)
            if (sem.totalClasses > 0 && sem.attendancePercentage > 0 && sem.attendancePercentage < lowestSemAtt) {
                lowestSemAtt = sem.attendancePercentage;
                lowestSemName = sem.semesterName;
            }

            // Track highest semester attendance
            if (sem.totalClasses > 0 && sem.attendancePercentage > highestSemAtt) {
                highestSemAtt = sem.attendancePercentage;
                highestSemName = sem.semesterName;
            }

            // Track subjects within semester
            if (sem.subjects != null) {
                for (SemesterSummary.SubjectStat sub : sem.subjects) {
                    if (sub.marksPercentage > 0) {
                        if (sub.marksPercentage > highestSubjectScore) {
                            highestSubjectScore = sub.marksPercentage;
                            highestSubjectName = sub.courseTitle != null && !sub.courseTitle.isEmpty() ? sub.courseTitle : sub.courseCode;
                            highestSubjectCode = sub.courseCode;
                            highestSubjectSem = sem.semesterName;
                        }

                        if (sub.marksPercentage < lowestSubjectScore) {
                            lowestSubjectScore = sub.marksPercentage;
                            lowestSubjectName = sub.courseTitle != null && !sub.courseTitle.isEmpty() ? sub.courseTitle : sub.courseCode;
                            lowestSubjectCode = sub.courseCode;
                            lowestSubjectSem = sem.semesterName;
                        }
                    }
                }
            }
        }

        analytics.totalAttendedClasses = totalAttended;
        analytics.totalConductedClasses = totalConducted;
        analytics.overallAttendancePercentage = totalConducted > 0 ?
                Math.round((totalAttended * 10000.0) / totalConducted) / 100.0f : 0.0f;

        analytics.overallAverageMarks = marksCount > 0 ? (float) (Math.round((marksSum / marksCount) * 100.0) / 100.0) : 0.0f;

        // Extremes
        analytics.highestSubjectName = highestSubjectScore > 0 ? highestSubjectName : "";
        analytics.highestSubjectCode = highestSubjectScore > 0 ? highestSubjectCode : "";
        analytics.highestSubjectScore = highestSubjectScore > 0 ? Math.round(highestSubjectScore * 10.0) / 10.0 : 0.0;
        analytics.highestSubjectSemester = highestSubjectSem;

        analytics.lowestSubjectName = (lowestSubjectScore < 9999 && lowestSubjectScore > 0) ? lowestSubjectName : "";
        analytics.lowestSubjectCode = (lowestSubjectScore < 9999 && lowestSubjectScore > 0) ? lowestSubjectCode : "";
        analytics.lowestSubjectScore = (lowestSubjectScore < 9999 && lowestSubjectScore > 0) ? Math.round(lowestSubjectScore * 10.0) / 10.0 : 0.0;
        analytics.lowestSubjectSemester = lowestSubjectSem;

        // Only report lowest semester attendance if more than 1 semester is compared
        if (list.size() > 1 && lowestSemAtt <= 100f && !lowestSemName.isEmpty()) {
            analytics.lowestAttendanceSemesterName = lowestSemName;
            analytics.lowestAttendancePercentage = Math.round(lowestSemAtt * 10.0) / 10.0f;
        } else {
            analytics.lowestAttendanceSemesterName = "";
            analytics.lowestAttendancePercentage = 0.0f;
        }

        if (highestSemAtt >= 0f && !highestSemName.isEmpty()) {
            analytics.highestAttendanceSemesterName = highestSemName;
            analytics.highestAttendancePercentage = Math.round(highestSemAtt * 10.0) / 10.0f;
        } else {
            analytics.highestAttendanceSemesterName = "";
            analytics.highestAttendancePercentage = 0.0f;
        }

        return analytics;
    }
}
