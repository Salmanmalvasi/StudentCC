package com.salmanmalvasi.studentcc.sync;

import android.util.Log;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;

/**
 * Native, secure OkHttp network client for VTOP data fetching.
 * Preserves strict TLS certificate verification and session cookies extracted from WebView.
 */
public class VTOPOkHttpNetworkClient {
    private static final String TAG = "VTOPNetClient";
    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36";

    // Shared connection pool across synchronization tasks to reuse warm connections
    private static final ConnectionPool SHARED_CONNECTION_POOL = new ConnectionPool(5, 5, TimeUnit.MINUTES);

    private final OkHttpClient client;
    private final String baseUrl;

    public static class HttpResponse {
        public final int code;
        public final String body;
        public final boolean isSuccessful;
        public final String contentType;

        public HttpResponse(int code, String body) {
            this(code, body, "text/plain");
        }

        public HttpResponse(int code, String body, String contentType) {
            this.code = code;
            this.body = body != null ? body : "";
            this.isSuccessful = (code >= 200 && code < 300);
            this.contentType = contentType != null ? contentType : "unknown";
        }

        public boolean isSessionExpired() {
            String lower = body.toLowerCase();
            return code == 401 || code == 403 ||
                    lower.contains("not authorized") ||
                    lower.contains("session expired") ||
                    lower.contains("login to continue") ||
                    (lower.contains("vtop: login") && lower.contains("passwd"));
        }
    }

    public VTOPOkHttpNetworkClient() {
        this(VTOPEndpoints.BASE_URL);
    }

    public VTOPOkHttpNetworkClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(3500, TimeUnit.MILLISECONDS) // 3.5s connection setup timeout per IP attempt
                .readTimeout(25, TimeUnit.SECONDS) // Generous read timeout for large marks/attendance tables
                .writeTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true) // Automatically failover across all resolved DNS routes
                .connectionPool(SHARED_CONNECTION_POOL)
                .dns(new VTOPAdaptiveDns()) // Prioritizes verified reachable IPs dynamically
                .eventListenerFactory(VTOPNetworkDiagnosticsListener.FACTORY) // Network diagnostics
                .build();
    }

    public VTOPOkHttpNetworkClient(OkHttpClient customClient, String baseUrl) {
        this.client = customClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String userAgent = DEFAULT_USER_AGENT;
    private String apiKey = VTOPEndpoints.API_KEY_VALUE;

    public void setUserAgent(String userAgent) {
        if (userAgent != null && !userAgent.isEmpty()) {
            this.userAgent = userAgent;
        }
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    private void logRequestAttempt(String method, String endpoint, int attemptNum,
                                   String contentType, Map<String, String> params,
                                   String cookieHeader, boolean hasApiKey) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.US, "[Attempt #%d] %s %s | Content-Type: %s | Params: [",
                attemptNum, method, endpoint, contentType));
        if (params != null && !params.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) sb.append(", ");
                boolean isPresent = entry.getValue() != null && !entry.getValue().trim().isEmpty();
                sb.append(entry.getKey()).append(": ").append(isPresent ? "present" : "empty");
                first = false;
            }
        } else {
            sb.append("none");
        }
        sb.append("] | Headers: [");
        sb.append("Cookie: ").append(cookieHeader != null && !cookieHeader.trim().isEmpty() ? "present" : "absent");
        sb.append(", S-API-KEY: ").append(hasApiKey ? "present" : "absent");
        sb.append(", S-KEY-USER: absent");
        sb.append("]");
        Log.d(TAG, sb.toString());
    }

    private void logResponseAttempt(String method, String endpoint, int attemptNum,
                                    HttpResponse res, String nextActionReason) {
        String classification;
        if (res.code == 401) {
            classification = "Unauthorized / unmapped route (HTTP 401)";
        } else if (res.code == 403) {
            classification = "Forbidden (HTTP 403)";
        } else if (res.code == 415) {
            classification = "Unsupported Media Type (HTTP 415)";
        } else if (res.code == 404) {
            classification = "Not Found (HTTP 404)";
        } else if (res.code >= 200 && res.code < 300) {
            classification = "Success (" + (res.body.isEmpty() ? "empty body" : res.body.length() + " chars") + ")";
        } else {
            classification = "HTTP " + res.code;
        }

        int byteCount = 0;
        try {
            byteCount = res.body != null ? res.body.getBytes("UTF-8").length : 0;
        } catch (Exception ignored) {
            byteCount = res.body != null ? res.body.length() : 0;
        }

        Log.d(TAG, String.format(java.util.Locale.US,
                "[Response #%d] %s %s | Status: %d | Content-Type: %s | Bytes: %d | Classification: %s | Next: %s",
                attemptNum, method, endpoint, res.code, res.contentType, byteCount,
                classification, nextActionReason));
    }

    public HttpResponse post(String relativePath, Map<String, String> formParams, String cookieHeader) throws IOException {
        return postAttempt(relativePath, formParams, cookieHeader, 1);
    }

    public HttpResponse postAttempt(String relativePath, Map<String, String> formParams, String cookieHeader, int attemptNum) throws IOException {
        String fullUrl = relativePath.startsWith("http") ? relativePath : (baseUrl + "/" + (relativePath.startsWith("/") ? relativePath.substring(1) : relativePath));

        logRequestAttempt("POST", relativePath, attemptNum, "application/x-www-form-urlencoded; charset=UTF-8",
                formParams, cookieHeader, apiKey != null && !apiKey.isEmpty());

        FormBody.Builder formBuilder = new FormBody.Builder();
        if (formParams != null) {
            for (Map.Entry<String, String> entry : formParams.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    formBuilder.add(entry.getKey(), entry.getValue());
                }
            }
        }
        RequestBody requestBody = formBuilder.build();

        Request.Builder requestBuilder = new Request.Builder()
                .url(fullUrl)
                .post(requestBody)
                .header("User-Agent", userAgent != null ? userAgent : DEFAULT_USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/content")
                .header("Origin", baseUrl)
                .header("Accept", "*/*")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        // Add S-API-KEY header for VTOP API endpoints
        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header(VTOPEndpoints.HEADER_API_KEY, apiKey);
        }

        if (cookieHeader != null && !cookieHeader.trim().isEmpty()) {
            requestBuilder.header("Cookie", cookieHeader);
        }

        Request request = requestBuilder.build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            ResponseBody responseBody = response.body();
            String bodyString = responseBody != null ? responseBody.string() : "";
            String contentType = response.header("Content-Type");
            HttpResponse res = new HttpResponse(code, bodyString, contentType);
            logResponseAttempt("POST", relativePath, attemptNum, res, "Completed");
            return res;
        }
    }

    public HttpResponse postJson(String relativePath, String jsonString, String cookieHeader) throws IOException {
        HttpResponse res = postJsonAttempt(relativePath, jsonString, null, cookieHeader, 1);
        logResponseAttempt("POST", relativePath, 1, res, "Completed");
        return res;
    }

    public HttpResponse postJson(String relativePath, Map<String, String> params, String cookieHeader) throws IOException {
        HttpResponse res = postJsonAttempt(relativePath, null, params, cookieHeader, 1);
        logResponseAttempt("POST", relativePath, 1, res, "Completed");
        return res;
    }

    public HttpResponse postJsonAttempt(String relativePath, String jsonString, Map<String, String> params, String cookieHeader, int attemptNum) throws IOException {
        String fullUrl = relativePath.startsWith("http") ? relativePath : (baseUrl + "/" + (relativePath.startsWith("/") ? relativePath.substring(1) : relativePath));

        logRequestAttempt("POST", relativePath, attemptNum, "application/json; charset=UTF-8",
                params, cookieHeader, apiKey != null && !apiKey.isEmpty());

        if (jsonString == null) {
            JSONObject json = new JSONObject();
            if (params != null) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        try {
                            json.put(entry.getKey(), entry.getValue());
                        } catch (Exception ignored) {}
                    }
                }
            }
            jsonString = json.toString();
        }

        RequestBody requestBody = RequestBody.create(
                okhttp3.MediaType.parse("application/json; charset=utf-8"),
                jsonString
        );

        Request.Builder requestBuilder = new Request.Builder()
                .url(fullUrl)
                .post(requestBody)
                .header("User-Agent", userAgent != null ? userAgent : DEFAULT_USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Content-Type", "application/json; charset=UTF-8");

        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header(VTOPEndpoints.HEADER_API_KEY, apiKey);
        }

        if (cookieHeader != null && !cookieHeader.trim().isEmpty()) {
            requestBuilder.header("Cookie", cookieHeader);
        }

        Request request = requestBuilder.build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            ResponseBody responseBody = response.body();
            String bodyString = responseBody != null ? responseBody.string() : "";
            String contentType = response.header("Content-Type");
            HttpResponse res = new HttpResponse(code, bodyString, contentType);
            return res;
        }
    }

    /**
     * Executes adaptive POST request: attempts JSON body format first (matching official app Dio client).
     * Only tries a serialization fallback (form-urlencoded) when the response provides evidence
     * of a media-type problem (such as HTTP 415 Unsupported Media Type).
     * Never automatically retries a 401 with a different content-type.
     */
    public HttpResponse postAdaptive(String relativePath, Map<String, String> params, String cookieHeader) throws IOException {
        HttpResponse res = postJsonAttempt(relativePath, null, params, cookieHeader, 1);
        if (res.isSuccessful && !res.body.isEmpty() && !res.isSessionExpired()) {
            logResponseAttempt("POST", relativePath, 1, res, "Completed successfully");
            return res;
        }

        // Only retry with form-urlencoded if server explicitly signals a media-type mismatch (HTTP 415)
        if (res.code == 415) {
            logResponseAttempt("POST", relativePath, 1, res,
                    "Retrying with application/x-www-form-urlencoded due to HTTP 415 Unsupported Media Type");
            try {
                HttpResponse formRes = postAttempt(relativePath, params, cookieHeader, 2);
                logResponseAttempt("POST", relativePath, 2, formRes, "Fallback completed");
                return formRes;
            } catch (IOException e) {
                Log.w(TAG, "[Attempt #2] Fallback failed for " + relativePath + ": " + e.getMessage());
                throw e;
            }
        }

        // For HTTP 401 or other non-415 codes, DO NOT retry with a different content type
        String nextReason = (res.code == 401)
                ? "No retry: HTTP 401 indicates authorization failure or unmapped route; content-type fallback is not applicable"
                : "No retry: status " + res.code + " does not indicate a media-type mismatch";
        logResponseAttempt("POST", relativePath, 1, res, nextReason);
        return res;
    }

    public HttpResponse get(String relativePath, String cookieHeader) throws IOException {
        String fullUrl = relativePath.startsWith("http") ? relativePath : (baseUrl + "/" + (relativePath.startsWith("/") ? relativePath.substring(1) : relativePath));

        Request.Builder requestBuilder = new Request.Builder()
                .url(fullUrl)
                .get()
                .header("User-Agent", userAgent != null ? userAgent : DEFAULT_USER_AGENT)
                .header("Accept", "*/*");

        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header(VTOPEndpoints.HEADER_API_KEY, apiKey);
        }

        if (cookieHeader != null && !cookieHeader.trim().isEmpty()) {
            requestBuilder.header("Cookie", cookieHeader);
        }

        Request request = requestBuilder.build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            ResponseBody responseBody = response.body();
            String bodyString = responseBody != null ? responseBody.string() : "";
            String contentType = response.header("Content-Type");
            return new HttpResponse(code, bodyString, contentType);
        }
    }

    public OkHttpClient getOkHttpClient() {
        return client;
    }
}
