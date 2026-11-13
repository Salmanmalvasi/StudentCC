package com.salmanmalvasi.studentcc.integrations.studentcc;

public final class StudentCCStatisticsResult {
    public enum Status { AVAILABLE, INSUFFICIENT_DATA, NOT_CONFIGURED, UNAVAILABLE, INVALID_REQUEST }
    public enum Source { NETWORK, CACHE, STALE_CACHE, NONE }
    public final Status status;
    public final Source source;
    public final StudentCCClassStatistics statistics;
    public final long fetchedAtMillis;
    public final String message;

    private StudentCCStatisticsResult(Status status, Source source, StudentCCClassStatistics statistics, long at, String message) {
        this.status = status; this.source = source; this.statistics = statistics;
        this.fetchedAtMillis = at; this.message = message;
    }
    static StudentCCStatisticsResult data(StudentCCClassStatistics statistics, Source source, long at) {
        return new StudentCCStatisticsResult(statistics.hasValues() ? Status.AVAILABLE : Status.INSUFFICIENT_DATA,
                source, statistics, at, statistics.hasValues() ? "Statistics from contributing StudentCC students."
                : "No StudentCC contributions are available for this class yet.");
    }
    static StudentCCStatisticsResult unavailable(Status status, String message) { return new StudentCCStatisticsResult(status, Source.NONE, null, 0, message); }
    public boolean hasStatistics() { return status == Status.AVAILABLE && statistics != null; }
    public boolean isStale() { return source == Source.STALE_CACHE; }
}
