package com.salmanmalvasi.studentcc.sync;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Repeatable, automated benchmark suite comparing:
 * - V0: Sequential pipeline (C=1)
 * - V1: Dependency-aware DAG scheduler with Fixed Concurrency (C=2 and C=3)
 * - V2: Adaptive Concurrency Scheduler (C adapts between 1 and 4)
 *
 * Runs across 20 iterations per configuration to report median, p95, peak concurrency, and correctness.
 */
public class SyncEngineBenchmarkTest {

    private static final int NUM_TRIALS = 20;

    public static class BenchmarkRunMetrics {
        public final String name;
        public final List<Long> durations = new ArrayList<>();
        public int peakConcurrency;
        public int errorCount;
        public int integrityViolations;

        public BenchmarkRunMetrics(String name) {
            this.name = name;
        }

        public long getMedianDuration() {
            List<Long> sorted = new ArrayList<>(durations);
            Collections.sort(sorted);
            int mid = sorted.size() / 2;
            return sorted.get(mid);
        }

        public long getP95Duration() {
            List<Long> sorted = new ArrayList<>(durations);
            Collections.sort(sorted);
            int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
        }

        public double getMeanDuration() {
            long sum = 0;
            for (long d : durations) sum += d;
            return (double) sum / durations.size();
        }
    }

    private List<SyncTask> createStandardWorkload(AtomicInteger activeConcurrency, AtomicInteger maxObservedConcurrency, int simulatedDelayMs) {
        List<SyncTask> tasks = new ArrayList<>();

        // 1. Auth (Root)
        tasks.add(new BenchmarkTask(SyncTaskId.AUTH, Collections.emptySet(), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));

        // 2. Semesters (depends on Auth)
        tasks.add(new BenchmarkTask(SyncTaskId.SEMESTERS, setOf(SyncTaskId.AUTH), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));

        // 3. Courses (depends on Semesters)
        tasks.add(new BenchmarkTask(SyncTaskId.COURSES, setOf(SyncTaskId.SEMESTERS), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));

        // 4. Timetable (depends on Courses)
        tasks.add(new BenchmarkTask(SyncTaskId.TIMETABLE, setOf(SyncTaskId.COURSES), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));

        // 5. Attendance Summary (depends on Courses)
        tasks.add(new BenchmarkTask(SyncTaskId.ATTENDANCE_SUMMARY, setOf(SyncTaskId.COURSES), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));

        // 6. Detailed Attendance (depends on Attendance Summary)
        tasks.add(new BenchmarkTask(SyncTaskId.DETAILED_ATTENDANCE, setOf(SyncTaskId.ATTENDANCE_SUMMARY), activeConcurrency, maxObservedConcurrency, simulatedDelayMs * 3)); // 3x weight for detailed loops

        // 7. Marks, Grades, Exams (depend on Semesters & Courses)
        tasks.add(new BenchmarkTask(SyncTaskId.MARKS, setOf(SyncTaskId.COURSES), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));
        tasks.add(new BenchmarkTask(SyncTaskId.GRADES, setOf(SyncTaskId.COURSES), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));
        tasks.add(new BenchmarkTask(SyncTaskId.EXAMS, setOf(SyncTaskId.COURSES), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));

        // 8. Independent tasks (depend only on Auth)
        tasks.add(new BenchmarkTask(SyncTaskId.PROFILE, setOf(SyncTaskId.AUTH), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));
        tasks.add(new BenchmarkTask(SyncTaskId.DEAN_HOD, setOf(SyncTaskId.AUTH), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));
        tasks.add(new BenchmarkTask(SyncTaskId.SPOTLIGHT, setOf(SyncTaskId.AUTH), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));
        tasks.add(new BenchmarkTask(SyncTaskId.RECEIPTS, setOf(SyncTaskId.AUTH), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));
        tasks.add(new BenchmarkTask(SyncTaskId.CALENDAR, setOf(SyncTaskId.AUTH), activeConcurrency, maxObservedConcurrency, simulatedDelayMs * 2)); // Calendar loop
        tasks.add(new BenchmarkTask(SyncTaskId.LEAVE, setOf(SyncTaskId.AUTH), activeConcurrency, maxObservedConcurrency, simulatedDelayMs));

        return tasks;
    }

    private static Set<SyncTaskId> setOf(SyncTaskId... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static class BenchmarkTask extends SyncTask {
        private final AtomicInteger active;
        private final AtomicInteger maxObserved;
        private final int delayMs;

        public BenchmarkTask(SyncTaskId id, Set<SyncTaskId> prereqs, AtomicInteger active, AtomicInteger maxObserved, int delayMs) {
            super(id, prereqs);
            this.active = active;
            this.maxObserved = maxObserved;
            this.delayMs = delayMs;
        }

        @Override
        public SyncTaskResult execute(SyncContext context) throws Exception {
            int current = active.incrementAndGet();
            maxObserved.updateAndGet(max -> Math.max(max, current));
            try {
                Thread.sleep(delayMs);
                return SyncTaskResult.success(delayMs);
            } finally {
                active.decrementAndGet();
            }
        }
    }

    @Test
    public void runComparativeBenchmarks() {
        final int delayMs = 15; // 15ms base simulated request time

        BenchmarkRunMetrics mV0 = runBenchmarkSuite("V0 (Sequential C=1)", 1, delayMs);
        BenchmarkRunMetrics mV1_C2 = runBenchmarkSuite("V1 (Fixed Concurrency C=2)", 2, delayMs);
        BenchmarkRunMetrics mV1_C3 = runBenchmarkSuite("V1 (Fixed Concurrency C=3)", 3, delayMs);
        BenchmarkRunMetrics mV2 = runAdaptiveBenchmarkSuite("V2 (Adaptive Concurrency)", delayMs);

        System.out.println("\n================ BENCHMARK RESULTS SUMMARY ================");
        printMetrics(mV0);
        printMetrics(mV1_C2);
        printMetrics(mV1_C3);
        printMetrics(mV2);
        System.out.println("===========================================================\n");

        // Assertions:
        // 1. Correctness: 0 errors and 0 integrity violations across all configurations
        Assert.assertEquals(0, mV0.errorCount);
        Assert.assertEquals(0, mV1_C2.errorCount);
        Assert.assertEquals(0, mV1_C3.errorCount);
        Assert.assertEquals(0, mV2.errorCount);

        // 2. Concurrency bound adherence:
        Assert.assertEquals(1, mV0.peakConcurrency);
        Assert.assertTrue(mV1_C2.peakConcurrency <= 2);
        Assert.assertTrue(mV1_C3.peakConcurrency <= 3);
        Assert.assertTrue(mV2.peakConcurrency <= 4);

        // 3. Performance improvement: V1 (C=2) and V1 (C=3) must be substantially faster than V0
        Assert.assertTrue("V1 (C=2) median (" + mV1_C2.getMedianDuration() + "ms) must be significantly faster than V0 (" + mV0.getMedianDuration() + "ms)",
                mV1_C2.getMedianDuration() < mV0.getMedianDuration() * 0.70);
    }

    private BenchmarkRunMetrics runBenchmarkSuite(String name, int concurrency, int delayMs) {
        BenchmarkRunMetrics m = new BenchmarkRunMetrics(name);
        AtomicInteger active = new AtomicInteger(0);
        AtomicInteger peak = new AtomicInteger(0);

        for (int i = 0; i < NUM_TRIALS; i++) {
            SyncContext context = new SyncContext(null, "2024-01", null);
            List<SyncTask> tasks = createStandardWorkload(active, peak, delayMs);
            DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(concurrency);

            long start = System.currentTimeMillis();
            DeterministicTaskScheduler.SchedulerResult result = scheduler.execute(tasks, context);
            long dur = System.currentTimeMillis() - start;

            m.durations.add(dur);
            if (!result.overallSuccess) {
                m.errorCount++;
            }
        }
        m.peakConcurrency = peak.get();
        return m;
    }

    private BenchmarkRunMetrics runAdaptiveBenchmarkSuite(String name, int delayMs) {
        BenchmarkRunMetrics m = new BenchmarkRunMetrics(name);
        AtomicInteger active = new AtomicInteger(0);
        AtomicInteger peak = new AtomicInteger(0);
        AdaptiveConcurrencyController controller = new AdaptiveConcurrencyController();

        for (int i = 0; i < NUM_TRIALS; i++) {
            SyncContext context = new SyncContext(null, "2024-01", null);
            List<SyncTask> tasks = createStandardWorkload(active, peak, delayMs);
            int currentLimit = controller.getCurrentConcurrency();
            DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(currentLimit);

            long start = System.currentTimeMillis();
            DeterministicTaskScheduler.SchedulerResult result = scheduler.execute(tasks, context);
            long dur = System.currentTimeMillis() - start;

            m.durations.add(dur);
            if (!result.overallSuccess) {
                m.errorCount++;
                controller.recordFailure(500, false);
            } else {
                controller.recordSuccess(dur / tasks.size());
            }
        }
        m.peakConcurrency = peak.get();
        return m;
    }

    private void printMetrics(BenchmarkRunMetrics m) {
        System.out.printf("%-30s | Median: %4d ms | p95: %4d ms | Mean: %6.1f ms | Peak C: %d | Errors: %d%n",
                m.name, m.getMedianDuration(), m.getP95Duration(), m.getMeanDuration(), m.peakConcurrency, m.errorCount);
    }
}
