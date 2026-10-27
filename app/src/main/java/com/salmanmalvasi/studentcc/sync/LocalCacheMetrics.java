package com.salmanmalvasi.studentcc.sync;

import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks performance and correctness metrics for local-first screen rendering,
 * cache freshness hits/misses, request skipping, background refreshes, and validation safety.
 * Logs structured diagnostics without exposing personal academic records or credentials.
 */
public final class LocalCacheMetrics {
    private static final String TAG = "StudentCCMetrics";

    private static LocalCacheMetrics instance;

    public static synchronized LocalCacheMetrics getInstance() {
        if (instance == null) {
            instance = new LocalCacheMetrics();
        }
        return instance;
    }

    // Startup & Screen navigation latencies
    private final AtomicLong appStartupToFirstUsefulRenderMs = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> screenOpenDurations = new ConcurrentHashMap<>();

    // Freshness & Network skip counters
    private final AtomicInteger networkRequestsSkippedCount = new AtomicInteger(0);
    private final AtomicInteger cacheHitsCount = new AtomicInteger(0);
    private final AtomicInteger cacheMissesCount = new AtomicInteger(0);

    // Refresh & Integrity counters
    private final AtomicLong lastBackgroundRefreshDurationMs = new AtomicLong(0);
    private final AtomicInteger validationFailuresCount = new AtomicInteger(0);
    private final AtomicInteger datasetsRetainedOnFailureCount = new AtomicInteger(0);

    private LocalCacheMetrics() {
    }

    public void recordStartupRender(long durationMs) {
        appStartupToFirstUsefulRenderMs.set(durationMs);
        Log.i(TAG, "[Startup] App launched to first useful screen rendered in " + durationMs + "ms (local-first)");
    }

    public void recordScreenOpen(String screenName, long durationMs, boolean isWarmCache) {
        screenOpenDurations.put(screenName, durationMs);
        Log.i(TAG, "[ScreenOpen] " + screenName + " rendered in " + durationMs + "ms (warmCache=" + isWarmCache + ")");
    }

    public void recordRequestSkipped(String dataset) {
        int total = networkRequestsSkippedCount.incrementAndGet();
        cacheHitsCount.incrementAndGet();
        Log.i(TAG, "[CacheHit] Network request skipped for dataset '" + dataset + "' (Data is fresh. Total skipped: " + total + ")");
    }

    public void recordCacheMiss(String dataset) {
        cacheMissesCount.incrementAndGet();
        Log.d(TAG, "[CacheMiss] Dataset '" + dataset + "' is stale or empty; network refresh needed");
    }

    public void recordCacheRefreshSuccess(String dataset) {
        Log.i(TAG, "[CacheRefresh] Dataset '" + dataset + "' successfully validated and refreshed in cache");
    }

    public void recordCacheRefreshFailure(String dataset, String reason) {
        validationFailuresCount.incrementAndGet();
        Log.w(TAG, "[CacheRefreshFailure] Refresh failed for dataset '" + dataset + "': " + reason);
    }

    public void recordBackgroundRefreshDuration(long durationMs, boolean success) {
        lastBackgroundRefreshDurationMs.set(durationMs);
        Log.i(TAG, "[BackgroundRefresh] Completed in " + durationMs + "ms (success=" + success + ")");
    }

    public void recordValidationFailure(String dataset, String reason) {
        validationFailuresCount.incrementAndGet();
        Log.w(TAG, "[ValidationFailure] Dataset '" + dataset + "' failed validation: " + reason);
    }

    public void recordDatasetRetainedOnFailure(String dataset) {
        int count = datasetsRetainedOnFailureCount.incrementAndGet();
        Log.i(TAG, "[CacheIntegrityProtected] Non-empty cached dataset '" + dataset + "' was preserved after replacement failed validation (total preserved: " + count + ")");
    }

    public String dumpSummary() {
        int hits = cacheHitsCount.get();
        int misses = cacheMissesCount.get();
        int totalAccess = hits + misses;
        float hitRate = totalAccess > 0 ? (hits * 100.0f / totalAccess) : 100.0f;

        return String.format(
                java.util.Locale.ROOT,
                "LocalCacheMetrics [Startup: %dms, SkippedRequests: %d, HitRate: %.1f%% (%d/%d), LastRefresh: %dms, RetainedOnFail: %d, ValidationFailures: %d]",
                appStartupToFirstUsefulRenderMs.get(),
                networkRequestsSkippedCount.get(),
                hitRate, hits, totalAccess,
                lastBackgroundRefreshDurationMs.get(),
                datasetsRetainedOnFailureCount.get(),
                validationFailuresCount.get()
        );
    }
}
