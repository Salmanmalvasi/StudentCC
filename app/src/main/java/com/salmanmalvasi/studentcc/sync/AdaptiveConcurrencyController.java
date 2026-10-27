package com.salmanmalvasi.studentcc.sync;

import java.util.LinkedList;

/**
 * Adaptive concurrency controller that safely adjusts concurrency limits (between 1 and 4)
 * based on observed network latencies, server response codes, and throttling signals.
 */
public class AdaptiveConcurrencyController {
    public static final int MIN_CONCURRENCY = 1;
    public static final int MAX_CONCURRENCY = 4;
    public static final int DEFAULT_INITIAL_CONCURRENCY = 2;

    private final int minConcurrency;
    private final int maxConcurrency;
    private int currentConcurrency;

    private final int successWindowSize;
    private final long latencyThresholdMs;
    private final LinkedList<Long> recentLatencies = new LinkedList<>();
    private int consecutiveFastSuccesses = 0;
    private int consecutiveFailures = 0;

    public AdaptiveConcurrencyController(int initialConcurrency, int minConcurrency, int maxConcurrency,
                                         int successWindowSize, long latencyThresholdMs) {
        this.minConcurrency = minConcurrency;
        this.maxConcurrency = maxConcurrency;
        this.currentConcurrency = Math.max(minConcurrency, Math.min(maxConcurrency, initialConcurrency));
        this.successWindowSize = successWindowSize;
        this.latencyThresholdMs = latencyThresholdMs;
    }

    public AdaptiveConcurrencyController() {
        this(DEFAULT_INITIAL_CONCURRENCY, MIN_CONCURRENCY, MAX_CONCURRENCY, 3, 1500L);
    }

    public synchronized int getCurrentConcurrency() {
        return currentConcurrency;
    }

    /**
     * Records a successful request and its response duration.
     */
    public synchronized void recordSuccess(long durationMs) {
        consecutiveFailures = 0;
        recentLatencies.add(durationMs);
        if (recentLatencies.size() > successWindowSize) {
            recentLatencies.removeFirst();
        }

        if (durationMs < latencyThresholdMs) {
            consecutiveFastSuccesses++;
            // If we have enough consecutive fast successes and have room to scale up
            if (consecutiveFastSuccesses >= successWindowSize && currentConcurrency < maxConcurrency) {
                currentConcurrency++;
                consecutiveFastSuccesses = 0; // Reset counter for next tier
            }
        } else {
            // Slower than threshold -> do not scale up, reset fast streak
            consecutiveFastSuccesses = 0;
        }
    }

    /**
     * Records a request failure, timeout, or server error.
     */
    public synchronized void recordFailure(int statusCode, boolean isTimeout) {
        consecutiveFastSuccesses = 0;
        consecutiveFailures++;

        // On throttling (429) or multiple consecutive failures, immediately back off to minimum
        if (statusCode == 429 || statusCode == 503 || isTimeout || consecutiveFailures >= 2) {
            currentConcurrency = minConcurrency;
            consecutiveFailures = 0;
        } else if (currentConcurrency > minConcurrency) {
            currentConcurrency--;
        }
    }
}
