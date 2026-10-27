package com.salmanmalvasi.studentcc.sync;

/**
 * Result returned by a synchronization task execution.
 */
public class SyncTaskResult {
    public enum Status {
        SUCCESS,
        FAILURE,
        SKIPPED,
        CANCELLED
    }

    public final Status status;
    public final String errorMessage;
    public final int statusCode;
    public final long durationMs;

    public SyncTaskResult(Status status, String errorMessage, int statusCode, long durationMs) {
        this.status = status;
        this.errorMessage = errorMessage;
        this.statusCode = statusCode;
        this.durationMs = durationMs;
    }

    public static SyncTaskResult success(long durationMs) {
        return new SyncTaskResult(Status.SUCCESS, null, 200, durationMs);
    }

    public static SyncTaskResult failure(String errorMessage, int statusCode, long durationMs) {
        return new SyncTaskResult(Status.FAILURE, errorMessage, statusCode, durationMs);
    }

    public static SyncTaskResult skipped(String reason) {
        return new SyncTaskResult(Status.SKIPPED, reason, 0, 0);
    }

    public static SyncTaskResult cancelled() {
        return new SyncTaskResult(Status.CANCELLED, "Operation cancelled", 0, 0);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
