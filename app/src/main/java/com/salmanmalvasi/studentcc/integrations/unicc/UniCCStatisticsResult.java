package com.salmanmalvasi.studentcc.integrations.unicc;

/** A displayable fetch outcome. Failures never interrupt StudentCC's own marks loading. */
public final class UniCCStatisticsResult {
    public enum Status { AVAILABLE, NO_DATA, UNAVAILABLE, INVALID_REQUEST }
    public enum Source { NETWORK, CACHE, STALE_CACHE, NONE }

    public final Status status;
    public final Source source;
    public final UniCCClassStatistics statistics;
    public final long fetchedAtMillis;
    public final String message;

    private UniCCStatisticsResult(Status status, Source source, UniCCClassStatistics statistics,
                                  long fetchedAtMillis, String message) {
        this.status = status;
        this.source = source;
        this.statistics = statistics;
        this.fetchedAtMillis = fetchedAtMillis;
        this.message = message;
    }

    static UniCCStatisticsResult available(UniCCClassStatistics statistics, Source source, long at) {
        return new UniCCStatisticsResult(Status.AVAILABLE, source, statistics, at,
                source == Source.STALE_CACHE
                        ? "UniCC is unavailable. Showing previously downloaded class statistics."
                        : "Class statistics from UniCC's participating students.");
    }

    static UniCCStatisticsResult unavailable(Status status, String message) {
        return new UniCCStatisticsResult(status, Source.NONE, null, 0, message);
    }

    public boolean hasStatistics() { return statistics != null && status == Status.AVAILABLE; }
    public boolean isStale() { return source == Source.STALE_CACHE; }
}
