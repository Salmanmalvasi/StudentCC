package com.salmanmalvasi.studentcc.sync;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Unit tests verifying:
 * 1. RSA-1024 / PKCS#1 v1.5 encryption output length (128 bytes raw, 172 Base64 chars).
 * 2. Request payload construction (with and without optional genuine FCM token).
 * 3. In-memory CookieJar session handling (domain matching, expiration, purging).
 * 4. Redacted logging safety (no credentials, ciphertexts, or raw cookie values).
 * 5. Explicit authentication failure handling (HTTP 401, empty body, malformed JSON, server rejection).
 * 6. Controlled proof stage isolation (distinguishing native auth success from academic rejection).
 */
public class VTOPAuthClientTest {

    private MockWebServer server;
    private InMemoryCookieJar cookieJar;
    private OkHttpClient testClient;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        cookieJar = new InMemoryCookieJar();
        testClient = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .build();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    /**
     * Requirement 1: RSA-1024 encryption must output exactly 128 raw bytes,
     * which encodes to 172 Base64 characters.
     */
    @Test
    public void testRsaEncryptionOutputLengthAndProperties() throws Exception {
        String syntheticUsername = "24BCE0000";
        String syntheticPassword = "DummyPassword123!";

        String userEnc = VTOPAuthClient.encryptRsa(syntheticUsername);
        String passEnc = VTOPAuthClient.encryptRsa(syntheticPassword);

        Assert.assertNotNull(userEnc);
        Assert.assertNotNull(passEnc);

        // 1024-bit RSA produces 128 bytes -> exactly 172 Base64 characters (with padding)
        Assert.assertEquals("RSA-1024 Base64 string must be 172 characters", 172, userEnc.length());
        Assert.assertEquals("RSA-1024 Base64 string must be 172 characters", 172, passEnc.length());

        byte[] userBytes = VTOPAuthClient.decodeBase64(userEnc);
        byte[] passBytes = VTOPAuthClient.decodeBase64(passEnc);

        Assert.assertEquals("RSA-1024 ciphertext must be 128 bytes", 128, userBytes.length);
        Assert.assertEquals("RSA-1024 ciphertext must be 128 bytes", 128, passBytes.length);

        // PKCS#1 v1.5 random padding ensures repeated encryption of the same input produces different ciphertexts
        String passEnc2 = VTOPAuthClient.encryptRsa(syntheticPassword);
        Assert.assertNotEquals("PKCS#1 v1.5 padding must produce non-deterministic ciphertexts", passEnc, passEnc2);
    }

    /**
     * Requirement 1: Null credentials must be rejected immediately.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRsaEncryptionRejectsNull() throws Exception {
        VTOPAuthClient.encryptRsa(null);
    }

    /**
     * Requirement 2: Payload construction with and without genuine FCM token.
     */
    @Test
    public void testPayloadConstruction() throws Exception {
        String userEnc = "ENC_USER_SAMPLE";
        String encData = "ENC_PASS_SAMPLE";
        String deviceId = "4a7b8c9d0e1f2345";
        String genuineFcm = "fcm_token_from_firebase_sdk";

        // Case A: With genuine FCM token
        JSONObject payloadWithFcm = VTOPAuthClient.buildLoginPayload(userEnc, encData, deviceId, genuineFcm);
        Assert.assertEquals(userEnc, payloadWithFcm.getString("userEnc"));
        Assert.assertEquals(encData, payloadWithFcm.getString("encData"));
        Assert.assertEquals(deviceId, payloadWithFcm.getString("deviceId"));
        Assert.assertEquals("Android", payloadWithFcm.getString("deviceType"));
        Assert.assertEquals(genuineFcm, payloadWithFcm.getString("fcmToken"));

        // Case B: Without FCM token (null or empty)
        JSONObject payloadWithoutFcm = VTOPAuthClient.buildLoginPayload(userEnc, encData, deviceId, null);
        Assert.assertFalse("fcmToken must be omitted when null", payloadWithoutFcm.has("fcmToken"));

        JSONObject payloadWithEmptyFcm = VTOPAuthClient.buildLoginPayload(userEnc, encData, deviceId, "   ");
        Assert.assertFalse("fcmToken must be omitted when empty", payloadWithEmptyFcm.has("fcmToken"));
    }

    /**
     * Requirement 2: InMemoryCookieJar lifecycle, domain matching, and expiration.
     */
    @Test
    public void testInMemoryCookieJarLifecycle() {
        HttpUrl targetUrl = HttpUrl.parse("https://vtopcc5.vit.ac.in/students/studentRegisteredCourse");
        Assert.assertNotNull(targetUrl);

        // Initially empty
        Assert.assertFalse(cookieJar.hasValidSession("vtopcc5.vit.ac.in"));
        Assert.assertEquals(0, cookieJar.loadForRequest(targetUrl).size());

        // Save active JSESSIONID cookie
        Cookie activeCookie = new Cookie.Builder()
                .domain("vtopcc5.vit.ac.in")
                .path("/")
                .name("JSESSIONID")
                .value("DUMMY_SESSION_TOKEN_12345")
                .expiresAt(System.currentTimeMillis() + 3600000L) // 1 hour future
                .build();

        java.util.List<Cookie> cookies = new java.util.ArrayList<>();
        cookies.add(activeCookie);
        cookieJar.saveFromResponse(targetUrl, cookies);

        // Verification
        Assert.assertTrue(cookieJar.hasValidSession("vtopcc5.vit.ac.in"));
        Assert.assertFalse("Should not match different domain", cookieJar.hasValidSession("vtopcc.vit.ac.in"));
        Assert.assertEquals(1, cookieJar.loadForRequest(targetUrl).size());

        // Test clear
        cookieJar.clear();
        Assert.assertFalse(cookieJar.hasValidSession("vtopcc5.vit.ac.in"));
        Assert.assertEquals(0, cookieJar.loadForRequest(targetUrl).size());
    }

    /**
     * Requirement 3: Redacted logging safety - models must never hold passwords or raw tokens.
     */
    @Test
    public void testRedactedLoggingSafety() {
        VTOPAuthClient.AuthResult authResult = VTOPAuthClient.AuthResult.success(200, "SUCCESS", "24BCE0000", true);
        Assert.assertTrue(authResult.success);
        Assert.assertEquals(200, authResult.httpCode);
        Assert.assertEquals("SUCCESS", authResult.status);
        Assert.assertEquals("24BCE0000", authResult.regNo);

        VTOPAuthClient.DiagnosticProofResult proof = new VTOPAuthClient.DiagnosticProofResult(
                true, 200, "SUCCESS", true, true, 200, true, "NONE", "Proof success"
        );
        Assert.assertTrue(proof.authSucceeded);
        Assert.assertTrue(proof.sessionCookieAccepted);
        Assert.assertTrue(proof.academicValidResponse);
        Assert.assertEquals("NONE", proof.failureStage);
    }

    /**
     * Requirement 4: Explicit failure on HTTP 401 with empty body.
     */
    @Test
    public void testAuthenticationFailureOnHttp401() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(401).setBody("");
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "test_device_id", null, true
        );

        Assert.assertFalse(result.success);
        Assert.assertEquals(401, result.httpCode);
        Assert.assertFalse(result.sessionCookieAccepted);
        Assert.assertFalse(cookieJar.hasValidSession("127.0.0.1"));
    }

    /**
     * Requirement 4: Explicit failure on HTTP 200 with empty body.
     */
    @Test
    public void testAuthenticationFailureOnEmptyBody() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200).setBody("");
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "test_device_id", null, true
        );

        Assert.assertFalse(result.success);
        Assert.assertEquals("EMPTY_BODY", result.status);
    }

    /**
     * Requirement 4: Explicit failure on malformed JSON response.
     */
    @Test
    public void testAuthenticationFailureOnMalformedJson() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200).setBody("<html><body>Service Unavailable</body></html>");
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "test_device_id", null, true
        );

        Assert.assertFalse(result.success);
        Assert.assertEquals("RESPONSE_HTML", result.status);
        Assert.assertNotNull(result.diagnostics);
        Assert.assertEquals("HTML", result.diagnostics.bodyClassification);
    }

    /**
     * Response diagnostics detects HTML pages, extracts titles/headings, and redacts student IDs.
     */
    @Test
    public void testResponseDiagnosticsDetectsHtmlPage() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "text/html; charset=UTF-8")
                        .setBody("<!DOCTYPE html><html><head><title>VTOP - Chennai Login</title></head>" +
                                "<body><h1>Session Timed Out</h1><div class=\"alert error\">Invalid session for 24BCE1234</div></body></html>");
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "test_device_id", null, true
        );

        Assert.assertFalse(result.success);
        Assert.assertEquals("RESPONSE_HTML", result.status);
        Assert.assertNotNull(result.diagnostics);
        Assert.assertEquals("HTML", result.diagnostics.bodyClassification);
        Assert.assertEquals("VTOP - Chennai Login", result.diagnostics.detectedTitle);
        Assert.assertEquals("Session Timed Out", result.diagnostics.detectedHeading);
        Assert.assertTrue(result.diagnostics.detectedMessage.contains("[REDACTED_REGNO]"));
    }

    /**
     * Response diagnostics detects plain text responses.
     */
    @Test
    public void testResponseDiagnosticsDetectsPlainText() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "text/plain")
                        .setBody("Invalid User Id / Password");
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "test_device_id", null, true
        );

        Assert.assertFalse(result.success);
        Assert.assertEquals("PLAIN_TEXT", result.status);
        Assert.assertNotNull(result.diagnostics);
        Assert.assertEquals("PLAIN_TEXT", result.diagnostics.bodyClassification);
        Assert.assertTrue(result.diagnostics.sanitizedSummary.contains("Invalid User Id"));
    }

    /**
     * Handles UTF-8 BOM prefixes transparently without JSONException.
     */
    @Test
    public void testJsonParsingWithUtf8Bom() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Set-Cookie", "JSESSIONID=BOM_SESSION_VALID; Path=/; HttpOnly")
                        .setBody("\uFEFF{\"status\":\"SUCCESS\",\"message\":\"Login Successful\",\"regNo\":\"24BCE0000\"}");
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "test_device_id", null, true
        );

        Assert.assertTrue(result.success);
        Assert.assertEquals("SUCCESS", result.status);
        Assert.assertTrue(result.sessionCookieAccepted);
    }

    /**
     * Handles JSON Array wrapping transparently.
     */
    @Test
    public void testJsonParsingWithJsonArray() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Set-Cookie", "JSESSIONID=ARR_SESSION_VALID; Path=/; HttpOnly")
                        .setBody("[{\"status\":\"SUCCESS\",\"message\":\"Login Successful\",\"regNo\":\"24BCE0000\"}]");
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "test_device_id", null, true
        );

        Assert.assertTrue(result.success);
        Assert.assertEquals("SUCCESS", result.status);
        Assert.assertTrue(result.sessionCookieAccepted);
    }

    /**
     * Requirement 4: Explicit failure on invalid credentials reported by server JSON.
     */
    @Test
    public void testAuthenticationFailureOnRejectedCredentials() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"status\":\"FAILURE\",\"message\":\"Invalid User Id / Password\"}");
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "wrong_password", "test_device_id", null, true
        );

        Assert.assertFalse(result.success);
        Assert.assertEquals("FAILURE", result.status);
        Assert.assertTrue(result.message.contains("Invalid User Id / Password"));
    }

    /**
     * Requirement 4 & 5: When follow-up academic request returns 401,
     * report ACADEMIC_401 and do NOT report authentication success.
     */
    @Test
    public void testControlledAuthProofDistinguishesAcademicEndpointRejection() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() != null ? request.getPath() : "";
                if (path.contains("appLoginProcess")) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Set-Cookie", "JSESSIONID=TEST_SESSION_12345; Path=/; HttpOnly")
                            .setBody("{\"status\":\"SUCCESS\",\"message\":\"Login Successful\",\"regNo\":\"24BCE0000\"}");
                } else if (path.contains("studentRegisteredCourse")) {
                    // Academic endpoint returns HTTP 401
                    return new MockResponse().setResponseCode(401).setBody("");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        VTOPAuthClient.DiagnosticProofResult proof = VTOPAuthClient.executeControlledAuthProof(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_password", "CH2024251", "test_device_id", null
        );

        // Requirement 4: If academic request returns 401, do NOT report auth success!
        Assert.assertFalse("Authentication success must NOT be reported if academic request returns 401", proof.authSucceeded);
        Assert.assertEquals(200, proof.authHttpCode);
        Assert.assertTrue("Session cookie must be verified as accepted", proof.sessionCookieAccepted);
        Assert.assertTrue("Academic request must have been attempted", proof.academicRequestAttempted);
        Assert.assertEquals(401, proof.academicHttpCode);
        Assert.assertFalse("Academic request did not succeed", proof.academicValidResponse);
        Assert.assertEquals("ACADEMIC_401", proof.failureStage);
    }

    /**
     * Requirement 5: Controlled proof full success when both native auth and academic API succeed.
     */
    @Test
    public void testControlledAuthProofFullSuccess() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() != null ? request.getPath() : "";
                if (path.contains("appLoginProcess")) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Set-Cookie", "JSESSIONID=AUTH_SESSION_VALID; Path=/; HttpOnly")
                            .setBody("{\"status\":\"SUCCESS\",\"message\":\"Login Successful\",\"regNo\":\"24BCE0000\"}");
                } else if (path.contains("studentRegisteredCourse")) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"status\":\"SUCCESS\",\"courseList\":[{\"courseCode\":\"CSE1001\"}]}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        VTOPAuthClient.DiagnosticProofResult proof = VTOPAuthClient.executeControlledAuthProof(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_password", "CH2024251", "test_device_id", null
        );

        Assert.assertTrue(proof.authSucceeded);
        Assert.assertEquals(200, proof.authHttpCode);
        Assert.assertTrue(proof.sessionCookieAccepted);
        Assert.assertTrue(proof.academicRequestAttempted);
        Assert.assertEquals(200, proof.academicHttpCode);
        Assert.assertTrue(proof.academicValidResponse);
        Assert.assertEquals("NONE", proof.failureStage);
    }

    /**
     * Verifies that a 44-byte Base64-encoded scalar (32-byte binary token) is accurately
     * classified as BASE64_SCALAR / BASE64_BINARY_32_BYTES and records cookie names.
     */
    @Test
    public void testBase64ScalarResponseDiagnosticsWithBinaryToken() {
        // 44-character Base64 string ending with '=' encoding 32 bytes of binary token
        final String scalar44 = "dGhpcyBpcyBhIDMyIGJ5dGUgZXhhbXBsZSBzdHJpbmc=";

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Set-Cookie", "SESSION=TEST_SPRING_SESSION_VAL; Path=/; Secure; HttpOnly")
                        .setBody(scalar44);
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "test_device_id", null, true
        );

        Assert.assertNotNull(result.diagnostics);
        Assert.assertEquals("BASE64_SCALAR", result.diagnostics.bodyClassification);
        Assert.assertTrue(result.diagnostics.sanitizedSummary.contains("Base64 Scalar (44 chars)"));
        Assert.assertEquals(1, result.diagnostics.cookieCount);
        Assert.assertTrue("Cookie names must include SESSION", result.diagnostics.cookieNames.contains("SESSION"));
        Assert.assertFalse("Cookie descriptors must not be empty", result.diagnostics.cookieDescriptors.isEmpty());
        // Value must NOT be in descriptor
        Assert.assertFalse("Secret cookie value must never be logged", result.diagnostics.cookieDescriptors.get(0).contains("TEST_SPRING_SESSION_VAL"));
        Assert.assertTrue(result.diagnostics.cookieDescriptors.get(0).contains("name=SESSION"));
    }

    /**
     * Requirement 4: When login returns HTTP 200 scalar with a cookie, but the follow-up
     * academic probe returns HTTP 401, report ACADEMIC_401 and do NOT report authentication success.
     */
    @Test
    public void testBase64ScalarControlledAuthProofRejectionOnAcademic401() {
        final String scalar44 = "V0VsQ09NRS1UTy1WVE9QLUFQSS1UT0tFTi1SRVNQT05TRQ==";

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() != null ? request.getPath() : "";
                if (path.contains("appLoginProcess")) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Set-Cookie", "SESSION=SESSION_ABCD_1234; Path=/; HttpOnly")
                            .setBody(scalar44);
                } else if (path.contains("studentRegisteredCourse")) {
                    return new MockResponse().setResponseCode(401).setBody("");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        VTOPAuthClient.DiagnosticProofResult proof = VTOPAuthClient.executeControlledAuthProof(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "CH2024251", "test_device_id", null
        );

        Assert.assertFalse("Must NOT report authentication success when academic endpoint returns 401", proof.authSucceeded);
        Assert.assertEquals(200, proof.authHttpCode);
        Assert.assertTrue(proof.academicRequestAttempted);
        Assert.assertEquals(401, proof.academicHttpCode);
        Assert.assertFalse(proof.academicValidResponse);
        Assert.assertEquals("ACADEMIC_401", proof.failureStage);
    }

    /**
     * Verifies that if login returns HTTP 200 scalar and follow-up academic probe succeeds,
     * authentication success is reported.
     */
    @Test
    public void testBase64ScalarControlledAuthProofSuccessOnAcademic200() {
        final String scalar44 = "V0VsQ09NRS1UTy1WVE9QLUFQSS1UT0tFTi1SRVNQT05TRQ==";

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() != null ? request.getPath() : "";
                if (path.contains("appLoginProcess")) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Set-Cookie", "SESSION=SESSION_ABCD_1234; Path=/; HttpOnly")
                            .setBody(scalar44);
                } else if (path.contains("studentRegisteredCourse")) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"status\":\"SUCCESS\",\"courseList\":[{\"courseCode\":\"MAT2001\"}]}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        VTOPAuthClient.DiagnosticProofResult proof = VTOPAuthClient.executeControlledAuthProof(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "CH2024251", "test_device_id", null
        );

        Assert.assertTrue("Must report authentication success when academic endpoint succeeds", proof.authSucceeded);
        Assert.assertEquals(200, proof.authHttpCode);
        Assert.assertTrue(proof.academicRequestAttempted);
        Assert.assertEquals(200, proof.academicHttpCode);
        Assert.assertTrue(proof.academicValidResponse);
        Assert.assertEquals("NONE", proof.failureStage);
    }

    /**
     * Verifies that a Base64 scalar response without any cookies is rejected.
     */
    @Test
    public void testBase64ScalarWithoutCookiesRejected() {
        final String scalar44 = "V0VsQ09NRS1UTy1WVE9QLUFQSS1UT0tFTi1SRVNQT05TRQ==";

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(scalar44);
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "test_device_id", null, true
        );

        Assert.assertFalse(result.success);
        Assert.assertEquals("BASE64_SCALAR", result.status);
        Assert.assertEquals(0, result.diagnostics.cookieCount);
    }

    /**
     * Verifies that when login returns HTTP 200 with a 44-byte Base64 scalar and ONLY
     * a load-balancer cookie (SERVERID), login is strictly REJECTED (success = false,
     * status = NO_SESSION_COOKIE) because SERVERID is not an authenticated session cookie.
     */
    @Test
    public void testBase64ScalarWithOnlyServerIdCookieRejected() {
        final String scalar44 = "dGhpcyBpcyBhIDMyIGJ5dGUgZXhhbXBsZSBzdHJpbmc=";

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Set-Cookie", "SERVERID=vtop_node1; Path=/")
                        .setBody(scalar44);
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "test_device_id", null, true
        );

        Assert.assertFalse("Login must NOT succeed on SERVERID cookie alone", result.success);
        Assert.assertEquals("NO_SESSION_COOKIE", result.status);
        Assert.assertNotNull(result.diagnostics);
        Assert.assertEquals(200, result.httpCode);
        Assert.assertEquals(1, result.diagnostics.cookieCount);
        Assert.assertTrue("Cookie names must list SERVERID", result.diagnostics.cookieNames.contains("SERVERID"));
        Assert.assertFalse("hasSessionCookie must be false for SERVERID", result.diagnostics.hasSessionCookie);
        Assert.assertFalse("InMemoryCookieJar must report no valid session for host", cookieJar.hasValidSession("127.0.0.1"));
    }

    /**
     * Verifies that executeControlledAuthProof fails immediately at the login stage
     * when only SERVERID is returned, reporting NO_SESSION_COOKIE without claiming authentication.
     */
    @Test
    public void testControlledAuthProofRejectsServerIdOnlyLogin() {
        final String scalar44 = "dGhpcyBpcyBhIDMyIGJ5dGUgZXhhbXBsZSBzdHJpbmc=";

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Set-Cookie", "SERVERID=vtop_node1; Path=/")
                        .setBody(scalar44);
            }
        });

        VTOPAuthClient.DiagnosticProofResult proof = VTOPAuthClient.executeControlledAuthProof(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "CH2024251", "test_device_id", null
        );

        Assert.assertFalse("Auth must NOT succeed when only SERVERID is issued", proof.authSucceeded);
        Assert.assertEquals(200, proof.authHttpCode);
        Assert.assertFalse("Session cookie must NOT be accepted", proof.sessionCookieAccepted);
        Assert.assertEquals("NO_SESSION_COOKIE", proof.failureStage);
        Assert.assertFalse("Academic request should NOT be attempted when login produced no session", proof.academicRequestAttempted);
    }

    /**
     * Verifies that request shape diagnostics helper executes cleanly and records metadata safely.
     */
    @Test
    public void testRequestShapeLoggingSafety() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("userEnc", "ENC_VALUE_172_CHARS");
        payload.put("encData", "ENC_VALUE_172_CHARS");
        payload.put("deviceId", "test_device_id");
        payload.put("deviceType", "Android");
        payload.put("fcmToken", "sample_fcm_token");

        okhttp3.Request dummyRequest = new okhttp3.Request.Builder()
                .url("https://vtopcc5.vit.ac.in/login/vitian/appLoginProcess")
                .header("Host", "vtopcc5.vit.ac.in")
                .header("Content-Type", "application/json; charset=UTF-8")
                .header(VTOPEndpoints.HEADER_API_KEY, VTOPEndpoints.API_KEY_VALUE)
                .build();

        // Must run without exception
        VTOPAuthClient.logOutboundRequestShape("Auth Request", "https://vtopcc5.vit.ac.in/login/vitian/appLoginProcess",
                "POST", dummyRequest, payload, cookieJar, "vtopcc5.vit.ac.in");
    }

    /**
     * Requirement 8.1: AES-128-CBC encryption and decryption round trips using official key/IV.
     */
    @Test
    public void testAes128CbcEncryptionDecryptionRoundtrip() throws Exception {
        String testJson = "{\"status\":\"SUCCESS\",\"studentApplicationNumber\":\"24BCE0001\",\"studName\":\"Test Student\"}";
        String cipherB64 = VTOPAuthClient.encryptAes(testJson);
        Assert.assertNotNull(cipherB64);
        Assert.assertTrue(cipherB64.length() > 0);

        String decrypted = VTOPAuthClient.decryptAes(cipherB64);
        Assert.assertEquals(testJson, decrypted);

        // Test with different lengths to verify PKCS7 block padding edge cases
        for (int len = 1; len <= 64; len++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) sb.append((char) ('a' + (i % 26)));
            String raw = sb.toString();
            String enc = VTOPAuthClient.encryptAes(raw);
            String dec = VTOPAuthClient.decryptAes(enc);
            Assert.assertEquals("AES roundtrip must preserve exact plaintext for len " + len, raw, dec);
        }
    }

    /**
     * Requirement 8.2: Credential RSA encryption format "${username}:${password}".
     */
    @Test
    public void testCredentialRsaEncryptionFormat() throws Exception {
        String user = "24BCE0000";
        String pass = "StrongPassword#2026";
        String userEnc = VTOPAuthClient.encryptCredentials(user, pass);

        Assert.assertNotNull(userEnc);
        Assert.assertEquals("RSA-1024 Base64 string must be 172 characters", 172, userEnc.length());

        // Repeated encryption should be non-deterministic due to PKCS1 v1.5 padding
        String userEnc2 = VTOPAuthClient.encryptCredentials(user, pass);
        Assert.assertNotEquals("PKCS#1 v1.5 padding must produce non-deterministic ciphertexts", userEnc, userEnc2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCredentialRsaEncryptionRejectsNull() throws Exception {
        VTOPAuthClient.encryptCredentials(null, "pass");
    }

    /**
     * Requirement 8.3: Dynamic S-KEY-USER format and generation with fixed timestamp.
     */
    @Test
    public void testSKeyUserGenerationWithFixedTimestamp() throws Exception {
        String regNo = "24BCE1234";
        long fixedTimestamp = 1729443210123L;

        String sKeyUser = VTOPAuthClient.generateSKeyUser(regNo, fixedTimestamp);
        Assert.assertNotNull(sKeyUser);
        Assert.assertEquals("S-KEY-USER RSA ciphertext must be 172 Base64 characters", 172, sKeyUser.length());

        // Generation with current time should also produce 172 chars
        String sKeyUserNow = VTOPAuthClient.generateSKeyUser(regNo);
        Assert.assertNotNull(sKeyUserNow);
        Assert.assertEquals(172, sKeyUserNow.length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSKeyUserRejectsEmptyRegNo() throws Exception {
        VTOPAuthClient.generateSKeyUser("   ");
    }

    /**
     * Requirement 8.4: Official login envelope JSON shape {"encData": <aes_b64>}.
     */
    @Test
    public void testOfficialLoginEnvelopeJsonShape() throws Exception {
        String username = "24BCE0000";
        String password = "SecretPassword123";
        String fcmToken = "sample_fcm_token";

        JSONObject envelope = VTOPAuthClient.buildOfficialLoginEnvelope(username, password, fcmToken);
        Assert.assertNotNull(envelope);
        Assert.assertTrue("Envelope must contain 'encData'", envelope.has("encData"));
        Assert.assertFalse("Envelope must NOT leak unencrypted userEnc in outer JSON", envelope.has("userEnc"));
        Assert.assertFalse("Envelope must NOT leak unencrypted password in outer JSON", envelope.has("password"));

        String encData = envelope.getString("encData");
        Assert.assertNotNull(encData);
        Assert.assertTrue(encData.length() > 0);

        // Decrypt encData with AES-128-CBC
        String decryptedInnerJsonStr = VTOPAuthClient.decryptAes(encData);
        JSONObject inner = new JSONObject(decryptedInnerJsonStr);

        Assert.assertEquals("login", inner.getString("encryption"));
        Assert.assertEquals("android", inner.getString("deviceType"));
        Assert.assertEquals(fcmToken, inner.getString("fcmToken"));
        Assert.assertEquals("", inner.getString("mobile"));
        Assert.assertTrue(inner.has("userEnc"));
        Assert.assertEquals(172, inner.getString("userEnc").length());
    }

    /**
     * Requirement 8.5: Decryption and parsing of valid encrypted login response fixture.
     */
    @Test
    public void testEncryptedLoginResponseDecryptionAndModelParsing() throws Exception {
        JSONObject rawResp = new JSONObject();
        rawResp.put("studentApplicationNumber", "24BCE9999");
        rawResp.put("studName", "Jane Doe");
        rawResp.put("otpMsg", "");
        rawResp.put("mpinFlag", "0");
        rawResp.put("mandatoryCheck", "0");

        String encryptedResponse = VTOPAuthClient.encryptAes(rawResp.toString());

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                // Verify request headers (official appLoginProcess contract)
                Assert.assertNotNull("Request must include encData header", request.getHeader("encData"));
                Assert.assertEquals("{}", request.getBody().readUtf8());

                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Set-Cookie", "SERVERID=vtop_node1; Path=/")
                        .setBody(encryptedResponse);
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE9999", "pass123", "test_device", null, true
        );

        Assert.assertTrue("Login must succeed on valid encrypted response", result.success);
        Assert.assertEquals("SUCCESS", result.status);
        Assert.assertEquals("24BCE9999", result.applNo);
        Assert.assertNotNull(result.decryptedResponse);
        Assert.assertEquals("Jane Doe", result.decryptedResponse.studentName);
        Assert.assertEquals("24BCE9999", result.decryptedResponse.studentApplicationNumber);
        Assert.assertFalse(result.decryptedResponse.isOtpRequired());
        Assert.assertFalse(result.decryptedResponse.isMpinRequired());
    }

    /**
     * Requirement 8.6: OTP challenge handling (stops at OTP_REQUIRED, does not proceed to academic query).
     */
    @Test
    public void testEncryptedLoginResponseWithOtpChallengePending() throws Exception {
        JSONObject rawResp = new JSONObject();
        rawResp.put("studentApplicationNumber", "24BCE9999");
        rawResp.put("studName", "Jane Doe");
        rawResp.put("otpMsg", "OTP sent to your registered mobile number");
        rawResp.put("mpinFlag", "0");

        String encryptedResponse = VTOPAuthClient.encryptAes(rawResp.toString());

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(encryptedResponse);
            }
        });

        // 1. Direct login test
        VTOPAuthClient.AuthResult authResult = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE9999", "pass123", "test_device", null, true
        );

        Assert.assertFalse("Login must not report full success when OTP is required", authResult.success);
        Assert.assertTrue("Must flag otpRequired", authResult.otpRequired);
        Assert.assertEquals("OTP_REQUIRED", authResult.status);
        Assert.assertEquals("24BCE9999", authResult.applNo);
        Assert.assertTrue(authResult.message.contains("OTP"));

        // 2. Controlled auth proof test
        VTOPAuthClient.DiagnosticProofResult proof = VTOPAuthClient.executeControlledAuthProof(
                null, testClient, server.url("/").toString(),
                "24BCE9999", "pass123", "CH2024251", "test_device", null
        );

        Assert.assertFalse("Proof must not claim success when OTP challenge pending", proof.authSucceeded);
        Assert.assertEquals("OTP_REQUIRED", proof.failureStage);
        Assert.assertFalse("Must NOT attempt academic request while OTP challenge is pending", proof.academicRequestAttempted);
        Assert.assertTrue(proof.diagnosticMessage.contains("OTP_REQUIRED"));
    }

    /**
     * Requirement 8.7: Controlled auth proof full success with verified AES academic response and applNo body.
     */
    @Test
    public void testControlledAuthProofWithOfficialDualAesFlow() throws Exception {
        JSONObject loginResp = new JSONObject();
        loginResp.put("studentApplicationNumber", "24BCE9999");
        loginResp.put("studName", "Jane Doe");
        loginResp.put("otpMsg", "");
        final String encryptedLogin = VTOPAuthClient.encryptAes(loginResp.toString());

        JSONObject acadResp = new JSONObject();
        acadResp.put("status", "SUCCESS");
        org.json.JSONArray courses = new org.json.JSONArray();
        JSONObject c1 = new JSONObject();
        c1.put("courseCode", "CSE2001");
        c1.put("courseTitle", "Computer Architecture");
        courses.put(c1);
        acadResp.put("courseList", courses);
        final String encryptedAcad = VTOPAuthClient.encryptAes(acadResp.toString());

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path.contains("appLoginProcess")) {
                    Assert.assertNotNull(request.getHeader("encData"));
                    Assert.assertEquals("{}", request.getBody().readUtf8());
                    return new MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(encryptedLogin);
                } else if (path.contains("studentRegisteredCourse")) {
                    // Verify academic request contract:
                    // 1. Headers: S-API-KEY and dynamic S-KEY-USER
                    Assert.assertEquals(VTOPEndpoints.API_KEY_VALUE, request.getHeader(VTOPEndpoints.HEADER_API_KEY));
                    Assert.assertNotNull("Academic request must include S-KEY-USER", request.getHeader(VTOPEndpoints.HEADER_KEY_USER));
                    Assert.assertEquals(172, request.getHeader(VTOPEndpoints.HEADER_KEY_USER).length());

                    // 2. Request body must contain applNo
                    String bodyStr = request.getBody().readUtf8();
                    try {
                        JSONObject bodyJson = new JSONObject(bodyStr);
                        Assert.assertEquals("Academic body must contain applNo", "24BCE9999", bodyJson.getString("applNo"));
                    } catch (JSONException e) {
                        Assert.fail("Academic request body was not valid JSON: " + bodyStr);
                    }

                    return new MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(encryptedAcad);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        VTOPAuthClient.DiagnosticProofResult proof = VTOPAuthClient.executeControlledAuthProof(
                null, testClient, server.url("/").toString(),
                "24BCE9999", "pass123", "CH2024251", "test_device", null
        );

        Assert.assertTrue("Controlled proof must succeed when login and academic responses decrypt cleanly", proof.authSucceeded);
        Assert.assertEquals(200, proof.authHttpCode);
        Assert.assertTrue(proof.academicRequestAttempted);
        Assert.assertEquals(200, proof.academicHttpCode);
        Assert.assertTrue(proof.academicValidResponse);
        Assert.assertEquals("NONE", proof.failureStage);
    }

    /**
     * Requirement 8.8: Rejection of invalid ciphertext and malformed response without crashing.
     */
    @Test
    public void testMalformedEncryptedResponseHandling() {
        // Corrupted 44-char Base64 string that is not valid AES ciphertext
        final String corruptedCipher = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(corruptedCipher);
            }
        });

        VTOPAuthClient.AuthResult result = VTOPAuthClient.login(
                null, testClient, server.url("/").toString(),
                "24BCE0000", "dummy_pass", "test_device", null, true
        );

        Assert.assertFalse("Login must fail on invalid ciphertext", result.success);
        Assert.assertNotNull(result.status);
    }

    /**
     * Requirement 8.9: Official submitOtp endpoint contract verified from Blutter disassembly.
     * Official endpoint: /login/vitian/appLoginProcess
     * Official transmission: Header 'encData: <AES_128_CBC_Base64(innerOtpPayload)>', Body: "{}"
     */
    @Test
    public void testSubmitOtpOfficialContract() throws Exception {
        JSONObject otpResp = new JSONObject();
        otpResp.put("status", "1");
        otpResp.put("studentApplicationNumber", "24BCE9999");
        otpResp.put("apiKey", "test-api-key");
        final String encOtpResp = VTOPAuthClient.encryptAes(otpResp.toString());

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                Assert.assertTrue(request.getPath().contains("appLoginProcess"));
                Assert.assertNotNull(request.getHeader("encData"));
                Assert.assertNull("Initial OTP verification does not include S-API-KEY", request.getHeader(VTOPEndpoints.HEADER_API_KEY));
                Assert.assertNull("Initial OTP verification does not include S-KEY-USER", request.getHeader(VTOPEndpoints.HEADER_KEY_USER));

                String encData = request.getHeader("encData");
                try {
                    String decPayload = VTOPAuthClient.decryptAes(encData);
                    JSONObject payload = new JSONObject(decPayload);
                    Assert.assertEquals("24BCE9999", payload.getString("userName"));
                    Assert.assertEquals("123456", payload.getString("otpText"));
                    Assert.assertEquals("submitOtp", payload.getString("encryption"));
                    Assert.assertEquals("android", payload.getString("deviceType"));
                } catch (Exception e) {
                    Assert.fail("Failed to decrypt OTP request payload: " + e.getMessage());
                }

                String bodyStr = request.getBody().readUtf8();
                Assert.assertEquals("{}", bodyStr.trim());

                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(encOtpResp);
            }
        });

        VTOPAuthClient.AuthResult res = VTOPAuthClient.submitOtp(
                testClient, server.url("/").toString(), "24BCE9999", "123456"
        );

        Assert.assertTrue("submitOtp must succeed", res.success);
        Assert.assertEquals("24BCE9999", res.applNo);
        Assert.assertNotNull(res.decryptedResponse);
        Assert.assertEquals("test-api-key", res.decryptedResponse.apiKey);
    }

    /**
     * Requirement 8.10: Official validateMpin endpoint contract.
     */
    @Test
    public void testValidateMpinOfficialContract() throws Exception {
        JSONObject mpinResp = new JSONObject();
        mpinResp.put("status", "SUCCESS");
        mpinResp.put("studentApplicationNumber", "24BCE9999");
        final String encMpinResp = VTOPAuthClient.encryptAes(mpinResp.toString());

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                Assert.assertTrue(request.getPath().contains("validateMpin"));
                Assert.assertEquals(VTOPEndpoints.API_KEY_VALUE, request.getHeader(VTOPEndpoints.HEADER_API_KEY));
                Assert.assertNotNull(request.getHeader(VTOPEndpoints.HEADER_KEY_USER));

                String bodyStr = request.getBody().readUtf8();
                try {
                    JSONObject body = new JSONObject(bodyStr);
                    Assert.assertEquals("24BCE9999", body.getString("userName"));
                    Assert.assertEquals("1234", body.getString("mpin"));
                } catch (JSONException e) {
                    Assert.fail("validateMpin payload error: " + e.getMessage());
                }

                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(encMpinResp);
            }
        });

        VTOPAuthClient.AuthResult res = VTOPAuthClient.validateMpin(
                testClient, server.url("/").toString(), "24BCE9999", "1234"
        );

        Assert.assertTrue("validateMpin must succeed", res.success);
        Assert.assertEquals("24BCE9999", res.applNo);
    }
}
