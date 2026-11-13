package com.salmanmalvasi.studentcc.integrations.unicc;

/** Separate aggregate-only cache; it never reads or writes StudentCC's Room marks tables. */
interface UniCCStatisticsCache {
    final class Entry {
        final UniCCClassStatistics statistics;
        final long fetchedAtMillis;
        Entry(UniCCClassStatistics statistics, long fetchedAtMillis) {
            this.statistics = statistics;
            this.fetchedAtMillis = fetchedAtMillis;
        }
    }

    Entry read(String key);
    void write(String key, Entry entry);
    void remove(String key);
}
