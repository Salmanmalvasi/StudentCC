package com.salmanmalvasi.studentcc.integrations.unicc;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Retrieves UniCC's existing public aggregates without sharing student sessions or marks. */
public final class UniCCStatisticsRepository {
    private static final long FRESH_TTL_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final long MAX_STALE_MILLIS = TimeUnit.DAYS.toMillis(7);
    private static volatile UniCCStatisticsRepository instance;

    interface Clock { long nowMillis(); }

    private final UniCCStatisticsClient client;
    private final UniCCStatisticsCache cache;
    private final Clock clock;
    private final long freshTtlMillis;
    private final Map<String, Flight> inFlight = new HashMap<>();

    private static final class Flight { Single<UniCCStatisticsResult> request; }

    public static UniCCStatisticsRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (UniCCStatisticsRepository.class) {
                if (instance == null) {
                    Context application = context.getApplicationContext();
                    instance = new UniCCStatisticsRepository(new UniCCStatisticsClient(),
                            new SharedPreferencesUniCCStatisticsCache(application.getSharedPreferences(
                                    "unicc_class_statistics_v1", Context.MODE_PRIVATE)),
                            System::currentTimeMillis, FRESH_TTL_MILLIS);
                }
            }
        }
        return instance;
    }

    UniCCStatisticsRepository(UniCCStatisticsClient client, UniCCStatisticsCache cache,
                              Clock clock, long freshTtlMillis) {
        this.client = client;
        this.cache = cache;
        this.clock = clock;
        this.freshTtlMillis = freshTtlMillis;
    }

    /** Lazy, cancellable fetch. Observe on Android's main thread before updating views. */
    public Single<UniCCStatisticsResult> getStatistics(String classId, String semesterId,
                                                      boolean forceRefresh) {
        return Single.defer(() -> {
            String normalizedClassId = classId == null ? "" : classId.trim();
            String normalizedSemesterId = semesterId == null ? "" : semesterId.trim();
            if (!validIdentifier(normalizedClassId) || !validIdentifier(normalizedSemesterId)) {
                return Single.just(UniCCStatisticsResult.unavailable(
                        UniCCStatisticsResult.Status.INVALID_REQUEST,
                        "Refresh StudentCC's VTOP data to load the class and semester identifiers."));
            }
            String key = normalizedClassId.length() + ":" + normalizedClassId + ":" + normalizedSemesterId;
            UniCCStatisticsCache.Entry cached = readCache(key);
            if (!forceRefresh && hasAgeWithin(cached, freshTtlMillis)) {
                return Single.just(UniCCStatisticsResult.available(cached.statistics,
                        UniCCStatisticsResult.Source.CACHE, cached.fetchedAtMillis));
            }
            synchronized (inFlight) {
                Flight existing = inFlight.get(key);
                if (existing != null) return existing.request;
                Flight flight = new Flight();
                flight.request = client.fetchStatistics(normalizedClassId)
                        .map(statistics -> {
                            long at = clock.nowMillis();
                            writeCache(key, new UniCCStatisticsCache.Entry(statistics, at));
                            return UniCCStatisticsResult.available(statistics,
                                    UniCCStatisticsResult.Source.NETWORK, at);
                        })
                        .onErrorReturn(error -> fetchFailure(key, cached, error))
                        .doFinally(() -> {
                            synchronized (inFlight) {
                                if (inFlight.get(key) == flight) inFlight.remove(key);
                            }
                        })
                        .toObservable().replay(1).refCount().firstOrError();
                inFlight.put(key, flight);
                return flight.request;
            }
        }).subscribeOn(Schedulers.io());
    }

    private UniCCStatisticsResult fetchFailure(String key, UniCCStatisticsCache.Entry cached, Throwable error) {
        if (error instanceof UniCCStatisticsClient.FetchException) {
            UniCCStatisticsClient.FailureKind kind = ((UniCCStatisticsClient.FetchException) error).kind;
            if (kind == UniCCStatisticsClient.FailureKind.NO_DATA) {
                removeCache(key);
                return UniCCStatisticsResult.unavailable(UniCCStatisticsResult.Status.NO_DATA,
                        "No UniCC samples are available for this class yet. Try again after classmates use UniCC.");
            }
            if (kind == UniCCStatisticsClient.FailureKind.INVALID_PAYLOAD) {
                removeCache(key);
                return UniCCStatisticsResult.unavailable(UniCCStatisticsResult.Status.UNAVAILABLE,
                        "UniCC returned statistics that cannot be compared with internal marks out of 60.");
            }
            if (kind == UniCCStatisticsClient.FailureKind.TEMPORARY && hasAgeWithin(cached, MAX_STALE_MILLIS)) {
                return UniCCStatisticsResult.available(cached.statistics,
                        UniCCStatisticsResult.Source.STALE_CACHE, cached.fetchedAtMillis);
            }
        }
        return UniCCStatisticsResult.unavailable(UniCCStatisticsResult.Status.UNAVAILABLE,
                "Cannot load UniCC class statistics. Check your connection and try again.");
    }

    private boolean hasAgeWithin(UniCCStatisticsCache.Entry entry, long maximumAge) {
        if (entry == null) return false;
        long age = clock.nowMillis() - entry.fetchedAtMillis;
        return age >= 0 && age < maximumAge;
    }

    private UniCCStatisticsCache.Entry readCache(String key) {
        try { return cache.read(key); } catch (RuntimeException ignored) { return null; }
    }

    private void writeCache(String key, UniCCStatisticsCache.Entry entry) {
        try { cache.write(key, entry); } catch (RuntimeException ignored) { /* Cache is optional. */ }
    }

    private void removeCache(String key) {
        try { cache.remove(key); } catch (RuntimeException ignored) { /* Cache is optional. */ }
    }

    private static boolean validIdentifier(String value) {
        return value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}");
    }
}
