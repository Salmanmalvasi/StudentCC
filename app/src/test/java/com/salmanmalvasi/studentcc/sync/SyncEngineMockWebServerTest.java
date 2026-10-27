package com.salmanmalvasi.studentcc.sync;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic test harness using MockWebServer.
 * Tests realistic VTOP HTTP traffic, session expiration, error codes, and header parity.
 */
public class SyncEngineMockWebServerTest {

    private MockWebServer server;
    private VTOPOkHttpNetworkClient client;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new VTOPOkHttpNetworkClient(server.url("/").toString());
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void testSuccessfulPostWithHeadersAndCookies() throws Exception {
        String htmlPayload = "<html><body><select id=\"semesterSubId\"><option value=\"SEM01\">Fall 2024</option></select></body></html>";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody(htmlPayload));

        Map<String, String> params = new HashMap<>();
        params.put("_csrf", "test-token-123");
        params.put("authorizedID", "21BCE1001");

        VTOPOkHttpNetworkClient.HttpResponse response = client.post("/academics/common/StudentTimeTableChn", params, "JSESSIONID=MOCK_SESSION_123");

        Assert.assertTrue(response.isSuccessful);
        Assert.assertFalse(response.isSessionExpired());

        RecordedRequest request = server.takeRequest();
        Assert.assertEquals("POST", request.getMethod());
        Assert.assertEquals("JSESSIONID=MOCK_SESSION_123", request.getHeader("Cookie"));
        Assert.assertTrue(request.getHeader("User-Agent").contains("Chrome"));
        Assert.assertTrue(request.getBody().readUtf8().contains("_csrf=test-token-123"));

        Map<String, String> semesters = VTOPHtmlParsers.parseSemesters(response.body);
        Assert.assertEquals("SEM01", semesters.get("Fall 2024"));
    }

    @Test
    public void testSessionExpiredDetection() throws Exception {
        String unauthorizedHtml = "<html><body><h3>You are not authorized to view this page. Please log in.</h3></body></html>";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(unauthorizedHtml));

        VTOPOkHttpNetworkClient.HttpResponse response = client.post("/academics/common/StudentTimeTableChn", new HashMap<>(), "JSESSIONID=EXPIRED");

        Assert.assertTrue("Should detect session expiration from body content", response.isSessionExpired());
    }

    @Test
    public void testHttp500ServerErrorHandling() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        VTOPOkHttpNetworkClient.HttpResponse response = client.post("/processViewStudentAttendance", new HashMap<>(), "JSESSIONID=VALID");

        Assert.assertFalse("HTTP 500 must not be flagged as successful", response.isSuccessful);
        Assert.assertEquals(500, response.code);
    }
}
