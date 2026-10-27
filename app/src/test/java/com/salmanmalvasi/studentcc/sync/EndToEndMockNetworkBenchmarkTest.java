package com.salmanmalvasi.studentcc.sync;

import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.AttendanceRecord;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Slot;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Category B Benchmark: End-to-end synchronization over MockWebServer network,
 * executing real HTTP POST requests, real JSoup HTML parsing, and relational staging validation.
 */
public class EndToEndMockNetworkBenchmarkTest {

    private MockWebServer server;
    private VTOPOkHttpNetworkClient client;
    private static final int NUM_TRIALS = 20;

    public static class CategoryBMetrics {
        public final String name;
        public final List<Long> durations = new ArrayList<>();
        public int peakConcurrency;
        public int errorCount;
        public int validationViolations;

        public CategoryBMetrics(String name) {
            this.name = name;
        }

        public long getMedian() {
            List<Long> sorted = new ArrayList<>(durations);
            Collections.sort(sorted);
            return sorted.get(sorted.size() / 2);
        }

        public long getP95() {
            List<Long> sorted = new ArrayList<>(durations);
            Collections.sort(sorted);
            int idx = (int) Math.ceil(0.95 * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(sorted.size() - 1, idx)));
        }

        public double getMean() {
            long sum = 0;
            for (long d : durations) sum += d;
            return (double) sum / durations.size();
        }
    }

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();

        // Realistic dispatcher serving VTOP HTML fixtures with 10ms simulated server latency
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() != null ? request.getPath() : "";
                try {
                    Thread.sleep(10); // 10ms simulated upstream server processing delay
                } catch (InterruptedException ignored) {
                }

                if (path.contains("StudentTimeTableChn")) {
                    return new MockResponse().setResponseCode(200)
                            .setBody("<html><body><select id=\"semesterSubId\"><option value=\"CH2024251\">Winter 2024</option></select></body></html>");
                } else if (path.contains("processViewTimeTable")) {
                    return new MockResponse().setResponseCode(200)
                            .setBody("<html><body><div id=\"studentDetailsList\"><table>" +
                                    "<tr><th>1</th><th>Course</th><th>L T P J C</th><th>Slot</th><th>Venue</th><th>Faculty</th><th>Status</th></tr>" +
                                    "<tr><td>1</td><td>CSE1001 - Problem Solving (Theory)</td><td>3 0 0 0 3</td><td>L1+L2</td><td>AB2-101</td><td>Dr. Smith</td><td>Regular</td></tr>" +
                                    "<tr><td>2</td><td>CSE2001 - Data Structures (Lab)</td><td>0 0 2 0 1</td><td>L31+L32</td><td>AB2-202</td><td>Dr. Jane</td><td>Regular</td></tr>" +
                                    "</table></div></body></html>");
                } else if (path.contains("processViewStudentAttendance")) {
                    return new MockResponse().setResponseCode(200)
                            .setBody("<html><body><table id=\"getStudentDetails\">" +
                                    "<tr><th>#</th><th>Code</th><th>Title</th><th>Type</th><th>Slot</th><th>D1</th><th>Attended</th><th>Total</th><th>%</th><th>D2</th><th>D3</th><th>D4</th><th>D5</th><th>View</th></tr>" +
                                    "<tr><td>1</td><td>CSE1001</td><td>Problem Solving</td><td>Theory</td><td>L1</td><td>-</td><td>20</td><td>22</td><td>90%</td><td>-</td><td>-</td><td>-</td><td>-</td><td><a onclick=\"processViewAttendanceDetail('C101','L1')\">View</a></td></tr>" +
                                    "<tr><td>2</td><td>CSE2001</td><td>Data Structures</td><td>Lab</td><td>L31</td><td>-</td><td>10</td><td>10</td><td>100%</td><td>-</td><td>-</td><td>-</td><td>-</td><td><a onclick=\"processViewAttendanceDetail('C201','L31')\">View</a></td></tr>" +
                                    "</table></body></html>");
                } else if (path.contains("processViewAttendanceDetail")) {
                    return new MockResponse().setResponseCode(200)
                            .setBody("<html><body><table class=\"table\">" +
                                    "<tr><th>#</th><th>Date</th><th>Day</th><th>Slot</th><th>Status</th></tr>" +
                                    "<tr><td>1</td><td>12-Aug-2024</td><td>Mon</td><td>L1</td><td>Present</td></tr>" +
                                    "<tr><td>2</td><td>14-Aug-2024</td><td>Wed</td><td>L1</td><td>Present</td></tr>" +
                                    "</table></body></html>");
                } else if (path.contains("processViewCalendar")) {
                    return new MockResponse().setResponseCode(200)
                            .setBody("<html><body><table class=\"calendar-table\"><tbody><tr><td>" +
                                    "<span>15</span><span>Independence Day (Holiday)</span>" +
                                    "</td></tr></tbody></table></body></html>");
                }
                return new MockResponse().setResponseCode(200).setBody("<html><body>OK</body></html>");
            }
        });

        server.start();
        client = new VTOPOkHttpNetworkClient(server.url("/").toString());
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void runCategoryBComparativeBenchmarks() {
        CategoryBMetrics mSequential = runBenchmarkSuite("V0 (Sequential C=1)", 1);
        CategoryBMetrics mFixedC2 = runBenchmarkSuite("V1 (Fixed Concurrency C=2)", 2);
        CategoryBMetrics mFixedC3 = runBenchmarkSuite("V1 (Fixed Concurrency C=3)", 3);
        CategoryBMetrics mAdaptive = runAdaptiveBenchmarkSuite("V2 (Adaptive Concurrency)");

        System.out.println("\n========== CATEGORY B (MOCK NETWORK + PARSING + VALIDATION) ==========");
        printMetrics(mSequential);
        printMetrics(mFixedC2);
        printMetrics(mFixedC3);
        printMetrics(mAdaptive);
        System.out.println("=======================================================================\n");

        Assert.assertEquals(0, mSequential.errorCount);
        Assert.assertEquals(0, mFixedC2.errorCount);
        Assert.assertEquals(0, mFixedC3.errorCount);
        Assert.assertEquals(0, mAdaptive.errorCount);

        Assert.assertEquals(0, mSequential.validationViolations);
        Assert.assertEquals(0, mFixedC2.validationViolations);
        Assert.assertEquals(0, mFixedC3.validationViolations);
        Assert.assertEquals(0, mAdaptive.validationViolations);

        Assert.assertTrue("V1 (C=2) must be faster than or equal to sequential",
                mFixedC2.getMedian() <= mSequential.getMedian());
    }

    private CategoryBMetrics runBenchmarkSuite(String name, int concurrency) {
        CategoryBMetrics metrics = new CategoryBMetrics(name);
        AtomicInteger active = new AtomicInteger(0);
        AtomicInteger peak = new AtomicInteger(0);

        // Warmup runs
        for (int w = 0; w < 2; w++) {
            SyncContext warmupCtx = new SyncContext(client.getOkHttpClient(), "CH2024251", null);
            warmupCtx.setSessionTokens("mock-csrf", "21BCE1001", "JSESSIONID=MOCK123");
            List<SyncTask> warmupTasks = createRealHttpTasks(new AtomicInteger(0), new AtomicInteger(0));
            new DeterministicTaskScheduler(concurrency).execute(warmupTasks, warmupCtx);
        }

        for (int i = 0; i < NUM_TRIALS; i++) {
            SyncContext context = new SyncContext(client.getOkHttpClient(), "CH2024251", null);
            context.setSessionTokens("mock-csrf", "21BCE1001", "JSESSIONID=MOCK123");
            List<SyncTask> tasks = createRealHttpTasks(active, peak);
            DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(concurrency);

            long start = System.currentTimeMillis();
            DeterministicTaskScheduler.SchedulerResult result = scheduler.execute(tasks, context);
            long dur = System.currentTimeMillis() - start;

            metrics.durations.add(dur);
            if (!result.overallSuccess) {
                metrics.errorCount++;
            }

            // Verify relational consistency of staged data
            String valErr = context.getStagedData().validateConsistency();
            if (valErr != null) {
                metrics.validationViolations++;
            }
        }
        metrics.peakConcurrency = peak.get();
        return metrics;
    }

    private CategoryBMetrics runAdaptiveBenchmarkSuite(String name) {
        CategoryBMetrics metrics = new CategoryBMetrics(name);
        AtomicInteger active = new AtomicInteger(0);
        AtomicInteger peak = new AtomicInteger(0);
        AdaptiveConcurrencyController controller = new AdaptiveConcurrencyController();

        // Warmup runs
        for (int w = 0; w < 2; w++) {
            SyncContext warmupCtx = new SyncContext(client.getOkHttpClient(), "CH2024251", null);
            warmupCtx.setSessionTokens("mock-csrf", "21BCE1001", "JSESSIONID=MOCK123");
            List<SyncTask> warmupTasks = createRealHttpTasks(new AtomicInteger(0), new AtomicInteger(0));
            new DeterministicTaskScheduler(2).execute(warmupTasks, warmupCtx);
        }

        for (int i = 0; i < NUM_TRIALS; i++) {
            SyncContext context = new SyncContext(client.getOkHttpClient(), "CH2024251", null);
            context.setSessionTokens("mock-csrf", "21BCE1001", "JSESSIONID=MOCK123");
            List<SyncTask> tasks = createRealHttpTasks(active, peak);
            int currentLimit = controller.getCurrentConcurrency();
            DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(currentLimit);

            long start = System.currentTimeMillis();
            DeterministicTaskScheduler.SchedulerResult result = scheduler.execute(tasks, context);
            long dur = System.currentTimeMillis() - start;

            metrics.durations.add(dur);
            if (!result.overallSuccess) {
                metrics.errorCount++;
                controller.recordFailure(500, false);
            } else {
                controller.recordSuccess(dur / tasks.size());
            }

            String valErr = context.getStagedData().validateConsistency();
            if (valErr != null) {
                metrics.validationViolations++;
            }
        }
        metrics.peakConcurrency = peak.get();
        return metrics;
    }

    private List<SyncTask> createRealHttpTasks(AtomicInteger active, AtomicInteger peak) {
        List<SyncTask> tasks = new ArrayList<>();

        // 1. Semesters Discovery Task
        tasks.add(new TrackedHttpTask(SyncTaskId.SEMESTERS, Collections.emptySet(), active, peak) {
            @Override
            public SyncTaskResult executeInternal(SyncContext ctx) throws Exception {
                VTOPOkHttpNetworkClient.HttpResponse res = client.post("/academics/common/StudentTimeTableChn", new HashMap<>(), ctx.getCookieHeader());
                Map<String, String> sems = VTOPHtmlParsers.parseSemesters(res.body);
                if (sems.isEmpty()) return SyncTaskResult.failure("No semesters found", res.code, 10);
                return SyncTaskResult.success(10);
            }
        });

        // 2. Courses Catalog Task (depends on Semesters)
        tasks.add(new TrackedHttpTask(SyncTaskId.COURSES, setOf(SyncTaskId.SEMESTERS), active, peak) {
            @Override
            public SyncTaskResult executeInternal(SyncContext ctx) throws Exception {
                VTOPOkHttpNetworkClient.HttpResponse res = client.post("/processViewTimeTable", new HashMap<>(), ctx.getCookieHeader());
                VTOPHtmlParsers.parseCoursesAndSlots(res.body, ctx.getStagedData().courses, ctx.getStagedData().slots);
                return SyncTaskResult.success(10);
            }
        });

        // 3. Attendance Summary Task (depends on Courses)
        tasks.add(new TrackedHttpTask(SyncTaskId.ATTENDANCE_SUMMARY, setOf(SyncTaskId.COURSES), active, peak) {
            @Override
            public SyncTaskResult executeInternal(SyncContext ctx) throws Exception {
                VTOPOkHttpNetworkClient.HttpResponse res = client.post("/processViewStudentAttendance", new HashMap<>(), ctx.getCookieHeader());
                Map<String, Integer> courseIdByCode = new HashMap<>();
                for (Course c : ctx.getStagedData().courses) courseIdByCode.put(c.code, c.id);
                List<Attendance> atts = VTOPHtmlParsers.parseAttendanceSummary(res.body, courseIdByCode);
                ctx.getStagedData().attendanceSummaries.addAll(atts);
                return SyncTaskResult.success(10);
            }
        });

        // 4. Detailed Attendance Task (depends on Attendance Summary)
        tasks.add(new TrackedHttpTask(SyncTaskId.DETAILED_ATTENDANCE, setOf(SyncTaskId.ATTENDANCE_SUMMARY), active, peak) {
            @Override
            public SyncTaskResult executeInternal(SyncContext ctx) throws Exception {
                // Fetch details for course 1
                VTOPOkHttpNetworkClient.HttpResponse res1 = client.post("/processViewAttendanceDetail?classId=C101", new HashMap<>(), ctx.getCookieHeader());
                List<AttendanceRecord> recs1 = VTOPHtmlParsers.parseDetailedAttendanceRecords(res1.body, 1, "L1");
                ctx.getStagedData().attendanceRecords.addAll(recs1);

                // Fetch details for course 2
                VTOPOkHttpNetworkClient.HttpResponse res2 = client.post("/processViewAttendanceDetail?classId=C201", new HashMap<>(), ctx.getCookieHeader());
                List<AttendanceRecord> recs2 = VTOPHtmlParsers.parseDetailedAttendanceRecords(res2.body, 2, "L31");
                ctx.getStagedData().attendanceRecords.addAll(recs2);

                return SyncTaskResult.success(20);
            }
        });

        // 5. Calendar Task (depends only on Semesters)
        tasks.add(new TrackedHttpTask(SyncTaskId.CALENDAR, setOf(SyncTaskId.SEMESTERS), active, peak) {
            @Override
            public SyncTaskResult executeInternal(SyncContext ctx) throws Exception {
                VTOPOkHttpNetworkClient.HttpResponse res = client.post("/processViewCalendar", new HashMap<>(), ctx.getCookieHeader());
                ctx.getStagedData().calendarEvents.addAll(VTOPHtmlParsers.parseCalendarEvents(res.body, "AUG-2024"));
                return SyncTaskResult.success(10);
            }
        });

        // 6. Profile Task (independent)
        tasks.add(new TrackedHttpTask(SyncTaskId.PROFILE, Collections.emptySet(), active, peak) {
            @Override
            public SyncTaskResult executeInternal(SyncContext ctx) throws Exception {
                VTOPOkHttpNetworkClient.HttpResponse res = client.post("/studentsRecord/StudentProfileAllView", new HashMap<>(), ctx.getCookieHeader());
                ctx.getStagedData().profileAttributes.put("name", "STUDENT TEST");
                return SyncTaskResult.success(10);
            }
        });

        return tasks;
    }

    private static Set<SyncTaskId> setOf(SyncTaskId... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private abstract static class TrackedHttpTask extends SyncTask {
        private final AtomicInteger active;
        private final AtomicInteger peak;

        public TrackedHttpTask(SyncTaskId id, Set<SyncTaskId> prereqs, AtomicInteger active, AtomicInteger peak) {
            super(id, prereqs);
            this.active = active;
            this.peak = peak;
        }

        @Override
        public SyncTaskResult execute(SyncContext context) throws Exception {
            int current = active.incrementAndGet();
            peak.updateAndGet(max -> Math.max(max, current));
            try {
                return executeInternal(context);
            } finally {
                active.decrementAndGet();
            }
        }

        protected abstract SyncTaskResult executeInternal(SyncContext context) throws Exception;
    }

    private void printMetrics(CategoryBMetrics m) {
        System.out.printf("%-30s | Median: %4d ms | p95: %4d ms | Mean: %6.1f ms | Peak C: %d | Errors: %d | Violations: %d%n",
                m.name, m.getMedian(), m.getP95(), m.getMean(), m.peakConcurrency, m.errorCount, m.validationViolations);
    }
}
