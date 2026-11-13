package com.salmanmalvasi.studentcc.sync;

import org.junit.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.*;

public class CgpaSyncRegressionTest {
    private static final String SUMMARY = "<table><thead><tr><th>Credits Registered</th><th>Credits Earned</th><th>CGPA</th></tr></thead>"
            + "<tbody><tr><td>68</td><td>64.5</td><td>9.12</td></tr></tbody></table>";

    @Test public void summaryWithThHeadersReadsCgpaAndEarnedCredits() {
        Map<String, String> result = VTOPHtmlParsers.parseCgpa("<table></table>" + SUMMARY);
        assertEquals("9.12", result.get("cgpa"));
        assertEquals("64.5", result.get("creditsEarned"));
    }

    @Test public void legacyTdHeadersDoNotRequireCreditsToBeTheFirstColumn() {
        Map<String, String> result = VTOPHtmlParsers.parseCgpa("<table><tr><td>CGPA</td><td>Credits Earned</td></tr>"
                + "<tr><td>8.7</td><td>62</td></tr></table>");
        assertEquals("8.7", result.get("cgpa"));
        assertEquals("62.0", result.get("creditsEarned"));
    }

    @Test public void labelValueRowsCanRepresentTheSameCumulativeSummary() {
        Map<String, String> result = VTOPHtmlParsers.parseCgpa("<table><tr><th>CGPA</th><td>9.01</td></tr>"
                + "<tr><th>Credits Earned</th><td>60</td></tr></table>");
        assertEquals("9.01", result.get("cgpa"));
        assertEquals("60.0", result.get("creditsEarned"));
    }

    @Test public void missingOrInvalidCgpaIsNotConvertedToBelowNine() {
        for (String missing : new String[] {"--", "N/A", "NaN", "Infinity", "10.1", "-1", "9.1 / 100", "9.1 / 9", "9.1 123", "9.1e0"}) {
            Map<String, String> result = VTOPHtmlParsers.parseCgpa("<table><tr><td>CGPA</td><td>Credits Earned</td></tr>"
                    + "<tr><td>" + missing + "</td><td>64</td></tr></table>");
            assertFalse(missing, result.containsKey("cgpa"));
            assertEquals("64.0", result.get("creditsEarned"));
        }
        assertTrue(VTOPHtmlParsers.parseCgpa("<html>Please log in</html>").isEmpty());
    }

    @Test public void formattedCgpaAndSuperscriptFootnotesPreserveTheActualSummaryNumber() {
        for (String value : new String[] {"9.12 / 10", "9.12&nbsp;/&nbsp;10.0", "9.12<sup>1</sup>", "9.12*", "<strong>9.12</strong>†"}) {
            Map<String, String> result = VTOPHtmlParsers.parseCgpa("<table><tr><th>CGPA<sup>*</sup></th>"
                    + "<th>Credits Earned</th></tr><tr><td>" + value + "</td><td>64.5<sup>2</sup></td></tr></table>");
            assertEquals(value, "9.12", result.get("cgpa"));
            assertEquals(value, "64.5", result.get("creditsEarned"));
        }
    }

    @Test public void cgpaNoteAndRegisteredCreditsDoNotBecomeAcademicProof() {
        String html = "<table><tr><td>CGPA is provisional for semester 9</td><td>9</td></tr>"
                + "<tr><td>Credits Registered</td><td>70</td></tr></table>";
        assertTrue(VTOPHtmlParsers.parseCgpa(html).isEmpty());
    }

    @Test public void cumulativeHeadingWithBasisAndNestedSummaryIsAccepted() {
        String html = "<table><tr><td><table><tr><th>Cumulative GPA (Out of 10):</th><td>8.75 / 10</td></tr>"
                + "<tr><th>Total Credits Earned</th><td>62</td></tr></table></td></tr></table>";
        Map<String, String> result = VTOPHtmlParsers.parseCgpa(html);
        assertEquals("8.75", result.get("cgpa"));
        assertEquals("62.0", result.get("creditsEarned"));
    }

    @Test public void jsonWrapperAndArraySummariesPreserveActualNumbers() {
        Map<String, String> nested = VTOPJsonParsers.parseCgpa("{\"data\":{\"cgpaView\":{\"CGPA\":9.12,\"creditEarned\":64.5}}}");
        assertEquals("9.12", nested.get("cgpa"));
        assertEquals("64.5", nested.get("creditsEarned"));
        assertEquals("8.7", VTOPJsonParsers.parseCgpa("{\"data\":[{\"cgpa\":8.7}]}").get("cgpa"));
        assertEquals("0.0", VTOPJsonParsers.parseCgpa("[{\"cgpa\":0}]").get("cgpa"));
        assertFalse(VTOPJsonParsers.parseCgpa("{\"cgpa\":\"--\"}").containsKey("cgpa"));
    }

    @Test public void nativeJsonSummaryStagesEarnedCreditsAlongsideAccountBoundCgpa() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"data\":{\"cgpa\":9.12,\"creditEarned\":64.5}}"));
            server.start();
            SyncContext context = context();
            new NativeSyncBridge(null, server.url("/").toString()).fetchAcademicSummary(context);
            assertEquals("9.12", context.getStagedData().profileAttributes.get("cgpa"));
            assertEquals("64.5", context.getStagedData().profileAttributes.get("creditsEarned"));
            assertEquals("SYNTHETIC_STUDENT", context.getStagedData().profileAttributes.get("cgpaAccount"));
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test public void nativeMissingSummaryFallsBackToGradeHistoryHtml() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{}"));
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody(SUMMARY));
            server.start();
            SyncContext context = context();
            new NativeSyncBridge(null, server.url("/").toString()).fetchAcademicSummary(context);
            assertEquals("9.12", context.getStagedData().profileAttributes.get("cgpa"));
            assertEquals("64.5", context.getStagedData().profileAttributes.get("creditsEarned"));
            RecordedRequest api = server.takeRequest(1, TimeUnit.SECONDS);
            RecordedRequest history = server.takeRequest(1, TimeUnit.SECONDS);
            assertEquals(VTOPEndpoints.CGPA_VIEW, api.getPath());
            assertEquals(VTOPEndpoints.GRADE_HISTORY, history.getPath());
            assertTrue(history.getHeader("Content-Type").contains("application/x-www-form-urlencoded"));
            assertTrue(history.getBody().readUtf8().contains("authorizedID=SYNTHETIC_STUDENT"));
        }
    }

    @Test public void unavailableHistoryRetainsAValidNativeCgpaWithoutInventingCredits() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"cgpa\":8.7}"));
            server.enqueue(new MockResponse().setResponseCode(403));
            server.start();
            SyncContext context = context();
            new NativeSyncBridge(null, server.url("/").toString()).fetchAcademicSummary(context);
            assertEquals("8.7", context.getStagedData().profileAttributes.get("cgpa"));
            assertFalse(context.getStagedData().profileAttributes.containsKey("creditsEarned"));
        }
    }

    @Test public void unknownSummaryDoesNotStageAZeroOrAcademicProof() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"cgpa\":\"--\"}"));
            server.enqueue(new MockResponse().setBody("<html>No grade history available</html>"));
            server.start();
            SyncContext context = context();
            new NativeSyncBridge(null, server.url("/").toString()).fetchAcademicSummary(context);
            assertFalse(context.getStagedData().profileAttributes.containsKey("cgpa"));
            assertFalse(context.getStagedData().profileAttributes.containsKey("cgpaAccount"));
        }
    }

    private static SyncContext context() {
        SyncContext context = new SyncContext(null, "CH20262601", null);
        context.setSessionTokens("synthetic-csrf", "SYNTHETIC_STUDENT", "JSESSIONID=synthetic");
        return context;
    }
}
