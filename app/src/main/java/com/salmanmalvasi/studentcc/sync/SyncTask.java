package com.salmanmalvasi.studentcc.sync;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Abstract task in the synchronization dependency graph.
 */
public abstract class SyncTask {
    private final SyncTaskId id;
    private final Set<SyncTaskId> prerequisites;
    private final boolean safeToRetry;
    private final int maxRetries;
    private final long timeoutMs;

    public SyncTask(SyncTaskId id, Set<SyncTaskId> prerequisites, boolean safeToRetry, int maxRetries, long timeoutMs) {
        this.id = id;
        this.prerequisites = prerequisites != null ? Collections.unmodifiableSet(new HashSet<>(prerequisites)) : Collections.emptySet();
        this.safeToRetry = safeToRetry;
        this.maxRetries = maxRetries;
        this.timeoutMs = timeoutMs;
    }

    public SyncTask(SyncTaskId id, Set<SyncTaskId> prerequisites) {
        this(id, prerequisites, false, 0, 15000); // 15 second default timeout
    }

    public SyncTaskId getId() {
        return id;
    }

    public Set<SyncTaskId> getPrerequisites() {
        return prerequisites;
    }

    public boolean isSafeToRetry() {
        return safeToRetry;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public boolean isEssential() {
        return true;
    }

    /**
     * Executes the task operation using the shared sync context.
     */
    public abstract SyncTaskResult execute(SyncContext context) throws Exception;
}
