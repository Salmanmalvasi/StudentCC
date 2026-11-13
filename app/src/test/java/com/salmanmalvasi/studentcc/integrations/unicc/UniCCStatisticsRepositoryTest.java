package com.salmanmalvasi.studentcc.integrations.unicc;

import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.rxjava3.observers.TestObserver;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

import static org.junit.Assert.*;

/** Network contract and cache behavior exercised with synthetic class IDs and marks only. */
public class UniCCStatisticsRepositoryTest {
    private static final String VALID = "{\"mean\":42.5,\"variance\":25,\"sd\":5,\"count\":35}";
    private static final long TTL = TimeUnit.MINUTES.toMillis(30);
    private MockWebServer server;
    private UniCCStatisticsClient client;
    private TestPreferences preferences;
    private AtomicLong now;
    private UniCCStatisticsRepository repository;

    @Before public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new UniCCStatisticsClient(server.url("/"));
        preferences = new TestPreferences();
        now = new AtomicLong(1_800_000_000_000L);
        repository = newRepository();
    }

    @After public void tearDown() throws IOException { server.shutdown(); }

    private UniCCStatisticsRepository newRepository() {
        return new UniCCStatisticsRepository(client,
                new SharedPreferencesUniCCStatisticsCache(preferences), now::get, TTL);
    }

    private UniCCStatisticsResult fetch(boolean force) {
        return repository.getStatistics("SYNTHETIC_CLASS_1", "SEM_TEST_1", force).blockingGet();
    }

    @Test public void aggregateRequestSendsOnlyClassIdentifier() throws Exception {
        server.enqueue(new MockResponse().setBody(VALID));
        UniCCStatisticsResult result = fetch(false);
        assertTrue(result.hasStatistics());
        assertEquals(UniCCStatisticsResult.Source.NETWORK, result.source);
        assertEquals(42.5, result.statistics.mean, 0.0001);
        assertEquals(35, result.statistics.count);
        assertEquals(60, result.statistics.marksBasis);
        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        assertEquals("/api/attendance/marks?classId=SYNTHETIC_CLASS_1", request.getPath());
        assertNull(request.getHeader("Cookie"));
        assertNull(request.getHeader("Authorization"));
        assertNull(request.getHeader("X-CSRF-Token"));
        assertEquals(0, request.getBodySize());
        assertFalse(request.getPath().contains("SEM_TEST_1"));
    }

    @Test public void knownBackendMissingData500BecomesNoData() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody(
                "{\"error\":\"Failed to fetch class statistics for classID \\\"SYNTHETIC_CLASS_1\\\": No data found for classID \\\"SYNTHETIC_CLASS_1\\\".\"}"));
        UniCCStatisticsResult result = fetch(false);
        assertEquals(UniCCStatisticsResult.Status.NO_DATA, result.status);
        assertFalse(result.hasStatistics());
        assertTrue(result.message.contains("classmates"));
    }

    @Test public void missingData404BecomesNoData() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{}"));
        assertEquals(UniCCStatisticsResult.Status.NO_DATA, fetch(false).status);
    }

    @Test public void rejectsMalformedAndIncompatiblePayloads() {
        String[] invalid = {
                "not json",
                "{\"mean\":42,\"variance\":25,\"sd\":5}",
                "{\"mean\":\"42\",\"variance\":25,\"sd\":5,\"count\":35}",
                "{\"mean\":75,\"variance\":25,\"sd\":5,\"count\":35}",
                "{\"mean\":42,\"variance\":-25,\"sd\":5,\"count\":35}",
                "{\"mean\":42,\"variance\":25,\"sd\":6,\"count\":35}",
                "{\"mean\":42,\"variance\":25,\"sd\":5,\"count\":35.5}",
                "{\"mean\":42,\"variance\":25,\"sd\":5,\"count\":2147483648}",
                "{\"mean\":42,\"variance\":25,\"sd\":5,\"count\":35,\"marksBasis\":100}",
                "{\"mean\":1e999,\"variance\":25,\"sd\":5,\"count\":35}"
        };
        for (String body : invalid) {
            server.enqueue(new MockResponse().setBody(body));
            UniCCStatisticsResult result = fetch(true);
            assertEquals(body, UniCCStatisticsResult.Status.UNAVAILABLE, result.status);
            assertFalse(result.hasStatistics());
        }
    }

    @Test public void zeroSamplesAreNoData() {
        server.enqueue(new MockResponse().setBody("{\"mean\":0,\"variance\":0,\"sd\":0,\"count\":0}"));
        assertEquals(UniCCStatisticsResult.Status.NO_DATA, fetch(false).status);
    }

    @Test public void freshAggregateCacheSurvivesRepositoryRecreation() {
        server.enqueue(new MockResponse().setBody(VALID));
        UniCCStatisticsResult original = fetch(false);
        repository = newRepository();
        now.addAndGet(TimeUnit.MINUTES.toMillis(10));
        UniCCStatisticsResult cached = fetch(false);
        assertEquals(UniCCStatisticsResult.Source.CACHE, cached.source);
        assertEquals(original.fetchedAtMillis, cached.fetchedAtMillis);
        assertEquals(42.5, cached.statistics.mean, 0.0001);
        assertEquals(1, server.getRequestCount());
    }

    @Test public void ttlAndForceRefreshRequestUpdatedStatistics() {
        server.enqueue(new MockResponse().setBody(VALID));
        fetch(false);
        server.enqueue(new MockResponse().setBody(VALID.replace("42.5", "44.5")));
        assertEquals(44.5, fetch(true).statistics.mean, 0.0001);
        now.addAndGet(TTL);
        server.enqueue(new MockResponse().setBody(VALID.replace("42.5", "46.5")));
        assertEquals(46.5, fetch(false).statistics.mean, 0.0001);
        assertEquals(3, server.getRequestCount());
    }

    @Test public void semesterKeysNeverShareCachedStatistics() {
        server.enqueue(new MockResponse().setBody(VALID));
        fetch(false);
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{}"));
        UniCCStatisticsResult otherSemester = repository.getStatistics(
                "SYNTHETIC_CLASS_1", "SEM_TEST_2", false).blockingGet();
        assertEquals(UniCCStatisticsResult.Status.NO_DATA, otherSemester.status);
        assertEquals(UniCCStatisticsResult.Source.CACHE, fetch(false).source);
        assertEquals(2, server.getRequestCount());
    }

    @Test public void temporaryFailureUsesPreviouslyDownloadedAggregateWithTimestamp() {
        server.enqueue(new MockResponse().setBody(VALID));
        long at = fetch(false).fetchedAtMillis;
        now.addAndGet(TTL + 1);
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{}"));
        UniCCStatisticsResult cached = fetch(false);
        assertTrue(cached.hasStatistics());
        assertTrue(cached.isStale());
        assertEquals(at, cached.fetchedAtMillis);
    }

    @Test public void veryOldAndFutureDatedCachesAreNotUsed() {
        server.enqueue(new MockResponse().setBody(VALID));
        fetch(false);
        now.addAndGet(TimeUnit.DAYS.toMillis(7));
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{}"));
        assertFalse(fetch(false).hasStatistics());
        now.addAndGet(-TimeUnit.DAYS.toMillis(8));
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{}"));
        assertFalse(fetch(false).hasStatistics());
    }

    @Test public void missingOrInvalidServerDataClearsCacheRatherThanShowingOldComparison() {
        server.enqueue(new MockResponse().setBody(VALID));
        fetch(false);
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{}"));
        assertEquals(UniCCStatisticsResult.Status.NO_DATA, fetch(true).status);
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{}"));
        assertFalse(fetch(false).hasStatistics());
        server.enqueue(new MockResponse().setBody(VALID));
        fetch(true);
        server.enqueue(new MockResponse().setBody(VALID.replace("42.5", "75")));
        assertEquals(UniCCStatisticsResult.Status.UNAVAILABLE, fetch(true).status);
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{}"));
        assertFalse(fetch(false).hasStatistics());
    }

    @Test public void concurrentObserversShareOneRequest() throws Exception {
        server.enqueue(new MockResponse().setBody(VALID).setBodyDelay(300, TimeUnit.MILLISECONDS));
        TestObserver<UniCCStatisticsResult> first = repository.getStatistics(
                "SYNTHETIC_CLASS_1", "SEM_TEST_1", true).test();
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        TestObserver<UniCCStatisticsResult> second = repository.getStatistics(
                "SYNTHETIC_CLASS_1", "SEM_TEST_1", true).test();
        first.awaitDone(3, TimeUnit.SECONDS).assertComplete().assertNoErrors();
        second.awaitDone(3, TimeUnit.SECONDS).assertComplete().assertNoErrors();
        assertTrue(first.values().get(0).hasStatistics());
        assertTrue(second.values().get(0).hasStatistics());
        assertEquals(1, server.getRequestCount());
    }

    @Test public void lastObserverCancellationAllowsImmediateRetry() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        TestObserver<UniCCStatisticsResult> first = repository.getStatistics(
                "SYNTHETIC_CLASS_1", "SEM_TEST_1", true).test();
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        first.dispose();
        server.enqueue(new MockResponse().setBody(VALID));
        TestObserver<UniCCStatisticsResult> retry = repository.getStatistics(
                "SYNTHETIC_CLASS_1", "SEM_TEST_1", true).test();
        retry.awaitDone(3, TimeUnit.SECONDS).assertComplete().assertNoErrors();
        assertTrue(retry.values().get(0).hasStatistics());
        assertEquals(2, server.getRequestCount());
    }

    @Test public void redirectsAreNotFollowed() {
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", server.url("/other")));
        assertFalse(fetch(false).hasStatistics());
        assertEquals(1, server.getRequestCount());
    }

    @Test public void missingIdentifiersReturnActionableResultWithoutNetwork() {
        UniCCStatisticsResult result = repository.getStatistics("", "SEM_TEST_1", false).blockingGet();
        assertEquals(UniCCStatisticsResult.Status.INVALID_REQUEST, result.status);
        assertTrue(result.message.contains("Refresh"));
        assertEquals(UniCCStatisticsResult.Status.INVALID_REQUEST,
                repository.getStatistics("CLASS", null, false).blockingGet().status);
        assertEquals(0, server.getRequestCount());
    }

    /** In-memory persistence API replacement, allowing cache serialization to be tested on the JVM. */
    private static final class TestPreferences implements SharedPreferences {
        private final Map<String, Object> data = new HashMap<>();
        @Override public synchronized Map<String, ?> getAll() { return new HashMap<>(data); }
        @Override public synchronized String getString(String key, String fallback) {
            Object value = data.get(key); return value == null ? fallback : (String) value;
        }
        @Override public Set<String> getStringSet(String key, Set<String> fallback) { return fallback; }
        @Override public int getInt(String key, int fallback) { return fallback; }
        @Override public long getLong(String key, long fallback) { return fallback; }
        @Override public float getFloat(String key, float fallback) { return fallback; }
        @Override public boolean getBoolean(String key, boolean fallback) { return fallback; }
        @Override public synchronized boolean contains(String key) { return data.containsKey(key); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public Editor edit() {
            return new Editor() {
                final Map<String, Object> pending = new HashMap<>();
                boolean clear;
                @Override public Editor putString(String key, String value) { pending.put(key, value); return this; }
                @Override public Editor putStringSet(String key, Set<String> value) { pending.put(key, value); return this; }
                @Override public Editor putInt(String key, int value) { pending.put(key, value); return this; }
                @Override public Editor putLong(String key, long value) { pending.put(key, value); return this; }
                @Override public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
                @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
                @Override public Editor remove(String key) { pending.put(key, null); return this; }
                @Override public Editor clear() { clear = true; return this; }
                @Override public boolean commit() {
                    synchronized (TestPreferences.this) {
                        if (clear) data.clear();
                        for (Map.Entry<String, Object> entry : pending.entrySet()) {
                            if (entry.getValue() == null) data.remove(entry.getKey());
                            else data.put(entry.getKey(), entry.getValue());
                        }
                    }
                    return true;
                }
                @Override public void apply() { commit(); }
            };
        }
    }
}
