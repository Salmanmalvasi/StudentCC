package com.salmanmalvasi.studentcc.helpers;

import android.content.Context;
import android.util.Log;
import android.webkit.CookieManager;

import com.salmanmalvasi.studentcc.models.SemesterSummary;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * High-performance, low-bandwidth direct HTTP scraper for VTOP multi-semester analytics.
 * Features:
 * - Direct HTTP POST without WebView rendering overhead
 * - Automatic GZIP compression/decompression
 * - Robust unescaping of AJAX JSON/HTML responses
 * - Bulletproof cell-heuristic fallbacks for VTOP tables
 * - Session expiry detection
 */
public class FastScraper {
    private static final String TAG = "FastScraper";
    public static final String BASE_URL = "https://vtopcc.vit.ac.in/vtop/";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 25000;
    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36";

    private static OkHttpClient okHttpClient;

    public static class SessionExpiredException extends Exception {
        public SessionExpiredException(String message) {
            super(message);
        }
    }

    private static synchronized OkHttpClient getClient() {
        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build();
        }
        return okHttpClient;
    }

    /**
     * Executes an authenticated HTTP POST request to VTOP with default user agent.
     */
    public static String post(String endpoint, String postData, String cookies) throws Exception {
        return post(endpoint, postData, cookies, DEFAULT_USER_AGENT);
    }

    /**
     * Executes an authenticated HTTP POST request to VTOP.
     */
    public static String post(String endpoint, String postData, String cookies, String userAgent) throws Exception {
        String fullUrl;
        if (endpoint.startsWith("http")) {
            fullUrl = endpoint;
        } else if (endpoint.startsWith("/")) {
            fullUrl = "https://vtopcc.vit.ac.in/vtop" + endpoint;
        } else {
            fullUrl = BASE_URL + endpoint;
        }

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8"),
                postData != null ? postData : ""
        );

        String ua = (userAgent != null && !userAgent.isEmpty()) ? userAgent : DEFAULT_USER_AGENT;

        Request.Builder requestBuilder = new Request.Builder()
                .url(fullUrl)
                .post(body)
                .header("User-Agent", ua)
                .header("Referer", "https://vtopcc.vit.ac.in/vtop/content")
                .header("Origin", "https://vtopcc.vit.ac.in")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "gzip, deflate");

        if (fullUrl.contains("vtopcc5.vit.ac.in") || fullUrl.contains("/login/vitian/")) {
            requestBuilder.header("S-API-KEY", "S@fe@VITCC123$#%");
        }

        if (cookies != null && !cookies.trim().isEmpty()) {
            requestBuilder.header("Cookie", cookies);
        }

        try (Response response = getClient().newCall(requestBuilder.build()).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                throw new SessionExpiredException("HTTP " + response.code() + " session expired");
            }
            if (!response.isSuccessful()) {
                throw new Exception("HTTP request failed with code: " + response.code());
            }
            ResponseBody responseBody = response.body();
            String res = responseBody != null ? responseBody.string() : "";

            if (isSessionExpired(res)) {
                throw new SessionExpiredException("VTOP session has expired or login required");
            }

            return res;
        }
    }

    /**
     * Unescapes unicode characters (\u003c, etc.) and escaped quotes from JSON AJAX responses.
     */
    public static String cleanHtml(String raw) {
        if (raw == null) return "";
        return raw.replace("\\u003C", "<").replace("\\u003E", ">")
                .replace("\\u003c", "<").replace("\\u003e", ">")
                .replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\r", "").replace("\\n", "");
    }

    /**
     * Checks if the response string is a login redirect or session timeout notice.
     */
    public static boolean isSessionExpired(String response) {
        if (response == null || response.trim().isEmpty()) return false;
        String lower = response.toLowerCase(Locale.US);
        return lower.contains("not authorized") ||
                lower.contains("session timed out") ||
                lower.contains("session expired") ||
                lower.contains("login to your account") ||
                lower.contains("form id=\"vtoploginform\"") ||
                lower.contains("action=\"dologin\"");
    }

    /**
     * Retrieves cookies from CookieManager for VTOP domains.
     */
    public static String getVtopCookies() {
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            String cookiesVtop = cookieManager.getCookie("https://vtopcc.vit.ac.in/vtop");
            String cookiesVit = cookieManager.getCookie("https://vtopcc.vit.ac.in");

            if (cookiesVtop != null && !cookiesVtop.isEmpty() && cookiesVit != null && !cookiesVit.isEmpty()) {
                if (cookiesVtop.contains("JSESSIONID") || cookiesVtop.contains("SERVERID")) {
                    return cookiesVtop;
                }
                return cookiesVit;
            }
            if (cookiesVtop != null && !cookiesVtop.isEmpty()) return cookiesVtop;
            if (cookiesVit != null && !cookiesVit.isEmpty()) return cookiesVit;
            return "";
        } catch (Exception e) {
            Log.e(TAG, "Error obtaining cookies: " + e.getMessage());
            return "";
        }
    }

    /**
     * Fetches official historical grades and GPA for a completed semester via doStudentGradeView.
     * Prevents duplicate attendance by explicitly ensuring totalClasses=0 for archived semesters.
     */
    public static SemesterSummary fetchSemesterGrades(
            String csrf,
            String authorizedId,
            String semesterSubId,
            String semesterName,
            String cookies,
            String userAgent) {
        SemesterSummary summary = new SemesterSummary(semesterSubId, semesterName);
        summary.isCompleted = true;
        summary.totalClasses = 0;
        summary.attendedClasses = 0;
        summary.attendancePercentage = 0f;

        try {
            String marksData = "semesterSubId=" + URLEncoder.encode(semesterSubId, "UTF-8") +
                    "&authorizedID=" + URLEncoder.encode(authorizedId, "UTF-8") +
                    "&_csrf=" + URLEncoder.encode(csrf, "UTF-8");

            Log.d(TAG, "Fetching grades for semester: " + semesterName + " (" + semesterSubId + ")");
            String html = post("examinations/examGradeView/doStudentGradeView", marksData, cookies, userAgent);
            String cleaned = cleanHtml(html);

            if (cleaned.toLowerCase(Locale.US).contains("no records") ||
                    cleaned.toLowerCase(Locale.US).contains("not authorized") ||
                    cleaned.toLowerCase(Locale.US).contains("unauthorised")) {
                Log.d(TAG, "No grade records for: " + semesterName + " (not enrolled)");
                return null;
            }

            parseGradeViewIntoSummary(cleaned, summary);
            if (summary.subjects.isEmpty()) {
                Log.d(TAG, "No courses found in grade view for: " + semesterName);
                return null;
            }

            Log.i(TAG, "Successfully parsed " + summary.subjects.size() + " courses and GPA=" + summary.gpa + " for " + semesterName);
            return summary;
        } catch (SessionExpiredException e) {
            Log.w(TAG, "Session expired fetching grades for " + semesterName + ": " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error fetching grades for " + semesterName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses official doStudentGradeView HTML into SemesterSummary.
     */
    public static void parseGradeViewIntoSummary(String html, SemesterSummary summary) {
        if (html == null || html.trim().isEmpty()) return;
        try {
            Document doc = Jsoup.parse(html);
            Element table = doc.selectFirst("table");
            if (table == null) return;

            Elements rows = table.select("tbody > tr");
            if (rows.isEmpty()) rows = table.select("tr");
            if (rows.isEmpty()) return;

            Elements headerCells = table.select("th");
            int codeIdx = -1, titleIdx = -1, creditsIdx = -1, gradeIdx = -1;
            for (int i = 0; i < headerCells.size(); i++) {
                String h = headerCells.get(i).text().toLowerCase(Locale.US);
                if (h.contains("code")) codeIdx = i;
                else if (h.contains("title")) titleIdx = i;
                else if (h.contains("credit")) creditsIdx = i;
                else if (h.contains("grade")) gradeIdx = i;
            }

            double marksSum = 0;
            int marksCount = 0;
            double highestScore = -1;
            double lowestScore = 9999;

            for (Element row : rows) {
                String rowText = row.text().trim();
                if (rowText.toLowerCase(Locale.US).contains("gpa") && rowText.contains(":")) {
                    try {
                        String[] parts = rowText.split(":");
                        if (parts.length >= 2) {
                            String gpaPart = parts[1].trim().split(" ")[0].trim();
                            if (!gpaPart.isEmpty()) summary.gpa = gpaPart;
                        }
                    } catch (Exception ignored) {}
                    continue;
                }

                Elements cells = row.select("td");
                if (cells.size() < 3) continue;

                String code = "";
                String title = "";
                String grade = "";
                int credits = 0;

                for (Element cell : cells) {
                    String t = cell.text().trim();
                    if (t.matches("^[A-Za-z]{3,4}\\d{3,4}[A-Za-z]?$")) {
                        code = t;
                        break;
                    }
                }

                if (code.isEmpty() && codeIdx >= 0 && cells.size() > codeIdx) {
                    code = cells.get(codeIdx).text().trim();
                }

                if (code.isEmpty() || !code.matches("^[A-Za-z]{3,4}\\d{3,4}[A-Za-z]?$")) {
                    continue;
                }

                if (titleIdx >= 0 && cells.size() > titleIdx) {
                    title = cells.get(titleIdx).text().trim();
                }
                if (title.isEmpty() || title.equalsIgnoreCase(code)) {
                    for (Element cell : cells) {
                        String t = cell.text().trim();
                        if (t.length() > 5 && !t.equalsIgnoreCase(code) && !t.matches("^[0-9.]+$")) {
                            title = t;
                            break;
                        }
                    }
                }
                if (title.isEmpty()) title = code;

                if (creditsIdx >= 0 && cells.size() > creditsIdx) {
                    try {
                        credits = Integer.parseInt(cells.get(creditsIdx).text().replaceAll("[^0-9]", "").trim());
                    } catch (Exception ignored) {}
                }

                for (Element cell : cells) {
                    String t = cell.text().trim().toUpperCase(Locale.US);
                    if (t.matches("^[SABCDEF]$")) {
                        grade = t;
                        break;
                    }
                }
                if (grade.isEmpty() && gradeIdx >= 0 && cells.size() > gradeIdx) {
                    String t = cells.get(gradeIdx).text().trim().toUpperCase(Locale.US);
                    if (t.matches("^[SABCDEF]$")) grade = t;
                }

                float gradePct = 0f;
                if ("S".equalsIgnoreCase(grade)) gradePct = 95f;
                else if ("A".equalsIgnoreCase(grade)) gradePct = 85f;
                else if ("B".equalsIgnoreCase(grade)) gradePct = 75f;
                else if ("C".equalsIgnoreCase(grade)) gradePct = 65f;
                else if ("D".equalsIgnoreCase(grade)) gradePct = 55f;
                else if ("E".equalsIgnoreCase(grade)) gradePct = 45f;

                SemesterSummary.SubjectStat sub = new SemesterSummary.SubjectStat();
                sub.courseCode = code;
                sub.courseTitle = title;
                sub.grade = grade;
                sub.credits = credits;
                sub.marksPercentage = gradePct;
                sub.attended = 0;
                sub.total = 0;
                sub.attendancePercentage = 0f;

                summary.subjects.add(sub);

                if (gradePct > 0) {
                    marksSum += gradePct;
                    marksCount++;
                    if (gradePct > highestScore) {
                        highestScore = gradePct;
                        summary.highestSubjectCode = code;
                        summary.highestSubjectName = title;
                        summary.highestSubjectScore = gradePct;
                    }
                    if (gradePct < lowestScore) {
                        lowestScore = gradePct;
                        summary.lowestSubjectCode = code;
                        summary.lowestSubjectName = title;
                        summary.lowestSubjectScore = gradePct;
                    }
                }
            }

            summary.courseCount = summary.subjects.size();
            summary.totalClasses = 0;
            summary.attendedClasses = 0;
            summary.attendancePercentage = 0f;
            summary.isCompleted = true;

            if (marksCount > 0) {
                summary.averageMarksPercentage = (float) Math.round((marksSum / marksCount) * 10.0) / 10.0f;
            }

            if (summary.gpa == null || summary.gpa.isEmpty()) {
                String fullText = table.text();
                Pattern p = Pattern.compile("(?i)gpa\\s*[:=-]\\s*([0-9.]+)");
                Matcher m = p.matcher(fullText);
                if (m.find()) {
                    summary.gpa = m.group(1).trim();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseGradeViewIntoSummary error: " + e.getMessage());
        }
    }

    /**
     * Backward-compatible scrapeSemesterData: delegates to fetchSemesterGrades
     * to prevent duplicating active attendance into past semesters.
     */
    public static SemesterSummary scrapeSemesterData(
            String csrf,
            String authorizedId,
            String semesterSubId,
            String semesterName,
            String cookies,
            String userAgent) {
        return fetchSemesterGrades(csrf, authorizedId, semesterSubId, semesterName, cookies, userAgent);
    }

    /**
     * Overload for backwards compatibility.
     */
    public static SemesterSummary scrapeSemesterData(String csrf, String authorizedId, String semesterSubId, String semesterName, String cookies) {
        return scrapeSemesterData(csrf, authorizedId, semesterSubId, semesterName, cookies, DEFAULT_USER_AGENT);
    }

    /**
     * Parses attendance HTML table and updates SemesterSummary.
     */
    public static void parseAttendanceIntoSummary(String html, SemesterSummary summary) {
        if (html == null || html.isEmpty()) return;

        try {
            Document doc = Jsoup.parse(html);
            Element table = doc.getElementById("studentDetailsList");
            if (table == null) {
                Element div = doc.getElementById("getStudentDetails");
                if (div != null) {
                    table = div.selectFirst("table");
                    if (table == null && "table".equalsIgnoreCase(div.tagName())) {
                        table = div;
                    }
                }
            }
            if (table == null) {
                table = doc.selectFirst("table");
            }

            if (table == null) {
                Log.w(TAG, "No attendance table found in HTML response");
                return;
            }

            Elements headers = table.select("th");
            int courseCodeIdx = -1, courseTitleIdx = -1, courseTypeIdx = -1, slotIdx = -1;
            int attendedIdx = -1, totalIdx = -1, percentageIdx = -1;

            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i).text().toLowerCase(Locale.US);
                if (h.contains("course") && h.contains("code")) courseCodeIdx = i;
                else if (h.contains("title")) courseTitleIdx = i;
                else if (h.contains("course") && h.contains("type")) courseTypeIdx = i;
                else if (h.contains("slot")) slotIdx = i;
                else if (h.contains("attended")) attendedIdx = i;
                else if (h.contains("total")) totalIdx = i;
                else if (h.contains("percentage") || h.contains("%")) percentageIdx = i;
            }

            // Defaults matching standard VTOP layout
            if (courseCodeIdx < 0) courseCodeIdx = 1;
            if (courseTitleIdx < 0) courseTitleIdx = 2;
            if (courseTypeIdx < 0) courseTypeIdx = 3;
            if (slotIdx < 0) slotIdx = 4;
            if (attendedIdx < 0) attendedIdx = 6;
            if (totalIdx < 0) totalIdx = 7;
            if (percentageIdx < 0) percentageIdx = 8;

            Elements rows = table.select("tbody > tr");
            if (rows.isEmpty()) rows = table.select("tr");

            int totalAttended = 0;
            int totalClasses = 0;

            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() < 5) continue;

                // Detect course code
                String code = "";
                if (cells.size() > courseCodeIdx) {
                    code = cells.get(courseCodeIdx).text().trim();
                }
                if (!code.matches("^[A-Za-z]{3,4}\\d{3,4}[A-Za-z]?$")) {
                    for (Element cell : cells) {
                        String t = cell.text().trim();
                        if (t.matches("^[A-Za-z]{3,4}\\d{3,4}[A-Za-z]?$")) {
                            code = t;
                            break;
                        }
                    }
                }
                if (code.isEmpty()) continue;

                String title = (cells.size() > courseTitleIdx) ? cells.get(courseTitleIdx).text().trim() : code;
                String type = (cells.size() > courseTypeIdx) ? cells.get(courseTypeIdx).text().trim() : "";

                int attended = 0;
                int total = 0;
                float pct = 0f;

                try {
                    if (cells.size() > attendedIdx) {
                        attended = Integer.parseInt(cells.get(attendedIdx).text().replaceAll("[^0-9]", "").trim());
                    }
                    if (cells.size() > totalIdx) {
                        total = Integer.parseInt(cells.get(totalIdx).text().replaceAll("[^0-9]", "").trim());
                    }
                    if (cells.size() > percentageIdx) {
                        String pctStr = cells.get(percentageIdx).text().replaceAll("[^0-9.]", "").trim();
                        if (!pctStr.isEmpty()) pct = Float.parseFloat(pctStr);
                    }
                } catch (Exception ignored) {
                }

                // Resilient fallback: scan row for 2 sequential integers where first <= second
                if (total <= 0) {
                    for (int c = 3; c < cells.size() - 1; c++) {
                        try {
                            String t1 = cells.get(c).text().replaceAll("[^0-9]", "").trim();
                            String t2 = cells.get(c + 1).text().replaceAll("[^0-9]", "").trim();
                            if (!t1.isEmpty() && !t2.isEmpty()) {
                                int val1 = Integer.parseInt(t1);
                                int val2 = Integer.parseInt(t2);
                                if (val2 > 0 && val1 <= val2 && val2 < 200) {
                                    attended = val1;
                                    total = val2;
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }

                if (pct <= 0f && total > 0) {
                    pct = (float) Math.round((attended * 10000.0) / total) / 100.0f;
                }

                if (total > 0) {
                    totalAttended += attended;
                    totalClasses += total;

                    SemesterSummary.SubjectStat existing = null;
                    for (SemesterSummary.SubjectStat s : summary.subjects) {
                        if (s.courseCode != null && s.courseCode.equalsIgnoreCase(code)) {
                            existing = s;
                            break;
                        }
                    }

                    if (existing != null) {
                        existing.attended = attended;
                        existing.total = total;
                        existing.attendancePercentage = pct;
                        if (existing.courseTitle == null || existing.courseTitle.isEmpty() || existing.courseTitle.equalsIgnoreCase(code)) {
                            existing.courseTitle = title.isEmpty() ? code : title;
                        }
                        if (existing.courseType == null || existing.courseType.isEmpty()) {
                            existing.courseType = type;
                        }
                    } else {
                        SemesterSummary.SubjectStat stat = new SemesterSummary.SubjectStat();
                        stat.courseCode = code;
                        stat.courseTitle = title.isEmpty() ? code : title;
                        stat.courseType = type;
                        stat.attended = attended;
                        stat.total = total;
                        stat.attendancePercentage = pct;
                        summary.subjects.add(stat);
                    }
                }
            }

            summary.attendedClasses = totalAttended;
            summary.totalClasses = totalClasses;
            summary.courseCount = summary.subjects.size();
            summary.attendancePercentage = totalClasses > 0 ?
                    (float) Math.round((totalAttended * 10000.0) / totalClasses) / 100.0f : 0f;

            Log.d(TAG, "Parsed attendance for " + summary.semesterName + ": " + summary.subjects.size() + " courses, " +
                    totalAttended + "/" + totalClasses + " (" + summary.attendancePercentage + "%)");

        } catch (Exception e) {
            Log.e(TAG, "parseAttendanceIntoSummary error: " + e.getMessage());
        }
    }

    /**
     * Parses marks HTML table and merges subject marks into SemesterSummary.
     */
    public static void parseMarksIntoSummary(String html, SemesterSummary summary) {
        if (html == null || html.isEmpty()) return;

        try {
            Document doc = Jsoup.parse(html);
            Element table = doc.getElementById("fixedTableContainer");
            if (table != null) {
                Element innerTableInDiv = table.selectFirst("table");
                if (innerTableInDiv != null) {
                    table = innerTableInDiv;
                }
            }
            if (table == null) {
                table = doc.selectFirst("table");
            }

            if (table == null) return;

            Elements rows = table.select("> tbody > tr");
            if (rows.isEmpty()) rows = table.select("> tr");
            if (rows.isEmpty()) rows = table.select("tr");
            if (rows.size() < 2) return;

            double highestMarks = -1;
            String highestCourse = "";
            String highestCourseCode = "";

            double lowestMarks = 9999;
            String lowestCourse = "";
            String lowestCourseCode = "";

            double marksSum = 0;
            int evaluatedCourses = 0;

            for (int i = 1; i < rows.size(); i++) {
                Element row = rows.get(i);
                Elements cells = row.select("> td, > th");
                if (cells.isEmpty()) continue;

                // Identify Course Code
                String courseCode = "";
                String courseTitle = "";
                for (Element cell : cells) {
                    String text = cell.text().trim();
                    if (text.matches("^[A-Za-z]{3,4}\\d{3,4}[A-Za-z]?$")) {
                        courseCode = text;
                    } else if (text.length() > 5 && !text.matches("^[0-9]+$") && courseTitle.isEmpty()) {
                        courseTitle = text;
                    }
                }

                if (courseCode.isEmpty()) continue;

                // Nested marks table
                Element innerTable = row.selectFirst("table");
                if (innerTable == null && i + 1 < rows.size()) {
                    innerTable = rows.get(i + 1).selectFirst("table");
                    if (innerTable != null) {
                        i++; // Advance outer iterator
                    }
                }

                if (innerTable != null) {
                    Elements innerRows = innerTable.select("tr");
                    if (innerRows.size() >= 2) {
                        Elements innerHeadings = innerRows.get(0).select("th, td");
                        int scoredWeightIdx = -1;
                        int maxWeightIdx = -1;
                        int scoredMarkIdx = -1;
                        int maxMarkIdx = -1;

                        for (int h = 0; h < innerHeadings.size(); h++) {
                            String hText = innerHeadings.get(h).text().toLowerCase(Locale.US);
                            if (hText.contains("weightage") && (hText.contains("scored") || hText.contains("mark"))) scoredWeightIdx = h;
                            else if (hText.contains("weightage") && (hText.contains("max") || hText.contains("total"))) maxWeightIdx = h;
                            else if (hText.contains("scored") || hText.contains("mark scored")) scoredMarkIdx = h;
                            else if (hText.contains("max") || hText.contains("maximum")) maxMarkIdx = h;
                        }

                        double totalScored = 0;
                        double totalMax = 0;

                        for (int r = 1; r < innerRows.size(); r++) {
                            Elements inCells = innerRows.get(r).select("td");
                            if (inCells.size() >= 2) {
                                try {
                                    double scored = 0;
                                    double max = 0;

                                    if (scoredWeightIdx >= 0 && maxWeightIdx >= 0 &&
                                            scoredWeightIdx < inCells.size() && maxWeightIdx < inCells.size()) {
                                        scored = Double.parseDouble(inCells.get(scoredWeightIdx).text().replaceAll("[^0-9.]", ""));
                                        max = Double.parseDouble(inCells.get(maxWeightIdx).text().replaceAll("[^0-9.]", ""));
                                    } else if (scoredMarkIdx >= 0 && maxMarkIdx >= 0 &&
                                            scoredMarkIdx < inCells.size() && maxMarkIdx < inCells.size()) {
                                        scored = Double.parseDouble(inCells.get(scoredMarkIdx).text().replaceAll("[^0-9.]", ""));
                                        max = Double.parseDouble(inCells.get(maxMarkIdx).text().replaceAll("[^0-9.]", ""));
                                    } else if (inCells.size() >= 3) {
                                        scored = Double.parseDouble(inCells.get(inCells.size() - 2).text().replaceAll("[^0-9.]", ""));
                                        max = Double.parseDouble(inCells.get(inCells.size() - 1).text().replaceAll("[^0-9.]", ""));
                                    }

                                    if (max > 0) {
                                        totalScored += scored;
                                        totalMax += max;
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }

                        if (totalMax > 0) {
                            double percentage = Math.round(((totalScored * 100.0) / totalMax) * 10.0) / 10.0;
                            marksSum += percentage;
                            evaluatedCourses++;

                            if (percentage > highestMarks) {
                                highestMarks = percentage;
                                highestCourse = courseTitle.isEmpty() ? courseCode : courseTitle;
                                highestCourseCode = courseCode;
                            }

                            if (percentage < lowestMarks) {
                                lowestMarks = percentage;
                                lowestCourse = courseTitle.isEmpty() ? courseCode : courseTitle;
                                lowestCourseCode = courseCode;
                            }

                            // Update in summary subjects
                            boolean matched = false;
                            for (SemesterSummary.SubjectStat sub : summary.subjects) {
                                if (sub.courseCode != null && sub.courseCode.equalsIgnoreCase(courseCode)) {
                                    sub.marksScore = totalScored;
                                    sub.marksMax = totalMax;
                                    sub.marksPercentage = (float) percentage;
                                    matched = true;
                                    break;
                                }
                            }

                            if (!matched) {
                                SemesterSummary.SubjectStat newSub = new SemesterSummary.SubjectStat(
                                        courseCode,
                                        courseTitle.isEmpty() ? courseCode : courseTitle,
                                        0f,
                                        (float) percentage
                                );
                                newSub.marksScore = totalScored;
                                newSub.marksMax = totalMax;
                                summary.subjects.add(newSub);
                            }
                        }
                    }
                }
            }

            if (evaluatedCourses > 0) {
                summary.averageMarksPercentage = (float) (Math.round((marksSum / evaluatedCourses) * 10.0) / 10.0);
            }

            if (highestMarks >= 0) {
                summary.highestSubjectScore = highestMarks;
                summary.highestSubjectName = highestCourse;
                summary.highestSubjectCode = highestCourseCode;
            }

            if (lowestMarks < 9000) {
                summary.lowestSubjectScore = lowestMarks;
                summary.lowestSubjectName = lowestCourse;
                summary.lowestSubjectCode = lowestCourseCode;
            }

            Log.d(TAG, "Parsed marks for " + summary.semesterName + ": evaluated " + evaluatedCourses + " courses, avg=" + summary.averageMarksPercentage + "%");

        } catch (Exception e) {
            Log.e(TAG, "parseMarksIntoSummary error: " + e.getMessage());
        }
    }
}
