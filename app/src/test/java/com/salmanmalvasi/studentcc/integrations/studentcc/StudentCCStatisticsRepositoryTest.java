package com.salmanmalvasi.studentcc.integrations.studentcc;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.rxjava3.observers.TestObserver;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.*;

/** Exercises independent aggregate caching and contribution/read races with synthetic data. */
public class StudentCCStatisticsRepositoryTest {
    private static final StudentCCClassScope SCOPE = new StudentCCClassScope("CH", "CH_TEST_SEM", "CH_TEST_CLASS");
    private MockWebServer server;
    private StudentCCStatisticsClient client;
    private MemoryCache cache;
    private AtomicLong now;
    private StudentCCStatisticsRepository repository;

    @Before public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new StudentCCStatisticsClient(server.url("/").toString(), true, true,
                () -> { throw new AssertionError("Aggregate reads must not request Firebase authentication"); });
        cache = new MemoryCache();
        now = new AtomicLong(1_800_000_000_000L);
        repository = new StudentCCStatisticsRepository(client, cache, now::get);
    }

    @After public void tearDown() throws IOException { server.shutdown(); }

    private StudentCCStatisticsResult fetch(boolean force) {
        return repository.getStatistics(SCOPE, force).blockingGet();
    }

    private static String payload(int count, double mean) {
        return "{\"source\":\"studentcc\",\"campus\":\"CH\",\"semesterId\":\"CH_TEST_SEM\","
                + "\"classId\":\"CH_TEST_CLASS\",\"stage\":\"INTERNAL_60\",\"marksBasis\":60,"
                + "\"count\":" + count + ",\"minContributors\":1,\"status\":\"available\","
                + "\"updatedAt\":\"2026-11-13T12:00:00.000Z\",\"mean\":" + mean + ",\"variance\":0,\"sd\":0}";
    }

    @Test public void firstContributorRemainsVisibleAfterRepositoryRecreation() {
        server.enqueue(new MockResponse().setBody(payload(1, 42.35)));
        StudentCCStatisticsResult original = fetch(false);
        repository = new StudentCCStatisticsRepository(client, cache, now::get);
        StudentCCStatisticsResult cached = fetch(false);
        assertTrue(cached.hasStatistics());
        assertEquals(StudentCCStatisticsResult.Source.CACHE, cached.source);
        assertEquals(1, cached.statistics.count);
        assertEquals(42.35, cached.statistics.mean, 0.00001);
        assertEquals(0, cached.statistics.sd, 0);
        assertEquals(original.fetchedAtMillis, cached.fetchedAtMillis);
        assertEquals(1, server.getRequestCount());
    }

    @Test public void internalAndFinalPoolsNeverReuseEachOthersCachedValues() {
        StudentCCClassScope finalScope = new StudentCCClassScope(SCOPE.campus, SCOPE.semesterId, SCOPE.classId, 100);
        server.enqueue(new MockResponse().setBody(payload(1, 47.75)));
        assertEquals(47.75, fetch(false).statistics.mean, 0.0);
        server.enqueue(new MockResponse().setBody(payload(1, 78.875)
                .replace("INTERNAL_60", "FINAL_100").replace("\"marksBasis\":60", "\"marksBasis\":100")));
        assertEquals(78.875, repository.getStatistics(finalScope, false).blockingGet().statistics.mean, 0.0);
        repository = new StudentCCStatisticsRepository(client, cache, now::get);
        assertEquals(47.75, fetch(false).statistics.mean, 0.0);
        assertEquals(78.875, repository.getStatistics(finalScope, false).blockingGet().statistics.mean, 0.0);
        assertEquals(2, server.getRequestCount());
        repository.clearCache();
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        assertFalse(fetch(false).hasStatistics());
        assertFalse(repository.getStatistics(finalScope, false).blockingGet().hasStatistics());
    }

    @Test public void temporaryFailureUsesFreshEnoughOwnPoolCache() {
        server.enqueue(new MockResponse().setBody(payload(1, 42.35)));
        long at = fetch(false).fetchedAtMillis;
        now.addAndGet(TimeUnit.MINUTES.toMillis(31));
        server.enqueue(new MockResponse().setResponseCode(503));
        StudentCCStatisticsResult fallback = fetch(false);
        assertTrue(fallback.hasStatistics());
        assertTrue(fallback.isStale());
        assertEquals(at, fallback.fetchedAtMillis);
        assertEquals(42.35, fallback.statistics.mean, 0.00001);
    }

    @Test public void incompatibleResponseClearsPreviousComparison() {
        server.enqueue(new MockResponse().setBody(payload(1, 42.35)));
        fetch(false);
        server.enqueue(new MockResponse().setBody(payload(1, 42.35).replace("CH_TEST_CLASS", "CH_WRONG_CLASS")));
        assertFalse(fetch(true).hasStatistics());
        server.enqueue(new MockResponse().setResponseCode(503));
        assertFalse(fetch(false).hasStatistics());
    }

    @Test public void differentBackendDoesNotReuseOwnPoolCache() throws IOException {
        server.enqueue(new MockResponse().setBody(payload(1, 42.35)));
        fetch(false);
        MockWebServer other = new MockWebServer();
        other.start();
        try {
            StudentCCStatisticsClient second = new StudentCCStatisticsClient(other.url("/").toString(), true, true, null);
            repository = new StudentCCStatisticsRepository(second, cache, now::get);
            other.enqueue(new MockResponse().setResponseCode(503));
            assertFalse(fetch(false).hasStatistics());
            assertEquals(1, other.getRequestCount());
        } finally { other.shutdown(); }
    }

    @Test public void concurrentReadersShareOneRequest() throws Exception {
        server.enqueue(new MockResponse().setBody(payload(1, 42.35)).setBodyDelay(200, TimeUnit.MILLISECONDS));
        TestObserver<StudentCCStatisticsResult> first = repository.getStatistics(SCOPE, true).test();
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        TestObserver<StudentCCStatisticsResult> second = repository.getStatistics(SCOPE, true).test();
        first.awaitDone(3, TimeUnit.SECONDS).assertComplete().assertNoErrors();
        second.awaitDone(3, TimeUnit.SECONDS).assertComplete().assertNoErrors();
        assertTrue(first.values().get(0).hasStatistics());
        assertTrue(second.values().get(0).hasStatistics());
        assertEquals(1, server.getRequestCount());
    }

    @Test public void olderGetCannotOverwriteConfirmedContribution() throws Exception {
        server.enqueue(new MockResponse().setBody(payload(1, 10)).setBodyDelay(200, TimeUnit.MILLISECONDS));
        TestObserver<StudentCCStatisticsResult> old = repository.getStatistics(SCOPE, true).test();
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        repository.recordSubmission(StudentCCStatisticsClient.parseStatistics(payload(1, 42.35), SCOPE));
        old.awaitDone(3, TimeUnit.SECONDS).assertComplete().assertNoErrors();
        assertEquals(42.35, fetch(false).statistics.mean, 0.00001);
        assertEquals(1, server.getRequestCount());
    }

    @Test public void olderInvalidGetCannotDeleteConfirmedContributionCache() throws Exception {
        server.enqueue(new MockResponse().setBody("{}").setBodyDelay(200, TimeUnit.MILLISECONDS));
        TestObserver<StudentCCStatisticsResult> old = repository.getStatistics(SCOPE, true).test();
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        repository.recordSubmission(StudentCCStatisticsClient.parseStatistics(payload(1, 42.35), SCOPE));
        old.awaitDone(3, TimeUnit.SECONDS).assertComplete().assertNoErrors();
        assertEquals(42.35, fetch(false).statistics.mean, 0.00001);
        assertEquals(1, server.getRequestCount());
    }

    @Test public void olderGetCannotRestoreCacheAfterWithdrawal() throws Exception {
        server.enqueue(new MockResponse().setBody(payload(1, 42.35)).setBodyDelay(200, TimeUnit.MILLISECONDS));
        TestObserver<StudentCCStatisticsResult> old = repository.getStatistics(SCOPE, true).test();
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        repository.clearCache();
        old.awaitDone(3, TimeUnit.SECONDS).assertComplete().assertNoErrors();
        server.enqueue(new MockResponse().setResponseCode(503));
        assertFalse(fetch(false).hasStatistics());
        assertEquals(2, server.getRequestCount());
    }

    private static final class MemoryCache implements StudentCCStatisticsCache {
        private final Map<String, Entry> entries = new HashMap<>();
        @Override public synchronized Entry read(String key, StudentCCClassScope scope) { return entries.get(key); }
        @Override public synchronized void write(String key, Entry entry) { entries.put(key, entry); }
        @Override public synchronized void remove(String key) { entries.remove(key); }
        @Override public synchronized void clear() { entries.clear(); }
    }
}
