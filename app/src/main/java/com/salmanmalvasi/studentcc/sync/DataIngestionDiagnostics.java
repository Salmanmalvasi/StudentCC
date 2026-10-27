package com.salmanmalvasi.studentcc.sync;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Debug-only diagnostic logger and response analyzer for VTOP synchronization.
 * Inspects response structure, format, content-types, and tracking reasons for
 * dropped/rejected records without leaking sensitive student or auth data.
 */
public final class DataIngestionDiagnostics {
    private static final String TAG = "DataIngestionDiag";

    public static class RejectionTracker {
        private final Map<String, Integer> rejectionCounts = new HashMap<>();

        public synchronized void recordRejection(String reason) {
            if (reason == null || reason.isEmpty()) reason = "unknown";
            rejectionCounts.put(reason, rejectionCounts.getOrDefault(reason, 0) + 1);
        }

        public synchronized int getTotalRejections() {
            int total = 0;
            for (int count : rejectionCounts.values()) {
                total += count;
            }
            return total;
        }

        public synchronized String getSummary() {
            if (rejectionCounts.isEmpty()) return "none";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> entry : rejectionCounts.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(entry.getKey()).append(": ").append(entry.getValue());
            }
            return sb.toString();
        }

        public synchronized Map<String, Integer> getCounts() {
            return new HashMap<>(rejectionCounts);
        }
    }

    public static class ResponseDiagnostic {
        public final String endpoint;
        public final String method;
        public final int statusCode;
        public final String contentType;
        public final int bodyLength;
        public final String detectedFormat;
        public final String structureSummary;
        public final boolean isAuthOrErrorPage;

        public ResponseDiagnostic(String endpoint, String method, int statusCode, String contentType,
                                  int bodyLength, String detectedFormat, String structureSummary,
                                  boolean isAuthOrErrorPage) {
            this.endpoint = endpoint;
            this.method = method;
            this.statusCode = statusCode;
            this.contentType = contentType != null ? contentType : "unknown";
            this.bodyLength = bodyLength;
            this.detectedFormat = detectedFormat;
            this.structureSummary = structureSummary;
            this.isAuthOrErrorPage = isAuthOrErrorPage;
        }

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "[%s %s] Status: %d | Type: %s | Bytes: %d | Format: %s | Structure: %s | Auth/Error: %b",
                    method, endpoint, statusCode, contentType, bodyLength, detectedFormat, structureSummary, isAuthOrErrorPage);
        }
    }

    private DataIngestionDiagnostics() {}

    /**
     * Inspects and classifies an HTTP response payload without printing body content or PII.
     */
    public static ResponseDiagnostic inspectResponse(String endpoint, String method, int statusCode,
                                                    String contentType, String body) {
        if (body == null) body = "";
        int length = body.length();
        String trimmed = body.trim();
        String lower = trimmed.toLowerCase(Locale.US);

        boolean isAuthOrError = statusCode == 401 || statusCode == 403 ||
                lower.contains("session expired") ||
                lower.contains("not authorized") ||
                lower.contains("login to continue") ||
                (lower.contains("vtop: login") && lower.contains("passwd")) ||
                lower.contains("invalid session") ||
                lower.contains("server error") ||
                lower.contains("access denied");

        String detectedFormat;
        String structureSummary;

        if (trimmed.isEmpty()) {
            detectedFormat = "EMPTY";
            structureSummary = "No body content";
        } else if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                   (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            detectedFormat = "JSON";
            structureSummary = summarizeJsonStructure(trimmed);
        } else if (trimmed.contains("<html") || trimmed.contains("<table") || trimmed.contains("<div") || trimmed.contains("<!doctype")) {
            detectedFormat = "HTML";
            structureSummary = summarizeHtmlStructure(trimmed);
        } else {
            detectedFormat = "RAW_TEXT";
            structureSummary = "Length: " + length;
        }

        ResponseDiagnostic diag = new ResponseDiagnostic(endpoint, method, statusCode, contentType,
                length, detectedFormat, structureSummary, isAuthOrError);
        Log.d(TAG, diag.toString());
        return diag;
    }

    /**
     * Summarizes JSON root structure (top-level keys or array length) safely.
     */
    private static String summarizeJsonStructure(String json) {
        try {
            if (json.startsWith("[")) {
                JSONArray arr = new JSONArray(json);
                return "Root Array with " + arr.length() + " items";
            } else {
                JSONObject obj = new JSONObject(json);
                List<String> keys = new ArrayList<>();
                Iterator<String> keyIter = obj.keys();
                while (keyIter.hasNext()) {
                    keys.add(keyIter.next());
                }
                Collections.sort(keys);
                return "Keys: " + keys.toString();
            }
        } catch (Exception e) {
            return "Malformed JSON (" + e.getMessage() + ")";
        }
    }

    /**
     * Summarizes HTML tables, classes, and IDs safely.
     */
    private static String summarizeHtmlStructure(String html) {
        try {
            Document doc = Jsoup.parse(html);
            Elements tables = doc.select("table");
            if (tables.isEmpty()) {
                Elements title = doc.select("title");
                String titleText = title.isEmpty() ? "No title" : title.text().trim();
                return "No tables, Title: '" + titleText + "'";
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(tables.size()).append(" table(s): ");
                for (int i = 0; i < Math.min(3, tables.size()); i++) {
                    Element t = tables.get(i);
                    String id = t.id();
                    String cls = t.className();
                    int rowCount = t.select("tr").size();
                    if (i > 0) sb.append(", ");
                    sb.append("[id='").append(id).append("' class='").append(cls).append("' rows=").append(rowCount).append("]");
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return "HTML parsing error (" + e.getMessage() + ")";
        }
    }

    /**
     * Logs the complete lifecycle of a data task stage.
     */
    public static void logTaskAudit(String taskName, int extracted, int rejected,
                                    String rejectionSummary, int staged) {
        Log.i(TAG, String.format(Locale.US,
                "==> Task %s Ingestion Audit: Extracted=%d | Rejected=%d (%s) | Staged=%d",
                taskName, extracted, rejected, rejectionSummary, staged));
    }
}
