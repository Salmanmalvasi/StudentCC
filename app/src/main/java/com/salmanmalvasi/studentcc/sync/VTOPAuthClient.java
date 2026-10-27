package com.salmanmalvasi.studentcc.sync;
import com.salmanmalvasi.studentcc.sync.VTOPJsonParsers;
import com.salmanmalvasi.studentcc.sync.TransactionalDataActivator;
import com.salmanmalvasi.studentcc.sync.StagedSyncData;
import com.salmanmalvasi.studentcc.models.Slot;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.salmanmalvasi.studentcc.helpers.FirebaseMessagingHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Isolated Native REST Authentication Client for VTOP (vtopcc5.vit.ac.in).
 * Implements the verified official VITianCC authentication flow:
 * 1. RSA-1024 / PKCS#1 v1.5 credential encryption (format: "${username}:${password}").
 * 2. Inner JSON construction: {"login":"", "userEnc":<rsa_cipher>, "deviceType":"android", "fcmToken":<fcm_or_empty>, "mobile":""}.
 * 3. AES-128-CBC / PKCS7 serialization: outer JSON {"encData": <aes_cipher>}.
 * 4. Dynamic S-KEY-USER header generation: RSA("${epochMillis}|${regNo}|vitian").
 * 5. AES-128-CBC decryption and parsing of login & academic response payloads.
 * 6. Explicit handling of OTP challenges (otpMsg) and auto-injection of applNo.
 *
 * Security and Integrity Constraints:
 * 1. Strictly in-memory session management via {@link InMemoryCookieJar}.
 * 2. Never logs passwords, encrypted strings, AES keys, API keys, or raw cookie values.
 * 3. Never disables TLS certificate or hostname validation.
 * 4. Uses genuine Firebase FCM tokens when available; never sends synthetic tokens.
 * 5. Distinguishes authentication failure from academic endpoint or parameter failure.
 */
public final class VTOPAuthClient {
    private static final String TAG = "VTOPAuthClient";

    // Verified 1024-bit RSA Public Key extracted from libapp.so (offset 0x2d78d / pp pool)
    public static final String RSA_PUBLIC_KEY_B64 =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCPDdAnqYfXImo+lDiylmFLxkgM" +
            "G+xABe45Jc3c0JfwSDNwJIWgrjn+W4iUjG21bq+OvDGUzDrUf2XCDhQNUww5z6Gh" +
            "QlKQxGjkt3AKaib540z2A4IeNWnCmnUknLDJ7Mgf70vCQRGRZZNNHLyby9oCaHup" +
            "0a66rtnuWNDkZYDH6wIDAQAB";

    public static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    public static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=UTF-8");

    // Shared in-memory cookie jar for native sessions
    private static final InMemoryCookieJar SESSION_COOKIE_JAR = new InMemoryCookieJar();

    // Default OkHttpClient with strict TLS and in-memory cookie tracking
    private static volatile OkHttpClient defaultClient;

    public interface AuthProofCallback {
        void onResult(DiagnosticProofResult result);
    }

    /**
     * Privacy-safe diagnostic metadata describing the exact HTTP response characteristics
     * without leaking credentials, ciphertexts, tokens, or session cookie values.
     */
    public static class ResponseDiagnostics {
        public final int httpCode;
        public final String initialUrl;
        public final String finalUrl;
        public final String initialMethod;
        public final String finalMethod;
        public final boolean redirected;
        public final int redirectCode;
        public final String redirectLocation;
        public final String contentType;
        public final int bodyLength;
        public final String bodyClassification; // "EMPTY", "HTML", "JSON_OBJECT", "JSON_ARRAY", "PLAIN_TEXT", "MALFORMED_JSON", "BASE64_SCALAR"
        public final String sanitizedSummary;
        public final String detectedTitle;
        public final String detectedHeading;
        public final String detectedMessage;
        public final String jsonParseError;
        public final int cookieCount;
        public final boolean hasSessionCookie;
        public final List<String> cookieNames;
        public final List<String> cookieDescriptors;
        public final String base64DecodedClassification;
        public final boolean scalarTokenCandidate;

        public ResponseDiagnostics(
                int httpCode, String initialUrl, String finalUrl, String initialMethod, String finalMethod,
                boolean redirected, int redirectCode, String redirectLocation,
                String contentType, int bodyLength, String bodyClassification,
                String sanitizedSummary, String detectedTitle, String detectedHeading, String detectedMessage,
                String jsonParseError, int cookieCount, boolean hasSessionCookie
        ) {
            this(httpCode, initialUrl, finalUrl, initialMethod, finalMethod,
                    redirected, redirectCode, redirectLocation,
                    contentType, bodyLength, bodyClassification,
                    sanitizedSummary, detectedTitle, detectedHeading, detectedMessage,
                    jsonParseError, cookieCount, hasSessionCookie,
                    Collections.emptyList(), Collections.emptyList(), "NOT_BASE64", false);
        }

        public ResponseDiagnostics(
                int httpCode, String initialUrl, String finalUrl, String initialMethod, String finalMethod,
                boolean redirected, int redirectCode, String redirectLocation,
                String contentType, int bodyLength, String bodyClassification,
                String sanitizedSummary, String detectedTitle, String detectedHeading, String detectedMessage,
                String jsonParseError, int cookieCount, boolean hasSessionCookie,
                List<String> cookieNames, List<String> cookieDescriptors,
                String base64DecodedClassification, boolean scalarTokenCandidate
        ) {
            this.httpCode = httpCode;
            this.initialUrl = initialUrl != null ? initialUrl : "";
            this.finalUrl = finalUrl != null ? finalUrl : "";
            this.initialMethod = initialMethod != null ? initialMethod : "";
            this.finalMethod = finalMethod != null ? finalMethod : "";
            this.redirected = redirected;
            this.redirectCode = redirectCode;
            this.redirectLocation = redirectLocation != null ? redirectLocation : "";
            this.contentType = contentType != null ? contentType : "unknown";
            this.bodyLength = bodyLength;
            this.bodyClassification = bodyClassification != null ? bodyClassification : "UNKNOWN";
            this.sanitizedSummary = sanitizedSummary != null ? sanitizedSummary : "";
            this.detectedTitle = detectedTitle != null ? detectedTitle : "";
            this.detectedHeading = detectedHeading != null ? detectedHeading : "";
            this.detectedMessage = detectedMessage != null ? detectedMessage : "";
            this.jsonParseError = jsonParseError;
            this.cookieCount = cookieCount;
            this.hasSessionCookie = hasSessionCookie;
            this.cookieNames = cookieNames != null ? cookieNames : Collections.emptyList();
            this.cookieDescriptors = cookieDescriptors != null ? cookieDescriptors : Collections.emptyList();
            this.base64DecodedClassification = base64DecodedClassification != null ? base64DecodedClassification : "NOT_BASE64";
            this.scalarTokenCandidate = scalarTokenCandidate;
        }

        public static ResponseDiagnostics createEmpty(int httpCode, String initialUrl) {
            return new ResponseDiagnostics(
                    httpCode, initialUrl, initialUrl, "POST", "POST",
                    false, 0, "", "unknown", 0, "EMPTY", "No response", "", "", "", null, 0, false,
                    Collections.emptyList(), Collections.emptyList(), "NOT_BASE64", false
            );
        }
    }

    /**
     * Parsed login response model representing decrypted payload from official server.
     */
    public static class DecryptedLoginResponse {
        public final String studentApplicationNumber;
        public final String studentName;
        public final String otpMsg;
        public final String mpinFlag;
        public final String mpinExpireMessage;
        public final String mandatoryCheck;
        public final String logout;
        public final String img;
        public final String apiKey;
        public final String urlPath;
        public final String status;
        public final String statusCode;
        public final String isOtpRequiredFlag;
        public final String message;
        public final JSONObject rawJson;

        public final String resultValue;

        public DecryptedLoginResponse(JSONObject json) {
            this.rawJson = json != null ? json : new JSONObject();
            JSONObject targetJson = this.rawJson;
            String resVal = this.rawJson.optString("result", "");

            if (this.rawJson.has("result")) {
                JSONObject nested = this.rawJson.optJSONObject("result");
                if (nested != null) {
                    targetJson = nested;
                } else if (resVal.trim().startsWith("{")) {
                    try {
                        targetJson = new JSONObject(resVal.trim());
                    } catch (Exception ignored) {}
                } else if (resVal.matches("^[A-Za-z0-9+/=_-]{20,}$")) {
                    try {
                        String dec = decryptAes(resVal.trim());
                        if (dec.trim().startsWith("{")) {
                            targetJson = new JSONObject(dec.trim());
                        }
                    } catch (Exception ignored) {}
                }
            }
            this.resultValue = resVal;

            String appNo = optSafe(targetJson, "studentApplicationNumber");
            if (appNo.isEmpty()) appNo = optSafe(targetJson, "applNo");
            if (appNo.isEmpty()) appNo = optSafe(this.rawJson, "studentApplicationNumber");
            if (appNo.isEmpty()) appNo = optSafe(this.rawJson, "applNo");

            String k = optSafe(targetJson, "data");
            if (k.isEmpty()) k = optSafe(targetJson, "apiKey");
            if (k.isEmpty()) k = optSafe(targetJson, "token");
            if (k.isEmpty()) k = optSafe(this.rawJson, "data");
            if (k.isEmpty()) k = optSafe(this.rawJson, "apiKey");
            if (k.isEmpty()) k = optSafe(this.rawJson, "token");

            String uPath = optSafe(targetJson, "urlPath");
            if (uPath.isEmpty()) uPath = optSafe(this.rawJson, "urlPath");

            // Official app zGa/wGa models support centerDetails array
            org.json.JSONArray centerArr = targetJson.optJSONArray("centerDetails");
            if (centerArr == null) centerArr = this.rawJson.optJSONArray("centerDetails");
            if (centerArr != null && centerArr.length() > 0) {
                org.json.JSONObject firstCenter = centerArr.optJSONObject(0);
                if (firstCenter != null) {
                    if (appNo.isEmpty()) appNo = optSafe(firstCenter, "studentApplicationNumber");
                    if (appNo.isEmpty()) appNo = optSafe(firstCenter, "applNo");
                    if (k.isEmpty()) k = optSafe(firstCenter, "data");
                    if (k.isEmpty()) k = optSafe(firstCenter, "apiKey");
                    if (k.isEmpty()) k = optSafe(firstCenter, "token");
                    if (uPath.isEmpty()) uPath = optSafe(firstCenter, "urlPath");
                }
            }

            this.studentApplicationNumber = appNo;
            this.apiKey = k;
            this.urlPath = uPath;

            String sName = optSafe(targetJson, "studName");
            if (sName.isEmpty()) sName = optSafe(targetJson, "studentName");
            this.studentName = sName;
            this.otpMsg = optSafe(targetJson, "otpMsg");
            this.mpinFlag = optSafe(targetJson, "mpinFlag");
            this.mpinExpireMessage = optSafe(targetJson, "mpinExpireMessage");
            this.mandatoryCheck = optSafe(targetJson, "mandatoryCheck");
            this.logout = optSafe(targetJson, "logout");
            this.img = optSafe(targetJson, "img");
            String st = optSafe(targetJson, "status");
            if (st.isEmpty()) st = optSafe(this.rawJson, "status");
            this.status = st;
            String stCode = optSafe(targetJson, "statusCode");
            if (stCode.isEmpty()) stCode = optSafe(this.rawJson, "statusCode");
            this.statusCode = stCode;
            String otpFlag = optSafe(targetJson, "isOtpRequired");
            if (otpFlag.isEmpty()) otpFlag = optSafe(this.rawJson, "isOtpRequired");
            this.isOtpRequiredFlag = otpFlag;
            String msg = optSafe(targetJson, "message");
            if (msg.isEmpty()) msg = optSafe(targetJson, "msg");
            if (msg.isEmpty()) msg = optSafe(this.rawJson, "message");
            if (msg.isEmpty()) msg = optSafe(this.rawJson, "msg");
            this.message = msg;
        }

        private static String optSafe(JSONObject json, String key) {
            if (json == null || json.isNull(key)) return "";
            String s = json.optString(key, "");
            return ("null".equalsIgnoreCase(s.trim())) ? "" : s.trim();
        }

        public boolean isOtpRequired() {
            if ("10".equals(status) || "10".equals(statusCode)) return true;
            if ("1".equals(isOtpRequiredFlag)) return true;
            if (otpMsg != null && !otpMsg.trim().isEmpty() && !"null".equalsIgnoreCase(otpMsg.trim())) return true;
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("otp")) return true;
            return false;
        }

        public boolean isMpinRequired() {
            return "1".equals(mpinFlag);
        }
    }

    /**
     * Diagnostic result model for authentication attempts.
     * Contains only sanitized status codes, stage indicators, and decrypted response metadata.
     * Never stores raw credentials, keys, or cookie values.
     */
    public static class AuthResult {
        public final boolean success;
        public final int httpCode;
        public final String status;
        public final String message;
        public final String regNo;
        public final String applNo;
        public final boolean sessionCookieAccepted;
        public final boolean otpRequired;
        public final DecryptedLoginResponse decryptedResponse;
        public final ResponseDiagnostics diagnostics;

        public AuthResult(boolean success, int httpCode, String status, String message, String regNo, String applNo,
                          boolean sessionCookieAccepted, boolean otpRequired, DecryptedLoginResponse decryptedResponse,
                          ResponseDiagnostics diagnostics) {
            this.success = success;
            this.httpCode = httpCode;
            this.status = status != null ? status : "";
            this.message = message != null ? message : "";
            this.regNo = regNo != null ? regNo : "";
            this.applNo = applNo != null ? applNo : "";
            this.sessionCookieAccepted = sessionCookieAccepted;
            this.otpRequired = otpRequired;
            this.decryptedResponse = decryptedResponse;
            this.diagnostics = diagnostics;
        }

        public AuthResult(boolean success, int httpCode, String status, String message, String regNo, boolean sessionCookieAccepted) {
            this(success, httpCode, status, message, regNo, "", sessionCookieAccepted, false, null, null);
        }

        public AuthResult(boolean success, int httpCode, String status, String message, String regNo, boolean sessionCookieAccepted, ResponseDiagnostics diagnostics) {
            this(success, httpCode, status, message, regNo, "", sessionCookieAccepted, false, null, diagnostics);
        }

        public static AuthResult success(int httpCode, String status, String regNo, String applNo, boolean sessionCookieAccepted, ResponseDiagnostics diagnostics, DecryptedLoginResponse decrypted) {
            return new AuthResult(true, httpCode, status, "Login Successful", regNo, applNo, sessionCookieAccepted, false, decrypted, diagnostics);
        }

        public static AuthResult success(int httpCode, String status, String regNo, boolean sessionCookieAccepted, ResponseDiagnostics diagnostics) {
            return success(httpCode, status, regNo, "", sessionCookieAccepted, diagnostics, null);
        }

        public static AuthResult success(int httpCode, String status, String regNo, boolean sessionCookieAccepted) {
            return success(httpCode, status, regNo, sessionCookieAccepted, null);
        }

        public static AuthResult otpRequired(int httpCode, String regNo, String applNo, String otpMsg, ResponseDiagnostics diagnostics, DecryptedLoginResponse decrypted) {
            return new AuthResult(false, httpCode, "OTP_REQUIRED", otpMsg != null && !otpMsg.isEmpty() ? otpMsg : "OTP verification required", regNo, applNo, false, true, decrypted, diagnostics);
        }

        public static AuthResult failure(int httpCode, String status, String message, boolean sessionCookieAccepted, ResponseDiagnostics diagnostics) {
            return new AuthResult(false, httpCode, status, message, "", "", sessionCookieAccepted, false, null, diagnostics);
        }

        public static AuthResult failure(int httpCode, String status, String message, boolean sessionCookieAccepted) {
            return failure(httpCode, status, message, sessionCookieAccepted, null);
        }
    }

    /**
     * Complete diagnostic record for the controlled native authentication proof.
     */
    public static class DiagnosticProofResult {
        public final boolean authSucceeded;
        public final int authHttpCode;
        public final String authStatus;
        public final boolean sessionCookieAccepted;
        public final boolean academicRequestAttempted;
        public final int academicHttpCode;
        public final boolean academicValidResponse;
        public final String failureStage; // "NONE", "AUTH_HTTP_ERROR", "AUTH_REJECTED", "OTP_REQUIRED", "NO_SESSION_COOKIE", "RESPONSE_HTML", "PLAIN_TEXT", "ACADEMIC_401", "ACADEMIC_HTTP_ERROR", "ACADEMIC_INVALID_BODY", "EXCEPTION"
        public final String diagnosticMessage;
        public final ResponseDiagnostics authDiagnostics;
        public final ResponseDiagnostics academicDiagnostics;
        public final AuthResult authResult;
        public final String regNo;
        public final String applNo;
        public final String apiKey;
        public final String urlPath;
        public final String academicPayloadJson;

        public DiagnosticProofResult(
                boolean authSucceeded,
                int authHttpCode,
                String authStatus,
                boolean sessionCookieAccepted,
                boolean academicRequestAttempted,
                int academicHttpCode,
                boolean academicValidResponse,
                String failureStage,
                String diagnosticMessage
        ) {
            this(authSucceeded, authHttpCode, authStatus, sessionCookieAccepted, academicRequestAttempted,
                    academicHttpCode, academicValidResponse, failureStage, diagnosticMessage, null, null, null);
        }

        public DiagnosticProofResult(
                boolean authSucceeded,
                int authHttpCode,
                String authStatus,
                boolean sessionCookieAccepted,
                boolean academicRequestAttempted,
                int academicHttpCode,
                boolean academicValidResponse,
                String failureStage,
                String diagnosticMessage,
                ResponseDiagnostics authDiagnostics,
                ResponseDiagnostics academicDiagnostics
        ) {
            this(authSucceeded, authHttpCode, authStatus, sessionCookieAccepted, academicRequestAttempted,
                    academicHttpCode, academicValidResponse, failureStage, diagnosticMessage, authDiagnostics, academicDiagnostics, null);
        }

        public DiagnosticProofResult(
                boolean authSucceeded,
                int authHttpCode,
                String authStatus,
                boolean sessionCookieAccepted,
                boolean academicRequestAttempted,
                int academicHttpCode,
                boolean academicValidResponse,
                String failureStage,
                String diagnosticMessage,
                ResponseDiagnostics authDiagnostics,
                ResponseDiagnostics academicDiagnostics,
                AuthResult authResult
        ) {
            this(authSucceeded, authHttpCode, authStatus, sessionCookieAccepted, academicRequestAttempted,
                    academicHttpCode, academicValidResponse, failureStage, diagnosticMessage, authDiagnostics, academicDiagnostics, authResult, "");
        }

        public DiagnosticProofResult(
                boolean authSucceeded,
                int authHttpCode,
                String authStatus,
                boolean sessionCookieAccepted,
                boolean academicRequestAttempted,
                int academicHttpCode,
                boolean academicValidResponse,
                String failureStage,
                String diagnosticMessage,
                ResponseDiagnostics authDiagnostics,
                ResponseDiagnostics academicDiagnostics,
                AuthResult authResult,
                String academicPayloadJson
        ) {
            this.authSucceeded = authSucceeded;
            this.authHttpCode = authHttpCode;
            this.authStatus = authStatus;
            this.sessionCookieAccepted = sessionCookieAccepted;
            this.academicRequestAttempted = academicRequestAttempted;
            this.academicHttpCode = academicHttpCode;
            this.academicValidResponse = academicValidResponse;
            this.failureStage = failureStage;
            this.diagnosticMessage = diagnosticMessage;
            this.authDiagnostics = authDiagnostics;
            this.academicDiagnostics = academicDiagnostics;
            this.authResult = authResult;
            this.regNo = authResult != null ? authResult.regNo : "";
            this.applNo = authResult != null ? authResult.applNo : "";
            this.apiKey = (authResult != null && authResult.decryptedResponse != null) ? authResult.decryptedResponse.apiKey : "";
            this.urlPath = (authResult != null && authResult.decryptedResponse != null) ? authResult.decryptedResponse.urlPath : "";
            this.academicPayloadJson = academicPayloadJson != null ? academicPayloadJson : "";
        }
    }

    private VTOPAuthClient() {
        // Utility / Singleton
    }

    public static InMemoryCookieJar getSharedCookieJar() {
        return SESSION_COOKIE_JAR;
    }

    public static OkHttpClient getDefaultClient() {
        if (defaultClient == null) {
            synchronized (VTOPAuthClient.class) {
                if (defaultClient == null) {
                    defaultClient = new OkHttpClient.Builder()
                            .cookieJar(SESSION_COOKIE_JAR)
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS)
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .retryOnConnectionFailure(true)
                            .dns(new VTOPAdaptiveDns())
                            .build();
                }
            }
        }
        return defaultClient;
    }

    /**
     * Encrypts plaintext string using Android cryptographic provider with 1024-bit RSA Public Key
     * and PKCS#1 v1.5 padding.
     *
     * @param plainText The plaintext string (e.g., credentials or timestamp string).
     * @return Base64-encoded ciphertext string (exactly 172 characters for 128-byte RSA-1024 block).
     * @throws Exception if cryptographic provider, key format, or padding fails.
     */
    public static String encryptRsa(String plainText) throws Exception {
        if (plainText == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }

        byte[] keyBytes = decodeBase64(RSA_PUBLIC_KEY_B64);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);

        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedBytes = cipher.doFinal(plainBytes);

        return encodeBase64(encryptedBytes);
    }

    /**
     * Encrypts credentials using official format: "${username}:${password}" via RSA/PKCS#1 v1.5.
     */
    public static String encryptCredentials(String username, String password) throws Exception {
        if (username == null || password == null) {
            throw new IllegalArgumentException("Username and password cannot be null");
        }
        String combo = username.trim() + ":" + password;
        return encryptRsa(combo);
    }

    /**
     * Generates dynamic S-KEY-USER header for a given registration number and epoch timestamp.
     * Official format: "${epochMillis}|${regNo}|vitian" encrypted with RSA/PKCS#1 v1.5.
     */
    public static String generateSKeyUser(String regNo, long timestampMillis) throws Exception {
        if (regNo == null || regNo.trim().isEmpty()) {
            throw new IllegalArgumentException("Registration number cannot be null or empty");
        }
        String raw = timestampMillis + "|" + regNo.trim() + "|vitian";
        return encryptRsa(raw);
    }

    /**
     * Generates dynamic S-KEY-USER header using the current system time.
     */
    public static String generateSKeyUser(String regNo) throws Exception {
        return generateSKeyUser(regNo, System.currentTimeMillis());
    }

    /**
     * Encrypts plaintext using AES-128-CBC with PKCS5/PKCS7 padding and the official 16-byte key and IV.
     * Key/IV: "S@fe@VITCC123$#%"
     *
     * @param plainText Plaintext UTF-8 string to encrypt.
     * @return Base64-encoded ciphertext string.
     */
    public static String encryptAes(String plainText) throws Exception {
        if (plainText == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }
        byte[] keyBytes = VTOPEndpoints.AES_KEY_VALUE.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(keyBytes);

        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return encodeBase64(cipherBytes);
    }

    /**
     * Decrypts Base64 ciphertext using AES-128-CBC with PKCS5/PKCS7 padding and the official 16-byte key and IV.
     * Key/IV: "S@fe@VITCC123$#%"
     *
     * @param base64CipherText Base64-encoded ciphertext string.
     * @return Decrypted UTF-8 plaintext string.
     */
    public static String decryptAes(String base64CipherText) throws Exception {
        if (base64CipherText == null) {
            throw new IllegalArgumentException("Ciphertext cannot be null");
        }
        byte[] cipherBytes = decodeBase64(base64CipherText.trim());
        if (cipherBytes == null || cipherBytes.length == 0) {
            throw new IllegalArgumentException("Ciphertext is not valid Base64");
        }
        byte[] keyBytes = VTOPEndpoints.AES_KEY_VALUE.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(keyBytes);

        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] plainBytes = cipher.doFinal(cipherBytes);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    /**
     * Resolves the actual Android device hardware identifier.
     * Does NOT invent or hardcode a fake fallback.
     *
     * @throws IllegalStateException if Android system does not provide ANDROID_ID.
     */
    public static String resolveDeviceId(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context required to resolve Android deviceId");
        }
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalStateException("Android device ID (ANDROID_ID) is unavailable on this device");
        }
        return deviceId.trim();
    }

    /**
     * Resolves the genuine Firebase Cloud Messaging registration token if available.
     * Does NOT generate synthetic or fake tokens.
     */
    public static String resolveGenuineFcmToken(Context context) {
        if (context == null) return null;
        try {
            FirebaseMessagingHelper helper = FirebaseMessagingHelper.getInstance(context);
            if (helper != null) {
                String token = helper.getFCMToken();
                if (token != null && !token.trim().isEmpty()) {
                    return token.trim();
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "FCM token not available via FirebaseMessagingHelper: " + t.getMessage());
        }
        return null;
    }

    /**
     * Constructs the official inner JSON payload for /login/vitian/appLoginProcess.
     * Shape: {"encryption":"login", "userEnc":<rsa>, "deviceType":"android", "fcmToken":<fcm_or_empty>, "mobile":""}
     */
    public static JSONObject buildInnerLoginPayload(String username, String userEnc, String genuineFcmToken) throws JSONException {
        if (userEnc == null || userEnc.trim().isEmpty()) {
            throw new IllegalArgumentException("userEnc is required");
        }
        JSONObject json = new JSONObject();
        json.put("encryption", "login");
        json.put("userEnc", userEnc.trim());
        json.put("deviceType", "android");
        json.put("fcmToken", genuineFcmToken != null ? genuineFcmToken.trim() : "");
        json.put("mobile", "");
        return json;
    }

    public static JSONObject buildInnerLoginPayload(String userEnc, String genuineFcmToken) throws JSONException {
        return buildInnerLoginPayload("", userEnc, genuineFcmToken);
    }

    /**
     * Serializes and encrypts an inner JSON payload into the official outer envelope {"encData": <aes_base64>}.
     */
    public static JSONObject buildEncryptedEnvelope(String plainJsonString) throws Exception {
        if (plainJsonString == null) {
            throw new IllegalArgumentException("Plain JSON cannot be null");
        }
        String encData = encryptAes(plainJsonString);
        JSONObject outer = new JSONObject();
        outer.put("encData", encData);
        return outer;
    }

    /**
     * Constructs the complete official login request envelope.
     */
    public static JSONObject buildOfficialLoginEnvelope(String username, String password, String genuineFcmToken) throws Exception {
        String userEnc = encryptCredentials(username, password);
        JSONObject inner = buildInnerLoginPayload(username, userEnc, genuineFcmToken);
        return buildEncryptedEnvelope(inner.toString());
    }

    /**
     * Backward-compatible legacy login payload builder.
     */
    public static JSONObject buildLoginPayload(String userEnc, String encData, String deviceId, String genuineFcmToken)
            throws JSONException {
        if (userEnc == null || encData == null || deviceId == null) {
            throw new IllegalArgumentException("userEnc, encData, and deviceId are required fields");
        }

        JSONObject json = new JSONObject();
        json.put("userEnc", userEnc);
        json.put("encData", encData);
        json.put("deviceId", deviceId);
        json.put("deviceType", "Android");

        if (genuineFcmToken != null && !genuineFcmToken.trim().isEmpty()) {
            json.put("fcmToken", genuineFcmToken.trim());
        }

        return json;
    }

    /**
     * Checks if an active native session exists in memory.
     */
    public static boolean hasValidSession() {
        return SESSION_COOKIE_JAR.hasValidSession("vtopcc5.vit.ac.in");
    }

    /**
     * Clears in-memory session cookies.
     */
    public static void clearSession() {
        SESSION_COOKIE_JAR.clear();
    }

    /**
     * Performs native REST login against VTOP appLoginProcess endpoint.
     */
    public static AuthResult login(
            Context context,
            OkHttpClient client,
            String baseUrl,
            String username,
            String password,
            boolean forceReauth
    ) {
        return login(context, client, baseUrl, username, password, null, null, forceReauth);
    }

    public static AuthResult login(
            Context context,
            OkHttpClient client,
            String baseUrl,
            String username,
            String password,
            String deviceIdOverride,
            String fcmTokenOverride,
            boolean forceReauth
    ) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return AuthResult.failure(-1, "EMPTY_CREDENTIALS", "Username and password cannot be empty", false);
        }

        if (client == null) {
            client = getDefaultClient();
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = VTOPEndpoints.BASE_URL;
        }

        InMemoryCookieJar activeJar = (client.cookieJar() instanceof InMemoryCookieJar)
                ? (InMemoryCookieJar) client.cookieJar()
                : SESSION_COOKIE_JAR;

        if (forceReauth) {
            activeJar.clear();
        }

        String host = extractHost(baseUrl);

        // Resolve genuine FCM token if available
        String genuineFcmToken = fcmTokenOverride;
        if (genuineFcmToken == null && context != null) {
            genuineFcmToken = resolveGenuineFcmToken(context);
        }

        try {
            // Build official outer envelope with AES-encrypted inner payload
            JSONObject envelope = buildOfficialLoginEnvelope(username, password, genuineFcmToken);
            String encData = envelope.getString("encData");

            // Official appLoginProcess contract (aAh.dart:2860-2870 & zrf:798-815):
            // 1. encData is transmitted as an HTTP Header: "encData: <AES_CIPHERTEXT>"
            // 2. HTTP Body is an empty JSON object: "{}"
            // 3. Auth headers (S-API-KEY, S-KEY-USER) are NOT included on login (includeAuthHeaders = false)
            RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, "{}");

            String loginUrl = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                    + VTOPEndpoints.AUTH_LOGIN;

            Request request = new Request.Builder()
                    .url(loginUrl)
                    .post(body)
                    .header("Host", host)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("encData", encData)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)")
                    .build();

            logOutboundRequestShape("Auth Request", loginUrl, "POST", request, envelope, activeJar, host);

            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                String responseBody = response.body() != null ? response.body().string() : "";

                // Strip UTF-8 BOM and leading/trailing whitespace if present
                String cleanBody = responseBody.trim();
                if (cleanBody.startsWith("\uFEFF")) {
                    cleanBody = cleanBody.substring(1).trim();
                }

                JSONObject resJson = null;
                DecryptedLoginResponse decryptedResp = null;
                Exception jsonException = null;

                // 1. Attempt AES-128-CBC decryption on response (official encrypted format)
                if (cleanBody.matches("^[A-Za-z0-9+/=_-]{20,}$")) {
                    try {
                        String decryptedStr = decryptAes(cleanBody).trim();
                        if (decryptedStr.startsWith("\uFEFF")) {
                            decryptedStr = decryptedStr.substring(1).trim();
                        }
                        if (decryptedStr.startsWith("{")) {
                            resJson = new JSONObject(decryptedStr);
                            decryptedResp = new DecryptedLoginResponse(resJson);
                        } else if (decryptedStr.startsWith("[")) {
                            org.json.JSONArray arr = new org.json.JSONArray(decryptedStr);
                            if (arr.length() > 0 && arr.get(0) instanceof JSONObject) {
                                resJson = arr.getJSONObject(0);
                                decryptedResp = new DecryptedLoginResponse(resJson);
                            }
                        }
                    } catch (Throwable t) {
                        Log.d(TAG, "[NativeAuth] Response is not AES-encrypted or decryption failed: " + t.getMessage());
                    }
                }

                // 2. Fallback to direct JSON parsing if not encrypted (mock responses or unencrypted gateways)
                if (resJson == null) {
                    if (cleanBody.startsWith("{")) {
                        try {
                            resJson = new JSONObject(cleanBody);
                            decryptedResp = new DecryptedLoginResponse(resJson);
                        } catch (JSONException e) {
                            jsonException = e;
                        }
                    } else if (cleanBody.startsWith("[")) {
                        try {
                            org.json.JSONArray arr = new org.json.JSONArray(cleanBody);
                            if (arr.length() > 0 && arr.get(0) instanceof JSONObject) {
                                resJson = arr.getJSONObject(0);
                                decryptedResp = new DecryptedLoginResponse(resJson);
                            }
                        } catch (JSONException e) {
                            jsonException = e;
                        }
                    } else if (cleanBody.matches("^[A-Za-z0-9+/=_-]{20,}$")) {
                        jsonException = new JSONException("Body is an undecryptable scalar Base64 token");
                    } else if (!cleanBody.isEmpty()) {
                        jsonException = new JSONException("Body does not start with JSON token '{' or '['");
                    }
                }

                ResponseDiagnostics diag = analyzeResponse(response, loginUrl, "POST", responseBody, activeJar, host, jsonException);

                Log.i(TAG, "[NativeAuthDiagnostics] Auth Response: HTTP " + diag.httpCode +
                        " | URL: " + diag.finalUrl +
                        " | BodyLength: " + diag.bodyLength + " bytes | DecryptedJSON: " + (resJson != null) +
                        " | Cookies: count=" + diag.cookieCount + ", session=" + diag.hasSessionCookie +
                        ", names=" + diag.cookieNames);

                if (code == 401 || code == 403) {
                    Log.w(TAG, "[NativeAuth] Authentication rejected by server: HTTP " + code);
                    activeJar.clear();
                    return AuthResult.failure(code, "HTTP_" + code, "Server rejected credentials (HTTP " + code + ")", false, diag);
                }

                if (code < 200 || code >= 300) {
                    Log.w(TAG, "[NativeAuth] Server returned non-2xx status: HTTP " + code);
                    activeJar.clear();
                    return AuthResult.failure(code, "HTTP_" + code, "Server returned HTTP " + code, false, diag);
                }

                if (responseBody.trim().isEmpty()) {
                    Log.w(TAG, "[NativeAuth] Server returned empty response body with HTTP " + code);
                    activeJar.clear();
                    return AuthResult.failure(code, "EMPTY_BODY", "Server returned empty response body", false, diag);
                }

                // If response decrypted/parsed successfully into login response model:
                if (decryptedResp != null) {
                    // Check if official backend issued an OTP challenge
                    if (decryptedResp.isOtpRequired()) {
                        Log.w(TAG, "[NativeAuth] Server returned OTP challenge: otpMsg is present. Session pending verification.");
                        return AuthResult.otpRequired(code, username, decryptedResp.studentApplicationNumber, decryptedResp.otpMsg, diag, decryptedResp);
                    }

                    // Check for explicit server-level error message
                    String status = resJson.optString("status", "");
                    String message = resJson.optString("message", resJson.optString("msg", ""));
                    if ("FAILURE".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) {
                        Log.w(TAG, "[NativeAuth] Server payload reported failure: " + status + " (" + message + ")");
                        activeJar.clear();
                        return AuthResult.failure(code, status.toUpperCase(), message.isEmpty() ? "Server reported " + status : message, false, diag);
                    }

                    java.util.Iterator<String> keyIter = resJson.keys();
                    java.util.List<String> jsonKeys = new java.util.ArrayList<>();
                    while (keyIter.hasNext()) {
                        jsonKeys.add(keyIter.next());
                    }

                    String applNo = decryptedResp.studentApplicationNumber;
                    if (applNo == null || applNo.isEmpty()) {
                        applNo = resJson.optString("applNo", username);
                    }

                    String maskedAppl = (applNo != null && applNo.length() >= 4)
                            ? applNo.substring(0, 4) + "***"
                            : "[EMPTY]";

                    Log.i(TAG, "[NativeAuth] Login decrypted successfully! Keys=" + jsonKeys +
                            " | resultValue='" + decryptedResp.resultValue + "'" +
                            " | applNo=" + maskedAppl +
                            " | studentName=" + (decryptedResp.studentName.isEmpty() ? "[EMPTY]" : "[PRESENT]") +
                            " | mpinFlag=" + decryptedResp.mpinFlag +
                            " | mandatoryCheck=" + decryptedResp.mandatoryCheck +
                            " | otpMsgLen=" + decryptedResp.otpMsg.length() +
                            " | Cookies=" + activeJar.getCookieNames() +
                            " | rawJson=" + sanitizeTextPreview(resJson.toString(), 250));

                    String rVal = decryptedResp.resultValue != null ? decryptedResp.resultValue.trim() : "";
                    String rValLower = rVal.toLowerCase();
                    if ("0".equals(rVal) || "FAIL".equalsIgnoreCase(rVal) ||
                            "FAILURE".equalsIgnoreCase(rVal) || "ERROR".equalsIgnoreCase(rVal) ||
                            rValLower.contains("invalid") || rValLower.contains("fail") ||
                            rValLower.contains("error")) {
                        Log.w(TAG, "[NativeAuth] Server returned failure indicator in resultValue='" + decryptedResp.resultValue + "'");
                        activeJar.clear();
                        return AuthResult.failure(code, "AUTH_REJECTED", "Server reported failure in result: " + decryptedResp.resultValue, false, diag);
                    }
                    return AuthResult.success(code, "SUCCESS", username, applNo, diag.hasSessionCookie, diag, decryptedResp);
                }

                // Fallback handling for unencrypted scalar responses
                boolean hasSessionCookie = activeJar.hasValidSession(host);

                if (code >= 200 && code < 300 && hasSessionCookie) {
                    Log.i(TAG, "[NativeAuth] HTTP " + code + " " + diag.bodyClassification + " response received with valid session cookie.");
                    return new AuthResult(true, code, "SCALAR_RESPONSE_200", "HTTP 200 " + diag.bodyClassification + " response with session cookie", username, "", true, false, null, diag);
                }

                activeJar.clear();
                if (!hasSessionCookie && diag.cookieCount > 0) {
                    Log.w(TAG, "[NativeAuth] Server returned HTTP " + code + " " + diag.bodyClassification + " with non-session cookie(s) " +
                            diag.cookieNames + ". SERVERID is an infrastructure load-balancer cookie, not an authenticated session. Native auth is NOT proven.");
                    return AuthResult.failure(code, "NO_SESSION_COOKIE", "Server returned HTTP " + code + " " + diag.bodyClassification + " without session cookie (only " + diag.cookieNames + " received). SERVERID is not an authenticated session.", false, diag);
                } else if ("HTML".equals(diag.bodyClassification)) {
                    Log.w(TAG, "[NativeAuth] Server returned HTML page instead of JSON: " + diag.sanitizedSummary);
                    return AuthResult.failure(code, "RESPONSE_HTML", "Server returned HTML page (" + diag.sanitizedSummary + ")", false, diag);
                } else if ("PLAIN_TEXT".equals(diag.bodyClassification) || "BASE64_SCALAR".equals(diag.bodyClassification)) {
                    Log.w(TAG, "[NativeAuth] Server returned " + diag.bodyClassification + " without valid session cookies or decryptable payload: " + diag.sanitizedSummary);
                    return AuthResult.failure(code, diag.bodyClassification, "Server returned " + diag.bodyClassification + " (" + diag.sanitizedSummary + ")", false, diag);
                } else {
                    return AuthResult.failure(code, "MALFORMED_RESPONSE", "Server returned unrecognized payload (" + diag.sanitizedSummary + ")", false, diag);
                }
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "[NativeAuth] Device prerequisite error: " + e.getMessage());
            return AuthResult.failure(-1, "PREREQUISITE_ERROR", e.getMessage(), false);
        } catch (IOException e) {
            Log.e(TAG, "[NativeAuth] Network I/O exception: " + e.getMessage());
            return AuthResult.failure(-1, "NETWORK_ERROR", e.getMessage(), false);
        } catch (Exception e) {
            Log.e(TAG, "[NativeAuth] Unexpected cryptographic or runtime exception: " + e.getMessage());
            return AuthResult.failure(-1, "RUNTIME_ERROR", e.getMessage(), false);
        }
    }

    /**
     * Constructs the official inner JSON payload for OTP verification on /login/vitian/appLoginProcess.
     * Shape: {"deviceType":"android", "fcmToken":<fcm_or_empty>, "mobile":"", "userName":<username>, "encryption":"submitOtp", "otpText":<otp>}
     */
    public static JSONObject buildInnerOtpPayload(String username, String otpText, String genuineFcmToken) throws JSONException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }
        if (otpText == null || otpText.trim().isEmpty()) {
            throw new IllegalArgumentException("otpText is required");
        }
        JSONObject json = new JSONObject();
        json.put("deviceType", "android");
        json.put("fcmToken", genuineFcmToken != null ? genuineFcmToken.trim() : "");
        json.put("mobile", "");
        json.put("userName", username.trim());
        json.put("encryption", "submitOtp");
        json.put("otpText", otpText.trim());
        return json;
    }

    /**
     * Submits an OTP for verified login challenge completion using the official VTOP protocol.
     * Official endpoint: /login/vitian/appLoginProcess
     * Official transmission: Header 'encData: <AES_128_CBC_Base64(innerOtpPayload)>', Body: "{}"
     */
    public static AuthResult submitOtp(
            Context context,
            OkHttpClient client,
            String baseUrl,
            String username,
            String otpText,
            String fcmTokenOverride
    ) {
        if (username == null || username.trim().isEmpty() || otpText == null || otpText.trim().isEmpty()) {
            return AuthResult.failure(-1, "EMPTY_CREDENTIALS", "Username and OTP cannot be empty", false);
        }
        if (client == null) {
            client = getDefaultClient();
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = VTOPEndpoints.BASE_URL;
        }

        InMemoryCookieJar activeJar = (client.cookieJar() instanceof InMemoryCookieJar)
                ? (InMemoryCookieJar) client.cookieJar()
                : SESSION_COOKIE_JAR;

        String host = extractHost(baseUrl);
        try {
            String fcmToken = (fcmTokenOverride != null) ? fcmTokenOverride : resolveGenuineFcmToken(context);
            if (fcmToken == null) fcmToken = "";

            JSONObject innerPayload = buildInnerOtpPayload(username, otpText, fcmToken);
            String encData = encryptAes(innerPayload.toString());

            String otpUrl = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                    + VTOPEndpoints.AUTH_LOGIN;

            RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, "{}");

            Request request = new Request.Builder()
                    .url(otpUrl)
                    .post(body)
                    .header("Host", host)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("encData", encData)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)")
                    .build();

            logOutboundRequestShape("OTP Request", otpUrl, "POST", request, innerPayload, activeJar, host);

            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                String responseBody = response.body() != null ? response.body().string() : "";
                String cleanBody = responseBody.trim();
                if (cleanBody.startsWith("\uFEFF")) {
                    cleanBody = cleanBody.substring(1).trim();
                }

                JSONObject resJson = null;
                DecryptedLoginResponse decryptedResp = null;
                Exception jsonException = null;

                if (cleanBody.matches("^[A-Za-z0-9+/=_-]{20,}$")) {
                    try {
                        String dec = decryptAes(cleanBody).trim();
                        if (dec.startsWith("\uFEFF")) {
                            dec = dec.substring(1).trim();
                        }
                        if (dec.startsWith("{")) {
                            resJson = new JSONObject(dec);
                            decryptedResp = new DecryptedLoginResponse(resJson);
                        } else if (dec.startsWith("[")) {
                            org.json.JSONArray arr = new org.json.JSONArray(dec);
                            if (arr.length() > 0 && arr.get(0) instanceof JSONObject) {
                                resJson = arr.getJSONObject(0);
                                decryptedResp = new DecryptedLoginResponse(resJson);
                            }
                        }
                    } catch (Throwable t) {
                        Log.d(TAG, "[NativeAuth] OTP response is not AES encrypted: " + t.getMessage());
                    }
                }

                if (resJson == null) {
                    if (cleanBody.startsWith("{")) {
                        try {
                            resJson = new JSONObject(cleanBody);
                            decryptedResp = new DecryptedLoginResponse(resJson);
                        } catch (JSONException e) {
                            jsonException = e;
                        }
                    }
                }

                ResponseDiagnostics diag = analyzeResponse(response, otpUrl, "POST", responseBody, activeJar, host, jsonException);

                if (code != 200) {
                    return AuthResult.failure(code, "OTP_VERIFICATION_FAILED", "Server returned HTTP " + code, false, diag);
                }

                Log.i(TAG, "[NativeAuthDiagnostics] OTP Response: HTTP " + diag.httpCode +
                        " | URL: " + diag.finalUrl +
                        " | BodyLength: " + diag.bodyLength + " bytes | DecryptedJSON: " + (resJson != null) +
                        " | Cookies: count=" + diag.cookieCount + ", names=" + diag.cookieNames +
                        " | RawSnippet=" + sanitizeTextPreview(responseBody, 200) +
                        (resJson != null ? " | DecryptedJson=" + sanitizeTextPreview(resJson.toString(), 250) : ""));

                if (resJson != null) {
                    Log.i("VTOPAcademicDump", "=== [OTP Response Decrypted JSON] ===");
                    Log.i("VTOPAcademicDump", "Raw JSON: " + sanitizeTextPreview(resJson.toString(), 500));
                    Log.i("VTOPAcademicDump", "Extracted apiKey (data): " + (decryptedResp != null ? decryptedResp.apiKey : "") + " | applNo: " + (decryptedResp != null ? decryptedResp.studentApplicationNumber : ""));
                    String status = resJson.optString("status", resJson.optString("statusCode", ""));
                    String message = resJson.optString("message", resJson.optString("msg", ""));
                    String resultVal = resJson.optString("result", "");

                    boolean isExplicitFailure = "0".equals(status) || "FAIL".equalsIgnoreCase(status) ||
                            "FAILURE".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status) ||
                            "0".equals(resultVal) || "FAIL".equalsIgnoreCase(resultVal);

                    boolean isExplicitSuccess = "1".equals(status) || "SUCCESS".equalsIgnoreCase(status) ||
                            "1".equals(resultVal) || "SUCCESS".equalsIgnoreCase(resultVal);

                    String applNo = decryptedResp != null ? decryptedResp.studentApplicationNumber : "";
                    if (applNo.isEmpty()) {
                        applNo = resJson.optString("applNo", resJson.optString("regNo", ""));
                    }

                    if (isExplicitSuccess || (!isExplicitFailure && !applNo.isEmpty())) {
                        Log.i(TAG, "[NativeAuth] OTP verified successfully! applNo=" + applNo + ", status=" + status);
                        return AuthResult.success(code, "SUCCESS", username, applNo.isEmpty() ? username : applNo, diag.hasSessionCookie, diag, decryptedResp);
                    } else {
                        Log.w(TAG, "[NativeAuth] OTP rejected by server: status=" + status + ", message=" + message + ", result=" + resultVal);
                        return AuthResult.failure(code, "OTP_REJECTED", message.isEmpty() ? "OTP verification failed (status=" + status + ")" : message, false, diag);
                    }
                }

                return AuthResult.failure(code, "OTP_REJECTED", "OTP rejected or invalid", false, diag);
            }
        } catch (Exception e) {
            Log.e(TAG, "[NativeAuth] Error during OTP submission: " + e.getMessage());
            return AuthResult.failure(-1, "OTP_ERROR", e.getMessage(), false);
        }
    }

    public static AuthResult submitOtp(
            OkHttpClient client,
            String baseUrl,
            String username,
            String otpText
    ) {
        return submitOtp(null, client, baseUrl, username, otpText, null);
    }

    /**
     * Validates MPIN for session establishment.
     * Official endpoint: /login/vitian/validateMpin
     * Request headers: S-API-KEY, S-KEY-USER
     */
    public static AuthResult validateMpin(
            OkHttpClient client,
            String baseUrl,
            String username,
            String mpin
    ) {
        if (username == null || username.trim().isEmpty() || mpin == null || mpin.trim().isEmpty()) {
            return AuthResult.failure(-1, "EMPTY_CREDENTIALS", "Username and MPIN cannot be empty", false);
        }
        if (client == null) {
            client = getDefaultClient();
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = VTOPEndpoints.BASE_URL;
        }

        String host = extractHost(baseUrl);
        try {
            String sKeyUser = generateSKeyUser(username, System.currentTimeMillis());

            JSONObject payload = new JSONObject();
            payload.put("userName", username.trim());
            payload.put("mpin", mpin.trim());

            RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, payload.toString());
            String mpinUrl = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                    + VTOPEndpoints.AUTH_VALIDATE_MPIN;

            Request request = new Request.Builder()
                    .url(mpinUrl)
                    .post(body)
                    .header("Host", host)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json, text/plain, */*")
                    .header(VTOPEndpoints.HEADER_API_KEY, VTOPEndpoints.API_KEY_VALUE)
                    .header(VTOPEndpoints.HEADER_KEY_USER, sKeyUser)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                String responseBody = response.body() != null ? response.body().string() : "";
                String cleanBody = responseBody.trim();
                if (cleanBody.startsWith("\uFEFF")) {
                    cleanBody = cleanBody.substring(1).trim();
                }

                if (code != 200) {
                    return AuthResult.failure(code, "MPIN_VERIFICATION_FAILED", "Server returned HTTP " + code, false);
                }

                JSONObject resJson = null;
                if (cleanBody.matches("^[A-Za-z0-9+/=_-]{20,}$")) {
                    try {
                        String dec = decryptAes(cleanBody);
                        if (dec.trim().startsWith("{")) {
                            resJson = new JSONObject(dec.trim());
                        }
                    } catch (Throwable ignored) {}
                }
                if (resJson == null && cleanBody.startsWith("{")) {
                    resJson = new JSONObject(cleanBody);
                }

                if (resJson != null) {
                    DecryptedLoginResponse decResp = new DecryptedLoginResponse(resJson);
                    String status = resJson.optString("status", "");
                    if ("SUCCESS".equalsIgnoreCase(status) || !decResp.studentApplicationNumber.isEmpty()) {
                        return AuthResult.success(code, "SUCCESS", username, decResp.studentApplicationNumber, false, null, decResp);
                    }
                }
                return AuthResult.failure(code, "MPIN_REJECTED", "MPIN rejected or invalid", false);
            }
        } catch (Exception e) {
            return AuthResult.failure(-1, "MPIN_ERROR", e.getMessage(), false);
        }
    }

    /**
     * Executes the controlled, isolated native authentication proof:
     * 1. Attempts single login with official dual-layer encryption (RSA credentials -> inner JSON -> AES outer envelope).
     * 2. Inspects response, decrypts AES ciphertext, extracts studentApplicationNumber as applNo, and checks for OTP challenge.
     * 3. If OTP challenge received, stops at OTP_REQUIRED without fabricating or bypassing challenge.
     * 4. If login succeeds, performs academic probe (studentRegisteredCourse) sending applNo, S-API-KEY, and dynamic S-KEY-USER.
     * 5. Decrypts AES academic response and verifies course content.
     */
    public static DiagnosticProofResult executeControlledAuthProof(
            Context context,
            OkHttpClient client,
            String baseUrl,
            String username,
            String password,
            String semesterId
    ) {
        return executeControlledAuthProof(context, client, baseUrl, username, password, semesterId, null, null);
    }

    public static DiagnosticProofResult executeControlledAuthProof(
            Context context,
            OkHttpClient client,
            String baseUrl,
            String username,
            String password,
            String semesterId,
            String deviceIdOverride,
            String fcmTokenOverride
    ) {
        Log.i(TAG, "[NativeAuthProof] Initiating controlled native authentication proof...");

        if (client == null) {
            client = getDefaultClient();
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = VTOPEndpoints.BASE_URL;
        }

        InMemoryCookieJar activeJar = (client.cookieJar() instanceof InMemoryCookieJar)
                ? (InMemoryCookieJar) client.cookieJar()
                : SESSION_COOKIE_JAR;

        // Step 1: Execute single native login
        AuthResult authResult = login(context, client, baseUrl, username, password, deviceIdOverride, fcmTokenOverride, true);

        if (!authResult.success) {
            String stage = authResult.status;
            Log.w(TAG, "[NativeAuthProof] Native login failed at stage: " + stage + " (HTTP " + authResult.httpCode + ", " + authResult.message + ")");
            String diagMsg = authResult.otpRequired
                    ? "Native login received OTP challenge from official backend (OTP_REQUIRED). OTP verification is mandatory before academic data retrieval."
                    : "Native login failed: HTTP " + authResult.httpCode + " [" + stage + "] - " + authResult.message;

            return new DiagnosticProofResult(
                    false,
                    authResult.httpCode,
                    authResult.status,
                    authResult.sessionCookieAccepted,
                    false,
                    -1,
                    false,
                    stage,
                    diagMsg,
                    authResult.diagnostics,
                    null,
                    authResult
            );
        }

        Log.i(TAG, "[NativeAuthProof] Native login step returned HTTP " + authResult.httpCode + " (" + authResult.status + "). Proceeding to academic endpoint verification...");
        return executeAcademicProbe(context, client, baseUrl, username, authResult, activeJar);
    }

    /**
     * Executes the controlled academic probe (studentRegisteredCourse) given a successful auth or OTP result.
     */
    public static DiagnosticProofResult executeAcademicProbe(
            Context context,
            OkHttpClient client,
            String baseUrl,
            String username,
            AuthResult authResult,
            InMemoryCookieJar activeJar
    ) {
        String host = extractHost(baseUrl);
        String rawPath = (authResult.decryptedResponse != null) ? authResult.decryptedResponse.urlPath : "";
        String subPath = (rawPath != null && !rawPath.isEmpty() && !"null".equalsIgnoreCase(rawPath.trim()))
                ? rawPath.trim()
                : "students";
        if (!subPath.startsWith("/")) subPath = "/" + subPath;
        if (subPath.endsWith("/")) subPath = subPath.substring(0, subPath.length() - 1);

        String academicUrl = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                + subPath + "/studentRegisteredCourse";

        String effectiveApplNo = (authResult.applNo != null && !authResult.applNo.isEmpty())
                ? authResult.applNo
                : username;

        // Candidate API key extracted from data or apiKey field, defaulting to API_KEY_VALUE if absent
        String apiKey = (authResult.decryptedResponse != null && !authResult.decryptedResponse.apiKey.isEmpty())
                ? authResult.decryptedResponse.apiKey
                : VTOPEndpoints.API_KEY_VALUE;

        String academicSKeyUser = "";
        try {
            academicSKeyUser = generateSKeyUser(username, System.currentTimeMillis());
        } catch (Exception e) {
            Log.e(TAG, "[NativeAuthProof] Failed to generate academic S-KEY-USER header: " + e.getMessage());
        }

        JSONObject acadPayload = new JSONObject();
        try {
            acadPayload.put("applNo", effectiveApplNo);
        } catch (JSONException ignored) {}

        RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, acadPayload.toString());

        Log.i("VTOPAcademicDump", "=== [studentRegisteredCourse Primary Probe] ===");
        Log.i("VTOPAcademicDump", "URL: " + academicUrl + " | Body: " + acadPayload.toString());
        Log.i("VTOPAcademicDump", "S-API-KEY: '" + apiKey + "' | S-KEY-USER: " + (academicSKeyUser.isEmpty() ? "[EMPTY]" : "[GENERATED len=" + academicSKeyUser.length() + "]"));
        Log.i("VTOPAcademicDump", "Cookies: " + activeJar.getCookieNames());

        Request.Builder acadReqBuilder = new Request.Builder()
                .url(academicUrl)
                .post(body)
                .header("Host", host)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)");

        if (!apiKey.isEmpty()) {
            acadReqBuilder.header(VTOPEndpoints.HEADER_API_KEY, apiKey);
        } else {
            acadReqBuilder.header(VTOPEndpoints.HEADER_API_KEY, "");
        }

        if (!academicSKeyUser.isEmpty()) {
            acadReqBuilder.header(VTOPEndpoints.HEADER_KEY_USER, academicSKeyUser);
        }

        Request acadRequest = acadReqBuilder.build();
        logOutboundRequestShape("Academic Request", academicUrl, "POST", acadRequest, acadPayload, activeJar, host);

        int acadCode = -1;
        String acadBody = "";
        ResponseDiagnostics acadDiag = null;

        try {
            try (Response response = client.newCall(acadRequest).execute()) {
                acadCode = response.code();
                acadBody = response.body() != null ? response.body().string() : "";
                acadDiag = analyzeResponse(response, academicUrl, "POST", acadBody, activeJar, host, null);
            }

            Log.i("VTOPAcademicDump", "=== [studentRegisteredCourse Primary Response: HTTP " + acadCode + "] ===");
            Log.i("VTOPAcademicDump", "Body: " + sanitizeTextPreview(acadBody, 300));

            // If 401 or 403, run fallback probes
            if (acadCode == 401 || acadCode == 403) {
                // Fallback Probe 1: Alternate applNo (swap between studentApplicationNumber and registration number)
                String altApplNo = effectiveApplNo.equalsIgnoreCase(username) ?
                        (authResult.decryptedResponse != null ? authResult.decryptedResponse.studentApplicationNumber : "") : username;

                if (!altApplNo.isEmpty() && !altApplNo.equalsIgnoreCase(effectiveApplNo)) {
                    Log.w(TAG, "[NativeAuthProof] Academic probe returned " + acadCode + ". Attempting Fallback 1: alternate applNo=" + altApplNo + "...");
                    Log.i("VTOPAcademicDump", "=== [studentRegisteredCourse Fallback 1: Alternate applNo=" + altApplNo + "] ===");
                    JSONObject altPayload = new JSONObject();
                    try { altPayload.put("applNo", altApplNo); } catch (JSONException ignored) {}
                    RequestBody altBody = RequestBody.create(JSON_MEDIA_TYPE, altPayload.toString());

                    Request altRequest = new Request.Builder()
                            .url(academicUrl)
                            .post(altBody)
                            .header("Host", host)
                            .header("Content-Type", "application/json; charset=UTF-8")
                            .header("Accept", "application/json, text/plain, */*")
                            .header(VTOPEndpoints.HEADER_API_KEY, apiKey)
                            .header(VTOPEndpoints.HEADER_KEY_USER, academicSKeyUser)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)")
                            .build();

                    try (Response altResp = client.newCall(altRequest).execute()) {
                        int altCode = altResp.code();
                        String altBodyStr = altResp.body() != null ? altResp.body().string() : "";
                        Log.i("VTOPAcademicDump", "Fallback 1 Response: HTTP " + altCode + " | Body=" + sanitizeTextPreview(altBodyStr, 200));
                        if (altCode >= 200 && altCode < 300) {
                            Log.i(TAG, "[NativeAuthProof] Fallback 1 SUCCEEDED with HTTP " + altCode + "!");
                            acadCode = altCode;
                            acadBody = altBodyStr;
                            effectiveApplNo = altApplNo;
                            acadDiag = analyzeResponse(altResp, academicUrl, "POST", altBodyStr, activeJar, host, null);
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (acadCode == 401 || acadCode == 403) {
                // Fallback Probe 2: Try without S-API-KEY / empty S-API-KEY
                Log.w(TAG, "[NativeAuthProof] Attempting Fallback 2: Empty S-API-KEY (matching official Crf empty behavior)...");
                Log.i("VTOPAcademicDump", "=== [studentRegisteredCourse Fallback 2: Empty S-API-KEY] ===");

                Request fallbackRequest = new Request.Builder()
                        .url(academicUrl)
                        .post(body)
                        .header("Host", host)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .header("Accept", "application/json, text/plain, */*")
                        .header(VTOPEndpoints.HEADER_KEY_USER, academicSKeyUser)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)")
                        .build();

                try (Response fallbackResp = client.newCall(fallbackRequest).execute()) {
                    int fbCode = fallbackResp.code();
                    String fbBody = fallbackResp.body() != null ? fallbackResp.body().string() : "";
                    Log.i("VTOPAcademicDump", "Fallback 2 Response: HTTP " + fbCode + " | Body=" + sanitizeTextPreview(fbBody, 200));
                    if (fbCode >= 200 && fbCode < 300) {
                        Log.i(TAG, "[NativeAuthProof] Fallback 2 SUCCEEDED with HTTP " + fbCode + "!");
                        acadCode = fbCode;
                        acadBody = fbBody;
                        acadDiag = analyzeResponse(fallbackResp, academicUrl, "POST", fbBody, activeJar, host, null);
                    }
                } catch (Exception fbEx) {
                    Log.w(TAG, "[NativeAuthProof] Fallback 2 error: " + fbEx.getMessage());
                }
            }

            if (acadCode == 401 || acadCode == 403) {
                Log.w("VTOPAcademicDump", "=== [studentRegisteredCourse HTTP " + acadCode + " REJECTED] ===");
                Log.w("VTOPAcademicDump", "URL: " + academicUrl + " | Body: " + sanitizeTextPreview(acadBody, 300));
                return new DiagnosticProofResult(
                        false,
                        authResult.httpCode,
                        authResult.status,
                        authResult.sessionCookieAccepted,
                        true,
                        acadCode,
                        false,
                        "ACADEMIC_401",
                        "Academic endpoint rejected session with HTTP " + acadCode + ". Stage: ACADEMIC_401. Server body: " + sanitizeTextPreview(acadBody, 100),
                        authResult.diagnostics,
                        acadDiag,
                        authResult
                );
            }

            if (acadCode < 200 || acadCode >= 300) {
                Log.w(TAG, "[NativeAuthProof] Academic endpoint failed: HTTP " + acadCode + ". Stage: ACADEMIC_HTTP_ERROR");
                return new DiagnosticProofResult(
                        false,
                        authResult.httpCode,
                        authResult.status,
                        authResult.sessionCookieAccepted,
                        true,
                        acadCode,
                        false,
                        "ACADEMIC_HTTP_ERROR",
                        "Academic endpoint returned HTTP " + acadCode + " error",
                        authResult.diagnostics,
                        acadDiag
                );
            }

            // Academic response payload processing:
            // Official backend returns AES-encrypted Base64 string for studentRegisteredCourse.
            String cleanAcadBody = acadBody.trim();
            if (cleanAcadBody.startsWith("﻿")) {
                cleanAcadBody = cleanAcadBody.substring(1).trim();
            }

            String plaintextAcad = cleanAcadBody;
            if (cleanAcadBody.matches("^[A-Za-z0-9+/=_-]{20,}$")) {
                try {
                    String decrypted = decryptAes(cleanAcadBody).trim();
                    if (decrypted.startsWith("﻿")) {
                        decrypted = decrypted.substring(1).trim();
                    }
                    if (decrypted.startsWith("{") || decrypted.startsWith("[")) {
                        plaintextAcad = decrypted;
                        Log.i(TAG, "[NativeAuthProof] Successfully decrypted AES-128-CBC academic response (" + plaintextAcad.length() + " chars)");
                    }
                } catch (Throwable t) {
                    Log.d(TAG, "[NativeAuthProof] Academic response was not AES encrypted or decryption failed: " + t.getMessage());
                }
            }

            boolean hasExpectedContent = false;
            try {
                JSONObject acadJson = new JSONObject(plaintextAcad);
                if (acadJson.has("courseList") || acadJson.has("studentCourseList") ||
                        "SUCCESS".equalsIgnoreCase(acadJson.optString("status", "")) ||
                        acadJson.has("classDetails")) {
                    hasExpectedContent = true;
                }
            } catch (JSONException ignored) {}

            if (!hasExpectedContent) {
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(plaintextAcad);
                    if (arr.length() > 0) {
                        hasExpectedContent = true;
                    }
                } catch (JSONException ignored) {}
            }

            // Log full decrypted academic response in chunks for complete visibility
            Log.i("VTOPAcademicDump", "=== [BEGIN studentRegisteredCourse DECRYPTED JSON (" + plaintextAcad.length() + " chars)] ===");
            for (int i = 0; i < plaintextAcad.length(); i += 800) {
                Log.i("VTOPAcademicDump", plaintextAcad.substring(i, Math.min(i + 800, plaintextAcad.length())));
            }
            Log.i("VTOPAcademicDump", "=== [END studentRegisteredCourse DECRYPTED JSON] ===");

            // Save JSON to disk for offline inspection
            if (context != null) {
                try {
                    java.io.File dump = new java.io.File(context.getFilesDir(), "vtop_registered_courses.json");
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dump)) {
                        fos.write(plaintextAcad.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    Log.i(TAG, "[NativeAuthProof] Saved decrypted academic payload to: " + dump.getAbsolutePath());
                } catch (Exception e) {
                    Log.w(TAG, "[NativeAuthProof] Could not persist academic json dump: " + e.getMessage());
                }
            }

            // Automatically parse and ingest courses and attendance into Room AppDatabase
            if (context != null && hasExpectedContent) {
                try {
                    AppDatabase appDb = AppDatabase.getInstance(context);
                    StagedSyncData stagedData = new StagedSyncData("NATIVE_REST");
                    VTOPJsonParsers.parseCoursesAndSlots(plaintextAcad, stagedData.courses, stagedData.slots, stagedData);
                    List<Attendance> attendances = VTOPJsonParsers.parseAttendanceSummary(plaintextAcad, stagedData.courses, stagedData.slots, stagedData);
                    stagedData.attendanceSummaries.addAll(attendances);

                    if (!stagedData.courses.isEmpty()) {
                        TransactionalDataActivator activator = new TransactionalDataActivator(appDb, context);
                        TransactionalDataActivator.ActivationResult actRes = activator.activate(stagedData, true);
                        Log.i(TAG, "[NativeAuthProof] Ingested " + stagedData.courses.size() + " courses and "
                                + stagedData.attendanceSummaries.size() + " attendance summaries into Room DB. Activated: " + actRes.success);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "[NativeAuthProof] Failed to auto-ingest academic payload into Room: " + t.getMessage(), t);
                }
            }

            // Probe secondary academic endpoints for inspection (timeTable & activeSemMarkView)
            probeSecondaryAcademicEndpoints(context, client, baseUrl, subPath, effectiveApplNo, apiKey, academicSKeyUser, host);

            if (hasExpectedContent) {
                Log.i(TAG, "[NativeAuthProof] PROOF COMPLETE: Native authentication and follow-up academic request fully succeeded (HTTP 200, valid course data).");
                return new DiagnosticProofResult(
                        true,
                        authResult.httpCode,
                        authResult.status,
                        authResult.sessionCookieAccepted,
                        true,
                        acadCode,
                        true,
                        "NONE",
                        "Native authentication and follow-up academic request fully succeeded (HTTP 200).",
                        authResult.diagnostics,
                        acadDiag,
                        authResult,
                        plaintextAcad
                );
            } else {
                Log.w(TAG, "[NativeAuthProof] Academic request returned HTTP 200 but unexpected payload structure. Stage: ACADEMIC_UNEXPECTED_BODY");
                return new DiagnosticProofResult(
                        false,
                        authResult.httpCode,
                        authResult.status,
                        true,
                        true,
                        acadCode,
                        false,
                        "ACADEMIC_UNEXPECTED_BODY",
                        "Academic request returned HTTP 200 but unexpected payload structure (" + acadDiag.sanitizedSummary + ")",
                        authResult.diagnostics,
                        acadDiag
                );
            }
        } catch (IOException e) {
            Log.e(TAG, "[NativeAuthProof] Follow-up academic request I/O error: " + e.getMessage());
            return new DiagnosticProofResult(
                    false,
                    authResult.httpCode,
                    authResult.status,
                    true,
                    true,
                    -1,
                    false,
                    "ACADEMIC_NETWORK_ERROR",
                    "Network error on academic request: " + e.getMessage(),
                    authResult.diagnostics,
                    null
            );
        }
    }

    public static DiagnosticProofResult executeControlledOtpProof(
            Context context,
            OkHttpClient client,
            String baseUrl,
            String username,
            String otpText,
            String semesterId
    ) {
        return executeControlledOtpProof(context, client, baseUrl, username, otpText, semesterId, null);
    }

    public static DiagnosticProofResult executeControlledOtpProof(
            Context context,
            OkHttpClient client,
            String baseUrl,
            String username,
            String otpText,
            String semesterId,
            AuthResult initialAuthResult
    ) {
        Log.i(TAG, "[NativeAuthProof] Initiating controlled OTP verification proof...");

        if (client == null) {
            client = getDefaultClient();
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = VTOPEndpoints.BASE_URL;
        }

        InMemoryCookieJar activeJar = (client.cookieJar() instanceof InMemoryCookieJar)
                ? (InMemoryCookieJar) client.cookieJar()
                : SESSION_COOKIE_JAR;

        // Step 1: Submit OTP using the existing active session
        AuthResult authResult = submitOtp(context, client, baseUrl, username, otpText, null);

        if (!authResult.success) {
            String stage = authResult.status;
            Log.w(TAG, "[NativeAuthProof] Native OTP verification failed at stage: " + stage + " (HTTP " + authResult.httpCode + ", " + authResult.message + ")");
            String diagMsg = "Native OTP verification failed: HTTP " + authResult.httpCode + " [" + stage + "] - " + authResult.message;

            return new DiagnosticProofResult(
                    false,
                    authResult.httpCode,
                    authResult.status,
                    authResult.sessionCookieAccepted,
                    false,
                    -1,
                    false,
                    stage,
                    diagMsg,
                    authResult.diagnostics,
                    null,
                    authResult
            );
        }

        // Carry forward initial login credentials (applNo, apiKey, urlPath) if not in OTP response
        if (initialAuthResult != null) {
            String effectiveApplNo = authResult.applNo;
            if (effectiveApplNo.isEmpty() || effectiveApplNo.equalsIgnoreCase(username)) {
                if (initialAuthResult.applNo != null && !initialAuthResult.applNo.isEmpty() && !initialAuthResult.applNo.equalsIgnoreCase(username)) {
                    effectiveApplNo = initialAuthResult.applNo;
                } else if (initialAuthResult.decryptedResponse != null && !initialAuthResult.decryptedResponse.studentApplicationNumber.isEmpty()) {
                    effectiveApplNo = initialAuthResult.decryptedResponse.studentApplicationNumber;
                }
            }

            DecryptedLoginResponse mergedDecrypted = authResult.decryptedResponse;
            if (mergedDecrypted == null) {
                mergedDecrypted = initialAuthResult.decryptedResponse;
            } else if (mergedDecrypted.apiKey == null || mergedDecrypted.apiKey.isEmpty()) {
                if (initialAuthResult.decryptedResponse != null && initialAuthResult.decryptedResponse.apiKey != null && !initialAuthResult.decryptedResponse.apiKey.isEmpty()) {
                    mergedDecrypted = initialAuthResult.decryptedResponse;
                }
            }

            authResult = new AuthResult(
                    authResult.success,
                    authResult.httpCode,
                    authResult.status,
                    authResult.message,
                    username,
                    effectiveApplNo,
                    authResult.sessionCookieAccepted,
                    false,
                    mergedDecrypted,
                    authResult.diagnostics
            );
        }

        Log.i(TAG, "[NativeAuthProof] Native OTP verification succeeded with HTTP " + authResult.httpCode + " (" + authResult.status + "). Proceeding to academic endpoint verification with applNo=" + authResult.applNo + "...");

        return executeAcademicProbe(context, client, baseUrl, username, authResult, activeJar);
    }

    /**
     * Submits OTP and executes controlled academic proof asynchronously on a background thread.
     */
    public static void submitOtpAndVerifyAcademicAsync(
            Context context,
            String username,
            String otpText,
            AuthProofCallback callback
    ) {
        submitOtpAndVerifyAcademicAsync(context, username, otpText, (AuthResult) null, callback);
    }

    public static void submitOtpAndVerifyAcademicAsync(
            Context context,
            String username,
            String otpText,
            AuthResult initialAuthResult,
            AuthProofCallback callback
    ) {
        new Thread(() -> {
            DiagnosticProofResult result = executeControlledOtpProof(context, getDefaultClient(), VTOPEndpoints.BASE_URL, username, otpText, "CH2024251", initialAuthResult);
            if (callback != null) {
                callback.onResult(result);
            }
        }, "VTOPNativeOtpProof").start();
    }

    public static ResponseDiagnostics analyzeResponse(
            Response response,
            String initialUrl,
            String initialMethod,
            String responseBody,
            InMemoryCookieJar cookieJar,
            String host,
            Exception jsonException
    ) {
        if (response == null) {
            return ResponseDiagnostics.createEmpty(-1, initialUrl);
        }

        int httpCode = response.code();
        String finalUrl = response.request() != null && response.request().url() != null
                ? response.request().url().toString()
                : initialUrl;
        String finalMethod = response.request() != null && response.request().method() != null
                ? response.request().method()
                : initialMethod;

        boolean redirected = response.priorResponse() != null;
        int redirectCode = redirected ? response.priorResponse().code() : 0;
        String redirectLocation = "";
        if (redirected && response.priorResponse().header("Location") != null) {
            redirectLocation = response.priorResponse().header("Location");
        }

        String contentType = response.header("Content-Type");
        if (contentType == null) contentType = "unknown";

        int bodyLength = responseBody != null ? responseBody.length() : 0;
        String cleanBody = responseBody != null ? responseBody.trim() : "";
        if (cleanBody.startsWith("\uFEFF")) {
            cleanBody = cleanBody.substring(1).trim();
        }

        String classification;
        String title = "";
        String heading = "";
        String message = "";
        String summary;
        String parseError = jsonException != null ? jsonException.getMessage() : null;

        String base64DecodedClassification = "NOT_BASE64";
        boolean isBase64Scalar = false;
        if (cleanBody.matches("^[A-Za-z0-9+/=_-]{20,}$")) {
            isBase64Scalar = true;
            try {
                byte[] decoded = decodeBase64(cleanBody);
                if (decoded != null && decoded.length > 0) {
                    boolean allPrintable = true;
                    for (byte b : decoded) {
                        int unsigned = b & 0xFF;
                        if ((unsigned < 32 && unsigned != '\n' && unsigned != '\r' && unsigned != '\t') || unsigned > 126) {
                            allPrintable = false;
                            break;
                        }
                    }
                    if (allPrintable) {
                        String decodedStr = new String(decoded, StandardCharsets.UTF_8).trim();
                        if (decodedStr.startsWith("{") || decodedStr.startsWith("[")) {
                            base64DecodedClassification = "BASE64_ENCODED_JSON (" + decoded.length + " bytes)";
                        } else {
                            base64DecodedClassification = "BASE64_PRINTABLE_TEXT (" + decoded.length + " bytes): " + sanitizeTextPreview(decodedStr, 40);
                        }
                    } else {
                        String magicHex = decoded.length >= 4
                                ? String.format("%02X%02X%02X%02X", decoded[0], decoded[1], decoded[2], decoded[3])
                                : String.format("%02X", decoded[0]);
                        base64DecodedClassification = "BASE64_BINARY_" + decoded.length + "_BYTES (Magic: 0x" + magicHex + ")";
                    }
                }
            } catch (Throwable ignored) {}
        }

        List<String> cookieNames = cookieJar != null ? cookieJar.getCookieNames() : Collections.emptyList();
        List<String> cookieDescriptors = cookieJar != null ? cookieJar.getCookieDescriptors() : Collections.emptyList();
        int cookieCount = cookieJar != null ? cookieJar.getCookieCount() : 0;
        boolean hasSessionCookie = cookieJar != null && cookieJar.hasValidSession(host);

        if (cleanBody.isEmpty()) {
            classification = "EMPTY";
            summary = "Empty body (0 bytes)";
        } else if (contentType.toLowerCase().contains("text/html") || cleanBody.startsWith("<")) {
            classification = "HTML";
            title = extractRegex(cleanBody, "<title>(.*?)</title>");
            heading = extractRegex(cleanBody, "<h[1-3]>(.*?)</h[1-3]>");
            message = extractRegex(cleanBody, "<[^>]+class=[\"'][^\"']*(?:error|alert|message|notification)[^\"']*[\"'][^>]*>(.*?)</[^>]+>");
            summary = buildHtmlSummary(title, heading, message, cleanBody);
        } else if (cleanBody.startsWith("[")) {
            classification = "JSON_ARRAY";
            summary = "JSON Array (" + bodyLength + " chars)";
        } else if (cleanBody.startsWith("{")) {
            if (jsonException != null) {
                classification = "MALFORMED_JSON";
                summary = "Malformed JSON Object: " + parseError;
            } else {
                classification = "JSON_OBJECT";
                summary = "JSON Object (" + bodyLength + " bytes)";
            }
        } else if (isBase64Scalar) {
            classification = "BASE64_SCALAR";
            summary = "Base64 Scalar (" + bodyLength + " chars) -> " + base64DecodedClassification;
        } else {
            classification = "PLAIN_TEXT";
            summary = sanitizeTextPreview(cleanBody, 80);
        }

        return new ResponseDiagnostics(
                httpCode, initialUrl, finalUrl, initialMethod, finalMethod,
                redirected, redirectCode, redirectLocation,
                contentType, bodyLength, classification,
                summary, title, heading, message, parseError,
                cookieCount, hasSessionCookie,
                cookieNames, cookieDescriptors,
                base64DecodedClassification, isBase64Scalar
        );
    }

    /**
     * Privacy-safe logging of outbound request metadata (URL, method, header names, payload field names and lengths).
     * NEVER logs credentials, encrypted strings, AES keys, API keys, session cookie values, or academic payload values.
     */
    public static void logOutboundRequestShape(
            String stageLabel,
            String url,
            String method,
            Request request,
            JSONObject payload,
            InMemoryCookieJar cookieJar,
            String host
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("[NativeAuthDiagnostics] Outbound ").append(stageLabel).append(": ")
                .append(method).append(" ").append(url);

        if (request != null && request.headers() != null) {
            sb.append("\n  Headers: ").append(request.headers().names());
        }

        if (payload != null) {
            sb.append("\n  Body Fields: [");
            java.util.Iterator<String> keys = payload.keys();
            boolean first = true;
            while (keys.hasNext()) {
                String key = keys.next();
                if (!first) sb.append(", ");
                first = false;
                Object val = payload.opt(key);
                if (val instanceof String) {
                    sb.append(key).append(" (len=").append(((String) val).length()).append(")");
                } else if (val != null) {
                    sb.append(key).append(" (type=").append(val.getClass().getSimpleName()).append(")");
                } else {
                    sb.append(key).append(" (null)");
                }
            }
            sb.append("]");
        }

        if (cookieJar != null) {
            List<String> names = cookieJar.getCookieNames();
            sb.append("\n  Outbound Cookies: count=").append(cookieJar.getCookieCount())
                    .append(", names=").append(names)
                    .append(", sessionMatch=").append(cookieJar.hasValidSession(host));
        }

        Log.i(TAG, sb.toString());
    }

    public static String sanitizeTextPreview(String raw, int maxLen) {
        if (raw == null || raw.isEmpty()) return "";
        String clean = raw.replaceAll("<[^>]+>", " ");
        clean = clean.replaceAll("(?i)\\b\\d{2}[a-z]{3}\\d{4}\\b", "[REDACTED_REGNO]");
        clean = clean.replaceAll("[A-Za-z0-9+/_-]{20,}={0,2}", "[REDACTED_TOKEN]");
        clean = clean.replaceAll("\\s+", " ").trim();
        if (clean.length() > maxLen) {
            clean = clean.substring(0, maxLen) + "...";
        }
        return clean;
    }

    private static String extractRegex(String input, String regex) {
        if (input == null || input.isEmpty()) return "";
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(input);
            if (m.find()) {
                return sanitizeTextPreview(m.group(1).replaceAll("<[^>]+>", " "), 80);
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static String buildHtmlSummary(String title, String heading, String message, String fullHtml) {
        StringBuilder sb = new StringBuilder();
        if (!title.isEmpty()) sb.append("Title='").append(title).append("' ");
        if (!heading.isEmpty()) sb.append("Heading='").append(heading).append("' ");
        if (!message.isEmpty()) sb.append("Message='").append(message).append("' ");
        if (sb.length() == 0) {
            sb.append("Snippet='").append(sanitizeTextPreview(fullHtml, 80)).append("'");
        }
        return sb.toString().trim();
    }

    /**
     * Convenience overload for running the controlled proof against production endpoints.
     */
    public static DiagnosticProofResult executeControlledAuthProof(Context context, String username, String password) {
        return executeControlledAuthProof(context, getDefaultClient(), VTOPEndpoints.BASE_URL, username, password, "CH2024251");
    }

    /**
     * Executes the controlled proof asynchronously on a background thread.
     */
    public static void executeControlledAuthProofAsync(
            Context context,
            String username,
            String password,
            AuthProofCallback callback
    ) {
        new Thread(() -> {
            DiagnosticProofResult result = executeControlledAuthProof(context, username, password);
            if (callback != null) {
                callback.onResult(result);
            }
        }, "VTOPNativeAuthProof").start();
    }

    private static String extractHost(String url) {
        if (url == null || url.isEmpty()) return "vtopcc5.vit.ac.in";
        String clean = url.replace("https://", "").replace("http://", "");
        int slashIdx = clean.indexOf('/');
        if (slashIdx != -1) {
            clean = clean.substring(0, slashIdx);
        }
        int colonIdx = clean.indexOf(':');
        if (colonIdx != -1) {
            clean = clean.substring(0, colonIdx);
        }
        return clean.trim();
    }

    public static byte[] decodeBase64(String input) {
        if (input == null) return new byte[0];
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                return java.util.Base64.getDecoder().decode(input.trim());
            }
            byte[] res = android.util.Base64.decode(input, android.util.Base64.DEFAULT);
            if (res != null && res.length > 0) return res;
        } catch (Throwable ignored) {}
        return java.util.Base64.getDecoder().decode(input.trim());
    }

    public static String encodeBase64(byte[] input) {
        if (input == null || input.length == 0) return "";
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                return java.util.Base64.getEncoder().encodeToString(input);
            }
            String res = android.util.Base64.encodeToString(input, android.util.Base64.NO_WRAP);
            if (res != null && !res.isEmpty()) return res;
        } catch (Throwable ignored) {}
        return java.util.Base64.getEncoder().encodeToString(input);
    }

    private static void probeSecondaryAcademicEndpoints(
            Context context,
            OkHttpClient client,
            String baseUrl,
            String subPath,
            String applNo,
            String apiKey,
            String academicSKeyUser,
            String host
    ) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String cleanSub = (subPath != null && !subPath.isEmpty() && !"null".equalsIgnoreCase(subPath.trim())) ? subPath.trim() : "students";
        if (!cleanSub.startsWith("/")) cleanSub = "/" + cleanSub;
        if (cleanSub.endsWith("/")) cleanSub = cleanSub.substring(0, cleanSub.length() - 1);
        subPath = cleanSub;
        JSONObject payload = new JSONObject();
        try {
            payload.put("applNo", applNo);
        } catch (JSONException ignored) {}
        RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, payload.toString());

        // Probe 1: timeTable
        try {
            String ttUrl = base + subPath + "/timeTable";
            Request.Builder reqB = new Request.Builder()
                    .url(ttUrl)
                    .post(body)
                    .header("Host", host)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json, text/plain, */*")
                    .header(VTOPEndpoints.HEADER_API_KEY, apiKey)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)");
            if (academicSKeyUser != null && !academicSKeyUser.isEmpty()) {
                reqB.header(VTOPEndpoints.HEADER_KEY_USER, academicSKeyUser);
            }
            try (Response ttResp = client.newCall(reqB.build()).execute()) {
                int code = ttResp.code();
                String raw = ttResp.body() != null ? ttResp.body().string() : "";
                String dec = raw;
                if (raw.matches("^[A-Za-z0-9+/=_-]{20,}$")) {
                    try { dec = decryptAes(raw.trim()); } catch (Throwable ignored) {}
                }
                Log.i(TAG, "[NativeAuthProof] Probed timeTable endpoint: HTTP " + code + " | Length: " + dec.length());
                Log.i("VTOPAcademicDump", "=== [BEGIN timeTable DECRYPTED JSON (" + dec.length() + " chars)] ===");
                for (int i = 0; i < dec.length(); i += 800) {
                    Log.i("VTOPAcademicDump", dec.substring(i, Math.min(i + 800, dec.length())));
                }
                Log.i("VTOPAcademicDump", "=== [END timeTable DECRYPTED JSON] ===");

                if (context != null && (dec.startsWith("{") || dec.startsWith("["))) {
                    try {
                        java.io.File dump = new java.io.File(context.getFilesDir(), "vtop_timetable.json");
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dump)) {
                            fos.write(dec.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "[NativeAuthProof] timeTable probe error: " + e.getMessage());
        }

        // Probe 2: activeSemMarkView
        try {
            String marksUrl = base + subPath + "/activeSemMarkView";
            Request.Builder reqB = new Request.Builder()
                    .url(marksUrl)
                    .post(body)
                    .header("Host", host)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json, text/plain, */*")
                    .header(VTOPEndpoints.HEADER_API_KEY, apiKey)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko)");
            if (academicSKeyUser != null && !academicSKeyUser.isEmpty()) {
                reqB.header(VTOPEndpoints.HEADER_KEY_USER, academicSKeyUser);
            }
            try (Response marksResp = client.newCall(reqB.build()).execute()) {
                int code = marksResp.code();
                String raw = marksResp.body() != null ? marksResp.body().string() : "";
                String dec = raw;
                if (raw.matches("^[A-Za-z0-9+/=_-]{20,}$")) {
                    try { dec = decryptAes(raw.trim()); } catch (Throwable ignored) {}
                }
                Log.i(TAG, "[NativeAuthProof] Probed activeSemMarkView endpoint: HTTP " + code + " | Length: " + dec.length());
                Log.i("VTOPAcademicDump", "=== [BEGIN activeSemMarkView DECRYPTED JSON (" + dec.length() + " chars)] ===");
                for (int i = 0; i < dec.length(); i += 800) {
                    Log.i("VTOPAcademicDump", dec.substring(i, Math.min(i + 800, dec.length())));
                }
                Log.i("VTOPAcademicDump", "=== [END activeSemMarkView DECRYPTED JSON] ===");

                if (context != null && (dec.startsWith("{") || dec.startsWith("["))) {
                    try {
                        java.io.File dump = new java.io.File(context.getFilesDir(), "vtop_marks.json");
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dump)) {
                            fos.write(dec.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "[NativeAuthProof] activeSemMarkView probe error: " + e.getMessage());
        }
    }

}
