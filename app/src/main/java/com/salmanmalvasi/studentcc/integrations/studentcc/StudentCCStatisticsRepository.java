package com.salmanmalvasi.studentcc.integrations.studentcc;

import android.content.Context;
import com.salmanmalvasi.studentcc.models.Course;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Independently scoped cache and data source; UniCC statistics are never copied into this pool. */
public final class StudentCCStatisticsRepository {
    private static volatile StudentCCStatisticsRepository instance;
    interface Clock { long now(); }
    private final StudentCCStatisticsClient client;
    private final StudentCCStatisticsCache cache;
    private final Clock clock;
    private final Map<String, Flight> inFlight = new HashMap<>();
    private final Map<String, Long> versions = new HashMap<>();
    private long clearVersion;
    private static final class Flight { Single<StudentCCStatisticsResult> request; }
    public static StudentCCStatisticsRepository getInstance(Context context) {
        if (instance == null) synchronized (StudentCCStatisticsRepository.class) {
            if (instance == null) instance = new StudentCCStatisticsRepository(new StudentCCStatisticsClient(),
                    new SharedPreferencesStudentCCStatisticsCache(context.getApplicationContext().getSharedPreferences(
                            "studentcc_class_statistics_v1", Context.MODE_PRIVATE)), System::currentTimeMillis);
        }
        return instance;
    }
    StudentCCStatisticsRepository(StudentCCStatisticsClient client, StudentCCStatisticsCache cache, Clock clock) {
        this.client = client; this.cache = cache; this.clock = clock;
    }
    public boolean isConfigured() { return client.isConfigured(); }
    StudentCCStatisticsClient client() { return client; }
    public Single<StudentCCStatisticsResult> getStatistics(Course.AllData course, boolean forceRefresh) {
        try { return getStatistics(StudentCCClassScope.fromCourse(course), forceRefresh); }
        catch (IllegalArgumentException error) { return Single.just(StudentCCStatisticsResult.unavailable(
                StudentCCStatisticsResult.Status.INVALID_REQUEST, "Sync StudentCC again to load this class and semester.")); }
    }
    public Single<StudentCCStatisticsResult> getStatistics(StudentCCClassScope scope, boolean forceRefresh) {
        return Single.defer(() -> {
            if (!client.isConfigured()) return Single.just(StudentCCStatisticsResult.unavailable(
                    StudentCCStatisticsResult.Status.NOT_CONFIGURED, "StudentCC class statistics are being prepared."));
            if (scope == null) return Single.just(StudentCCStatisticsResult.unavailable(StudentCCStatisticsResult.Status.INVALID_REQUEST,
                    "Sync StudentCC again to load this class and semester."));
            String key = key(scope); StudentCCStatisticsCache.Entry saved = read(key, scope);
            if (!forceRefresh && recent(saved, TimeUnit.MINUTES.toMillis(30))) return Single.just(StudentCCStatisticsResult.data(
                    saved.statistics, StudentCCStatisticsResult.Source.CACHE, saved.fetchedAtMillis));
            synchronized (inFlight) {
                Flight existing = inFlight.get(key); if (existing != null) return existing.request;
                Flight flight = new Flight();
                long requestVersion = versions.getOrDefault(key, 0L), requestClearVersion = clearVersion;
                flight.request = client.fetchStatistics(scope).map(statistics -> {
                    synchronized (inFlight) {
                        if (requestClearVersion != clearVersion || requestVersion != versions.getOrDefault(key, 0L)) {
                            StudentCCStatisticsCache.Entry latest = read(key, scope);
                            return latest == null ? StudentCCStatisticsResult.unavailable(StudentCCStatisticsResult.Status.UNAVAILABLE,
                                    "Refresh StudentCC class statistics to see the updated pool.")
                                    : StudentCCStatisticsResult.data(latest.statistics, StudentCCStatisticsResult.Source.CACHE, latest.fetchedAtMillis);
                        }
                        long at = clock.now(); store(key, new StudentCCStatisticsCache.Entry(statistics, at));
                        return StudentCCStatisticsResult.data(statistics, StudentCCStatisticsResult.Source.NETWORK, at);
                    }
                }).onErrorReturn(error -> {
                    synchronized (inFlight) {
                        if (requestClearVersion != clearVersion || requestVersion != versions.getOrDefault(key, 0L)) {
                            StudentCCStatisticsCache.Entry latest = read(key, scope);
                            return latest == null ? StudentCCStatisticsResult.unavailable(StudentCCStatisticsResult.Status.UNAVAILABLE,
                                    "Refresh StudentCC class statistics to see the updated pool.")
                                    : StudentCCStatisticsResult.data(latest.statistics, StudentCCStatisticsResult.Source.CACHE, latest.fetchedAtMillis);
                        }
                        return failure(key, saved, error);
                    }
                }).doFinally(() -> {
                    synchronized (inFlight) { if (inFlight.get(key) == flight) inFlight.remove(key); }
                }).toObservable().replay(1).refCount().firstOrError();
                inFlight.put(key, flight); return flight.request;
            }
        }).subscribeOn(Schedulers.io());
    }
    void recordSubmission(StudentCCClassStatistics stats) {
        synchronized (inFlight) {
            String key = key(stats.scope); versions.put(key, versions.getOrDefault(key, 0L) + 1); inFlight.remove(key);
            store(key, new StudentCCStatisticsCache.Entry(stats, clock.now()));
        }
    }
    void clearCache() {
        synchronized (inFlight) { clearVersion++; versions.clear(); inFlight.clear(); try { cache.clear(); } catch (RuntimeException ignored) {} }
    }
    private String key(StudentCCClassScope scope) { return client.backendOrigin() + "#" + scope.key(); }
    private StudentCCStatisticsResult failure(String key, StudentCCStatisticsCache.Entry saved, Throwable error) {
        if (error instanceof StudentCCStatisticsClient.FetchException) {
            StudentCCStatisticsClient.FailureKind kind = ((StudentCCStatisticsClient.FetchException) error).kind;
            if (kind == StudentCCStatisticsClient.FailureKind.INVALID_PAYLOAD) {
                try { cache.remove(key); } catch (RuntimeException ignored) {}
            } else if (kind == StudentCCStatisticsClient.FailureKind.TEMPORARY && recent(saved, TimeUnit.DAYS.toMillis(7))) {
                return StudentCCStatisticsResult.data(saved.statistics, StudentCCStatisticsResult.Source.STALE_CACHE, saved.fetchedAtMillis);
            }
        }
        return StudentCCStatisticsResult.unavailable(StudentCCStatisticsResult.Status.UNAVAILABLE,
                "StudentCC class statistics are temporarily unavailable. Your saved marks remain available.");
    }
    private boolean recent(StudentCCStatisticsCache.Entry entry, long age) { return entry != null && clock.now() >= entry.fetchedAtMillis && clock.now() - entry.fetchedAtMillis < age; }
    private StudentCCStatisticsCache.Entry read(String key, StudentCCClassScope scope) { try { return cache.read(key, scope); } catch (RuntimeException ignored) { return null; } }
    private void store(String key, StudentCCStatisticsCache.Entry entry) { try { cache.write(key, entry); } catch (RuntimeException ignored) {} }
}
