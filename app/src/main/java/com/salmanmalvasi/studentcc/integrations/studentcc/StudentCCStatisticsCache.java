package com.salmanmalvasi.studentcc.integrations.studentcc;

interface StudentCCStatisticsCache {
    final class Entry {
        final StudentCCClassStatistics statistics;
        final long fetchedAtMillis;
        Entry(StudentCCClassStatistics statistics, long at) { this.statistics = statistics; this.fetchedAtMillis = at; }
    }
    Entry read(String key, StudentCCClassScope scope);
    void write(String key, Entry entry);
    void remove(String key);
    void clear();
}
