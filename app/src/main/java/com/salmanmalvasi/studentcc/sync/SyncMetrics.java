package com.salmanmalvasi.studentcc.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe timing instrumentation tracker for synchronization operations.
 * Strictly complies with privacy rules: never logs credentials, tokens, or student data.
 * Uses JVM-compatible high-resolution clock so it executes seamlessly in unit tests and on device.
 */
public class SyncMetrics {
    private static final String TAG = "SyncMetrics";

    public static class SectionMetric {
        public final String name;
        public long startTimeMs;
        public long endTimeMs;
        public long durationMs;
        public boolean success;
        public int itemCount;
        public final List<Long> subItemDurations = Collections.synchronizedList(new ArrayList<>());

        public SectionMetric(String name) {
            this.name = name;
        }
    }

    private final long totalStartNano;
    private long totalEndNano;
    private final Map<String, SectionMetric> metrics = new LinkedHashMap<>();
    private final Map<String, Long> activeStartsNano = new ConcurrentHashMap<>();

    public SyncMetrics() {
        this.totalStartNano = System.nanoTime();
    }

    public synchronized void startSection(String sectionName) {
        if (sectionName == null) return;
        activeStartsNano.put(sectionName, System.nanoTime());
        if (!metrics.containsKey(sectionName)) {
            SectionMetric metric = new SectionMetric(sectionName);
            metric.startTimeMs = System.currentTimeMillis();
            metrics.put(sectionName, metric);
        }
    }

    public synchronized void endSection(String sectionName, boolean success) {
        if (sectionName == null) return;
        Long startNano = activeStartsNano.remove(sectionName);
        long nowNano = System.nanoTime();
        long durationMs = (startNano != null) ? (nowNano - startNano) / 1_000_000L : 0L;

        SectionMetric metric = metrics.get(sectionName);
        if (metric != null) {
            metric.endTimeMs = System.currentTimeMillis();
            metric.durationMs = durationMs;
            metric.success = success;
        }
    }

    public synchronized void recordItem(String sectionName, long durationMs) {
        SectionMetric metric = metrics.get(sectionName);
        if (metric != null) {
            metric.itemCount++;
            metric.subItemDurations.add(durationMs);
        }
    }

    public synchronized void finish() {
        this.totalEndNano = System.nanoTime();
    }

    public synchronized long getTotalDurationMs() {
        if (totalEndNano == 0) {
            return (System.nanoTime() - totalStartNano) / 1_000_000L;
        }
        return (totalEndNano - totalStartNano) / 1_000_000L;
    }

    public synchronized Map<String, SectionMetric> getMetrics() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
    }

    public synchronized String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SyncMetrics Execution Summary ===\n");
        sb.append(String.format("Total Elapsed Time: %d ms\n", getTotalDurationMs()));
        for (Map.Entry<String, SectionMetric> entry : metrics.entrySet()) {
            SectionMetric m = entry.getValue();
            sb.append(String.format(" - Section [%s]: %d ms (success=%b, items=%d)\n",
                    m.name, m.durationMs, m.success, m.itemCount));
        }
        sb.append("=====================================");
        return sb.toString();
    }
}
