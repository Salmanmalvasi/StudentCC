package com.salmanmalvasi.studentcc.integrations.studentcc;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.*;

/** Synthetic network tests for StudentCC's independent fractional-total service. */
public class StudentCCStatisticsClientTest {
    private static final StudentCCClassScope SCOPE = new StudentCCClassScope("CH", "CH20262601", "CH_TEST_CLASS_1");
    private MockWebServer server;
    private StudentCCStatisticsClient local;
    private File temporaryTrustStore;
    private String priorTrustStore, priorTrustPassword, priorTrustType;
    private boolean changedTrustStore;

    @Before public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        local = new StudentCCStatisticsClient(server.url("/").toString(), true, true,
                () -> Single.error(new AssertionError("Local requests must not obtain Firebase credentials")));
    }

    @After public void tearDown() throws Exception {
        if (server != null) server.shutdown();
        if (changedTrustStore) {
            restoreProperty("javax.net.ssl.trustStore", priorTrustStore);
            restoreProperty("javax.net.ssl.trustStorePassword", priorTrustPassword);
            restoreProperty("javax.net.ssl.trustStoreType", priorTrustType);
        }
        if (temporaryTrustStore != null) temporaryTrustStore.delete();
    }

    private static String available(double mean) throws Exception {
        return SCOPE.toJson().put("source", "studentcc").put("count", 1).put("minContributors", 1)
                .put("cgpaAtLeastNineCount", 0)
                .put("status", "available").put("mean", mean).put("variance", 0).put("sd", 0)
                .put("updatedAt", "2026-11-13T12:00:00.123Z").toString();
    }

    private static String missing() throws Exception {
        return SCOPE.toJson().put("source", "studentcc").put("count", 0).put("minContributors", 1)
                .put("cgpaAtLeastNineCount", 0)
                .put("status", "insufficient_data").put("updatedAt", JSONObject.NULL).toString();
    }

    private static void failure(Single<?> request, StudentCCStatisticsClient.FailureKind kind) {
        TestObserver<?> observer = request.test();
        observer.awaitDone(3, TimeUnit.SECONDS).assertError(error ->
                error instanceof StudentCCStatisticsClient.FetchException
                        && ((StudentCCStatisticsClient.FetchException) error).kind == kind);
    }

    @Test public void firstContributorMeanAndZeroSdAreVisible() throws Exception {
        server.enqueue(new MockResponse().setBody(available(42.35)));
        StudentCCClassStatistics statistics = local.fetchStatistics(SCOPE).blockingGet();
        assertTrue(statistics.hasValues());
        assertEquals(1, statistics.count);
        assertEquals(1, statistics.minContributors);
        assertEquals(42.35, statistics.mean, 0.00001);
        assertEquals(0, statistics.variance, 0);
        assertEquals(0, statistics.sd, 0);
        assertTrue(statistics.updatedAtMillis > 0);
    }

    @Test public void finalHundredPointReadAndWriteUseTheFinalPoolAndAcceptScoresAboveSixty() throws Exception {
        StudentCCClassScope finalScope = new StudentCCClassScope(SCOPE.campus, SCOPE.semesterId, SCOPE.classId, 100);
        String finalPayload = new JSONObject(available(42.35)).put("marksBasis", 100)
                .put("stage", "FINAL_100").put("mean", 78.875).toString();
        server.enqueue(new MockResponse().setBody(finalPayload));
        StudentCCClassStatistics statistics = local.fetchStatistics(finalScope).blockingGet();
        assertEquals(100, statistics.marksBasis);
        assertEquals(78.875, statistics.mean, 0.0);
        RecordedRequest read = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(read);
        assertEquals("FINAL_100", read.getRequestUrl().queryParameter("stage"));
        assertEquals("100", read.getRequestUrl().queryParameter("marksBasis"));
        server.enqueue(new MockResponse().setBody(finalPayload));
        local.submit(new StudentCCContribution(finalScope, "BCSE101L", 78.875), UUID.randomUUID().toString()).blockingGet();
        RecordedRequest post = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(post);
        JSONObject body = new JSONObject(post.getBody().readUtf8());
        assertEquals("FINAL_100", body.getString("stage"));
        assertEquals(100, body.getInt("marksBasis"));
        assertEquals(78.875, body.getDouble("total"), 0.0);
        server.enqueue(new MockResponse().setBody(available(42.35)));
        failure(local.fetchStatistics(finalScope), StudentCCStatisticsClient.FailureKind.INVALID_PAYLOAD);
    }

    @Test public void emptyCohortHasNoInventedValues() throws Exception {
        server.enqueue(new MockResponse().setBody(missing()));
        StudentCCClassStatistics statistics = local.fetchStatistics(SCOPE).blockingGet();
        assertEquals(0, statistics.count);
        assertFalse(statistics.hasValues());
        assertNull(statistics.mean);
        assertNull(statistics.sd);
        assertNull(statistics.variance);
        assertEquals(0, statistics.updatedAtMillis);
    }

    @Test public void publicClassTallyCanExceedCurrentAssessmentContributors() throws Exception {
        server.enqueue(new MockResponse().setBody(new JSONObject(available(42.35))
                .put("cgpaAtLeastNineCount", 3).toString()));
        StudentCCClassStatistics statistics = local.fetchStatistics(SCOPE).blockingGet();
        assertEquals(1, statistics.count);
        assertEquals(Integer.valueOf(3), statistics.cgpaAtLeastNineCount);
        server.enqueue(new MockResponse().setBody(new JSONObject(missing())
                .put("cgpaAtLeastNineCount", 3).toString()));
        statistics = local.fetchStatistics(SCOPE).blockingGet();
        assertFalse(statistics.hasValues());
        assertEquals(Integer.valueOf(3), statistics.cgpaAtLeastNineCount);
    }

    @Test public void legacyResponseWithoutPublicTallyKeepsItUnknown() throws Exception {
        JSONObject legacy = new JSONObject(available(42.35));
        legacy.remove("cgpaAtLeastNineCount");
        server.enqueue(new MockResponse().setBody(legacy.toString()));
        StudentCCClassStatistics statistics = local.fetchStatistics(SCOPE).blockingGet();
        assertTrue(statistics.hasValues());
        assertNull(statistics.cgpaAtLeastNineCount);
    }

    @Test public void publicClassTallyRejectsNegativeFractionalAndNonNumericValues() throws Exception {
        for (Object value : new Object[] {-1, 0.5, "1", true, JSONObject.NULL}) {
            server.enqueue(new MockResponse().setBody(new JSONObject(available(42.35))
                    .put("cgpaAtLeastNineCount", value).toString()));
            failure(local.fetchStatistics(SCOPE), StudentCCStatisticsClient.FailureKind.INVALID_PAYLOAD);
        }
    }

    @Test public void readUsesOnlyExplicitCohortAndNoCredentials() throws Exception {
        server.enqueue(new MockResponse().setBody(available(42.35)));
        local.fetchStatistics(SCOPE).blockingGet();
        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        assertEquals("/v1/class-statistics", request.getRequestUrl().encodedPath());
        assertEquals("CH", request.getRequestUrl().queryParameter("campus"));
        assertEquals("CH20262601", request.getRequestUrl().queryParameter("semesterId"));
        assertEquals("CH_TEST_CLASS_1", request.getRequestUrl().queryParameter("classId"));
        assertEquals("INTERNAL_60", request.getRequestUrl().queryParameter("stage"));
        assertEquals("60", request.getRequestUrl().queryParameter("marksBasis"));
        assertNull(request.getHeader("Cookie"));
        assertNull(request.getHeader("Authorization"));
        assertNull(request.getHeader("X-StudentCC-Participant"));
        assertEquals(0, request.getBodySize());
    }

    @Test public void scopeAndSourceMismatchAreRejected() throws Exception {
        String[] keys = {"campus", "semesterId", "classId", "stage", "source"};
        String[] values = {"VL", "CH20262602", "CH_TEST_CLASS_2", "FINAL_100", "unicc"};
        for (int index = 0; index < keys.length; index++) {
            server.enqueue(new MockResponse().setBody(new JSONObject(available(42)).put(keys[index], values[index]).toString()));
            failure(local.fetchStatistics(SCOPE), StudentCCStatisticsClient.FailureKind.INVALID_PAYLOAD);
        }
        server.enqueue(new MockResponse().setBody(new JSONObject(available(42)).put("marksBasis", 100).toString()));
        failure(local.fetchStatistics(SCOPE), StudentCCStatisticsClient.FailureKind.INVALID_PAYLOAD);
    }

    @Test public void oldFiveContributorGateAndInvalidStatisticsAreRejected() throws Exception {
        server.enqueue(new MockResponse().setBody(new JSONObject(missing()).put("minContributors", 5).toString()));
        failure(local.fetchStatistics(SCOPE), StudentCCStatisticsClient.FailureKind.INVALID_PAYLOAD);
        String[] invalid = {
                new JSONObject(available(42)).put("count", 1.5).toString(),
                new JSONObject(available(42)).put("sd", 5).toString(),
                new JSONObject(available(42)).put("mean", 75).toString(),
                new JSONObject(available(42)).put("mean", "42").toString(),
                new JSONObject(available(42)).put("updatedAt", "2026-99-13T12:00:00Z").toString(),
                new JSONObject(missing()).put("mean", 0).toString(),
                new JSONObject(missing()).put("status", "available").toString()
        };
        for (String body : invalid) {
            server.enqueue(new MockResponse().setBody(body));
            failure(local.fetchStatistics(SCOPE), StudentCCStatisticsClient.FailureKind.INVALID_PAYLOAD);
        }
    }

    @Test public void localFractionalPostAndWithdrawalUseSameRandomAliasOnly() throws Exception {
        String alias = UUID.randomUUID().toString();
        server.enqueue(new MockResponse().setBody(available(42.35)));
        assertEquals(42.35, local.submit(new StudentCCContribution(SCOPE, "BCSE101L", 42.35), alias)
                .blockingGet().mean, 0.00001);
        RecordedRequest post = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(post);
        assertEquals("POST", post.getMethod());
        assertEquals("/v1/contributions", post.getPath());
        assertEquals(alias, post.getHeader("X-StudentCC-Participant"));
        assertNull(post.getHeader("Authorization"));
        assertNull(post.getHeader("Cookie"));
        JSONObject body = new JSONObject(post.getBody().readUtf8());
        assertEquals(7, body.length());
        assertEquals(42.35, body.getDouble("total"), 0.00001);
        assertEquals("BCSE101L", body.getString("courseCode"));
        assertEquals("CH_TEST_CLASS_1", body.getString("classId"));
        assertFalse(body.has("participant"));
        assertFalse(body.has("password"));
        assertFalse(body.has("studentId"));

        server.enqueue(new MockResponse().setBody("{\"source\":\"studentcc\",\"status\":\"withdrawn\",\"deletedContributions\":3}"));
        assertEquals(Integer.valueOf(3), local.withdraw(alias).blockingGet());
        RecordedRequest delete = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(delete);
        assertEquals("DELETE", delete.getMethod());
        assertEquals("/v1/contributions", delete.getPath());
        assertEquals(alias, delete.getHeader("X-StudentCC-Participant"));
        assertNull(delete.getHeader("Authorization"));
        assertNull(delete.getHeader("Cookie"));
        assertEquals(0, delete.getBodySize());
    }

    @Test public void academicSummarySendsOnlyTheDisclosedBooleanAndUsesTheSameWithdrawalIdentity() throws Exception {
        String alias = UUID.randomUUID().toString();
        StudentCCStatisticsClient.AuthenticatedWriter writer = local.writer(alias, null).blockingGet();
        server.enqueue(new MockResponse().setBody("{\"source\":\"studentcc\",\"status\":\"saved\"}"));
        assertTrue(writer.submitAcademicSummary(true).blockingGet());
        RecordedRequest post = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(post);
        assertEquals("POST", post.getMethod());
        assertEquals("/v1/academic-summary", post.getPath());
        assertEquals(alias, post.getHeader("X-StudentCC-Participant"));
        assertNull(post.getHeader("Cookie"));
        JSONObject payload = new JSONObject(post.getBody().readUtf8());
        assertEquals(1, payload.length());
        assertEquals(Boolean.TRUE, payload.get("cgpaAtLeastNine"));
        server.enqueue(new MockResponse().setBody("{\"source\":\"studentcc\",\"status\":\"saved\"}"));
        assertTrue(writer.submitAcademicSummary(false).blockingGet());
        RecordedRequest replacement = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(replacement);
        assertEquals(alias, replacement.getHeader("X-StudentCC-Participant"));
        assertEquals(Boolean.FALSE, new JSONObject(replacement.getBody().readUtf8()).get("cgpaAtLeastNine"));
        server.enqueue(new MockResponse().setBody("{\"source\":\"studentcc\",\"status\":\"withdrawn\",\"deletedContributions\":0}"));
        writer.withdraw().blockingGet();
        RecordedRequest removal = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(removal);
        assertEquals("DELETE", removal.getMethod());
        assertEquals("/v1/contributions", removal.getPath());
        assertEquals(alias, removal.getHeader("X-StudentCC-Participant"));
    }

    @Test public void incompatibleAcademicAcknowledgmentLeavesUploadUnconfirmed() {
        StudentCCStatisticsClient.AuthenticatedWriter writer = local.writer(UUID.randomUUID().toString(), null).blockingGet();
        server.enqueue(new MockResponse().setBody("{\"source\":\"unicc\",\"status\":\"saved\"}"));
        failure(writer.submitAcademicSummary(true), StudentCCStatisticsClient.FailureKind.INVALID_PAYLOAD);
        server.enqueue(new MockResponse().setBody("{\"source\":\"studentcc\",\"status\":\"recorded\"}"));
        failure(writer.submitAcademicSummary(false), StudentCCStatisticsClient.FailureKind.INVALID_PAYLOAD);
    }

    @Test public void publicAcademicSharingAddsOnlyExplicitClassTallyPermission() throws Exception {
        String alias = UUID.randomUUID().toString();
        StudentCCStatisticsClient.AuthenticatedWriter writer = local.writer(alias, null).blockingGet();
        for (boolean threshold : new boolean[] {true, false}) {
            server.enqueue(new MockResponse().setBody("{\"source\":\"studentcc\",\"status\":\"saved\"}"));
            assertTrue(writer.submitAcademicSummary(threshold, true).blockingGet());
            RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("/v1/academic-summary", request.getPath());
            assertEquals(alias, request.getHeader("X-StudentCC-Participant"));
            JSONObject payload = new JSONObject(request.getBody().readUtf8());
            assertEquals(2, payload.length());
            assertEquals(threshold, payload.getBoolean("cgpaAtLeastNine"));
            assertEquals(Boolean.TRUE, payload.get("shareClassTally"));
        }
        server.enqueue(new MockResponse().setBody("{\"source\":\"studentcc\",\"status\":\"saved\"}"));
        assertTrue(writer.submitAcademicSummary(true, false).blockingGet());
        JSONObject privatePayload = new JSONObject(server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8());
        assertEquals(1, privatePayload.length());
        assertFalse(privatePayload.has("shareClassTally"));
    }

    @Test public void invalidLocalAliasAndWrongStoredIdentityPreventWrites() {
        failure(local.submit(new StudentCCContribution(SCOPE, "BCSE101L", 42), "24BCE9999"),
                StudentCCStatisticsClient.FailureKind.AUTHENTICATION);
        failure(local.writer(UUID.randomUUID().toString(), "local:some-other-account"),
                StudentCCStatisticsClient.FailureKind.AUTHENTICATION);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void releaseLocalAndUntrustedOriginsAreNotConfigured() {
        StudentCCStatisticsClient.TokenProvider never = () -> Single.error(new AssertionError("No authentication expected"));
        assertFalse(new StudentCCStatisticsClient(server.url("/").toString(), true, false, never).isConfigured());
        assertFalse(new StudentCCStatisticsClient("http://192.0.2.10:8766", true, true, never).isConfigured());
        assertFalse(new StudentCCStatisticsClient("https://studentcc.example", true, true, never).isConfigured());
        assertFalse(new StudentCCStatisticsClient("http://127.0.0.1:8766", false, false, never).isConfigured());
        assertFalse(new StudentCCStatisticsClient("https://user:password@studentcc.example", false, false, never).isConfigured());
        assertFalse(new StudentCCStatisticsClient("https://studentcc.example?token=secret", false, false, never).isConfigured());
        assertFalse(new StudentCCStatisticsClient("", false, false, never).isConfigured());
        assertTrue(new StudentCCStatisticsClient("http://10.0.2.2:8766", true, true, never).isConfigured());
        StudentCCStatisticsClient disabled = new StudentCCStatisticsClient(server.url("/").toString(), true, false, never);
        failure(disabled.fetchStatistics(SCOPE), StudentCCStatisticsClient.FailureKind.NOT_CONFIGURED);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void productionPostAndDeleteSendVerifiedTokenAndPerAccountAlias() throws Exception {
        AtomicInteger tokenCalls = new AtomicInteger();
        StudentCCStatisticsClient production = httpsClient(() -> {
            tokenCalls.incrementAndGet();
            return Single.just(new StudentCCStatisticsClient.AuthToken("synthetic-firebase-token", "synthetic-firebase-uid"));
        });
        String alias = UUID.randomUUID().toString();
        server.enqueue(new MockResponse().setBody(available(42.35)));
        StudentCCStatisticsClient.AuthenticatedWriter writer = production.writer(alias, "synthetic-firebase-uid").blockingGet();
        assertEquals("synthetic-firebase-uid", writer.subject);
        assertTrue(writer.submit(new StudentCCContribution(SCOPE, "BCSE101L", 42.35)).blockingGet().hasValues());
        RecordedRequest post = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(post);
        assertEquals("Bearer synthetic-firebase-token", post.getHeader("Authorization"));
        assertEquals(alias, post.getHeader("X-StudentCC-Participant"));
        assertNull(post.getHeader("Cookie"));
        assertEquals(42.35, new JSONObject(post.getBody().readUtf8()).getDouble("total"), 0.00001);
        server.enqueue(new MockResponse().setBody("{\"source\":\"studentcc\",\"status\":\"saved\"}"));
        writer.submitAcademicSummary(true).blockingGet();
        RecordedRequest academic = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(academic);
        assertEquals("/v1/academic-summary", academic.getPath());
        assertEquals("Bearer synthetic-firebase-token", academic.getHeader("Authorization"));
        assertEquals(alias, academic.getHeader("X-StudentCC-Participant"));
        JSONObject academicBody = new JSONObject(academic.getBody().readUtf8());
        assertEquals(1, academicBody.length());
        assertEquals(Boolean.TRUE, academicBody.get("cgpaAtLeastNine"));
        server.enqueue(new MockResponse().setBody("{\"source\":\"studentcc\",\"status\":\"withdrawn\",\"deletedContributions\":1}"));
        assertEquals(Integer.valueOf(1), writer.withdraw().blockingGet());
        RecordedRequest delete = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(delete);
        assertEquals("DELETE", delete.getMethod());
        assertEquals("Bearer synthetic-firebase-token", delete.getHeader("Authorization"));
        assertEquals(alias, delete.getHeader("X-StudentCC-Participant"));
        assertEquals(1, tokenCalls.get());
    }

    @Test public void productionReadNeverRequestsFirebaseToken() throws Exception {
        AtomicInteger tokenCalls = new AtomicInteger();
        StudentCCStatisticsClient production = httpsClient(() -> {
            tokenCalls.incrementAndGet();
            return Single.error(new IllegalStateException("No sharing authentication should be needed"));
        });
        server.enqueue(new MockResponse().setBody(available(42.35)));
        assertTrue(production.fetchStatistics(SCOPE).blockingGet().hasValues());
        RecordedRequest get = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(get);
        assertNull(get.getHeader("Authorization"));
        assertNull(get.getHeader("X-StudentCC-Participant"));
        assertNull(get.getHeader("Cookie"));
        assertEquals(0, tokenCalls.get());
    }

    @Test public void productionSubjectMismatchOrTokenFailurePreventsMutation() {
        StudentCCStatisticsClient production = new StudentCCStatisticsClient("https://studentcc.example/", false, false,
                () -> Single.just(new StudentCCStatisticsClient.AuthToken("synthetic-token", "different-subject")));
        failure(production.writer(UUID.randomUUID().toString(), "original-subject"),
                StudentCCStatisticsClient.FailureKind.AUTHENTICATION);
        StudentCCStatisticsClient unavailable = new StudentCCStatisticsClient("https://studentcc.example/", false, false,
                () -> Single.error(new IllegalStateException("Synthetic authentication failure")));
        failure(unavailable.submit(new StudentCCContribution(SCOPE, "BCSE101L", 42), UUID.randomUUID().toString()),
                StudentCCStatisticsClient.FailureKind.AUTHENTICATION);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void firebaseNetworkFailureIsRetryableAndNeverSendsUnauthenticatedTotals() {
        StudentCCStatisticsClient unavailable = new StudentCCStatisticsClient("https://studentcc.example/", false, false,
                () -> Single.error(new FirebaseNetworkException("Synthetic authentication connection failure")));
        failure(unavailable.submit(new StudentCCContribution(SCOPE, "BCSE101L", 42), UUID.randomUUID().toString()),
                StudentCCStatisticsClient.FailureKind.TEMPORARY);
        failure(unavailable.withdraw(UUID.randomUUID().toString()), StudentCCStatisticsClient.FailureKind.TEMPORARY);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void wrappedFirebaseNetworkFailureRemainsRetryable() {
        StudentCCStatisticsClient unavailable = new StudentCCStatisticsClient("https://studentcc.example/", false, false,
                () -> Single.error(new IllegalStateException("Synthetic wrapper",
                        new FirebaseNetworkException("Synthetic authentication connection failure"))));
        failure(unavailable.writer(UUID.randomUUID().toString(), null), StudentCCStatisticsClient.FailureKind.TEMPORARY);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void revokedFirebaseUserRemainsAnAuthenticationFailureWithoutMutation() {
        StudentCCStatisticsClient revoked = new StudentCCStatisticsClient("https://studentcc.example/", false, false,
                () -> Single.error(new FirebaseAuthInvalidUserException("ERROR_USER_TOKEN_EXPIRED", "Synthetic revoked user")));
        failure(revoked.submit(new StudentCCContribution(SCOPE, "BCSE101L", 42), UUID.randomUUID().toString()),
                StudentCCStatisticsClient.FailureKind.AUTHENTICATION);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void httpAuthFailureAndInvalidWithdrawalAreExplicitErrors() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{}"));
        failure(local.submit(new StudentCCContribution(SCOPE, "BCSE101L", 42), UUID.randomUUID().toString()),
                StudentCCStatisticsClient.FailureKind.AUTHENTICATION);
        server.enqueue(new MockResponse().setBody("{\"source\":\"unicc\",\"status\":\"withdrawn\",\"deletedContributions\":1}"));
        failure(local.withdraw(UUID.randomUUID().toString()), StudentCCStatisticsClient.FailureKind.INVALID_PAYLOAD);
    }

    /** A synthetic localhost certificate trusted only during this test, never in production configuration. */
    private StudentCCStatisticsClient httpsClient(StudentCCStatisticsClient.TokenProvider tokens) throws Exception {
        server.shutdown();
        char[] password = "studentcc-test".toCharArray();
        KeyStore keys = KeyStore.getInstance("JKS");
        try (InputStream input = getClass().getResourceAsStream("/studentcc/localhost-test-only.jks")) {
            assertNotNull("Synthetic TLS fixture is missing", input);
            keys.load(input, password);
        }
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(managers.getKeyManagers(), null, null);
        temporaryTrustStore = File.createTempFile("studentcc-statistics-test-trust", ".jks");
        try (FileOutputStream output = new FileOutputStream(temporaryTrustStore)) { keys.store(output, password); }
        priorTrustStore = System.getProperty("javax.net.ssl.trustStore");
        priorTrustPassword = System.getProperty("javax.net.ssl.trustStorePassword");
        priorTrustType = System.getProperty("javax.net.ssl.trustStoreType");
        changedTrustStore = true;
        System.setProperty("javax.net.ssl.trustStore", temporaryTrustStore.getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", "studentcc-test");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        server = new MockWebServer();
        server.useHttps(context.getSocketFactory(), false);
        server.start();
        return new StudentCCStatisticsClient(server.url("/").toString(), false, false, tokens);
    }

    private static void restoreProperty(String name, String priorValue) {
        if (priorValue == null) System.clearProperty(name);
        else System.setProperty(name, priorValue);
    }
}
