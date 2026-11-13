package com.salmanmalvasi.studentcc.integrations.studentcc;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.salmanmalvasi.studentcc.BuildConfig;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import io.reactivex.rxjava3.core.Single;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CookieJar;
import okhttp3.Dispatcher;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** A separate, credential-free read client and explicitly opted-in contribution client. */
public final class StudentCCStatisticsClient {
    static final long MAX_RESPONSE_BYTES = 64 * 1024;
    enum FailureKind { NOT_CONFIGURED, INVALID_PAYLOAD, AUTHENTICATION, TEMPORARY, PERMANENT }
    static final class FetchException extends IOException {
        final FailureKind kind;
        FetchException(FailureKind kind, String message) { super(message); this.kind = kind; }
    }
    static final class AuthToken {
        final String token, subject;
        AuthToken(String token, String subject) { this.token = token; this.subject = subject; }
    }
    interface TokenProvider { Single<AuthToken> bearerToken(); }
    private final HttpUrl origin;
    private final boolean localMode;
    private final TokenProvider tokens;
    private final OkHttpClient client;

    public StudentCCStatisticsClient() {
        this(BuildConfig.STUDENTCC_STATS_BASE_URL, BuildConfig.STUDENTCC_STATS_LOCAL_MODE, BuildConfig.DEBUG,
                StudentCCStatisticsClient::firebaseToken);
    }

    StudentCCStatisticsClient(String baseUrl, boolean localMode, boolean debug, TokenProvider tokens) {
        HttpUrl parsed = baseUrl == null || baseUrl.trim().isEmpty() ? null : HttpUrl.parse(baseUrl.trim());
        if (parsed != null && (!parsed.username().isEmpty() || !parsed.password().isEmpty()
                || parsed.query() != null || parsed.fragment() != null)) parsed = null;
        if (parsed != null && localMode && (!debug || !loopback(parsed.host()))) parsed = null;
        if (parsed != null && !localMode && !"https".equals(parsed.scheme())) parsed = null;
        this.origin = parsed == null ? null : HttpUrl.parse(parsed.toString().endsWith("/") ? parsed.toString() : parsed + "/");
        this.localMode = localMode; this.tokens = tokens;
        Dispatcher dispatcher = new Dispatcher(); dispatcher.setMaxRequests(4); dispatcher.setMaxRequestsPerHost(4);
        client = new OkHttpClient.Builder().dispatcher(dispatcher).cookieJar(CookieJar.NO_COOKIES)
                .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).writeTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
    }

    public boolean isConfigured() { return origin != null; }
    public String backendOrigin() { return origin == null ? "" : origin.toString(); }
    static StudentCCStatisticsClient forRemovalEndpoint(String endpoint) {
        HttpUrl parsed = endpoint == null ? null : HttpUrl.parse(endpoint);
        boolean local = parsed != null && "http".equals(parsed.scheme());
        return new StudentCCStatisticsClient(endpoint, local, BuildConfig.DEBUG && BuildConfig.STUDENTCC_STATS_LOCAL_MODE,
                StudentCCStatisticsClient::firebaseToken);
    }

    Single<StudentCCClassStatistics> fetchStatistics(StudentCCClassScope scope) {
        if (!isConfigured()) return notConfigured();
        HttpUrl url = origin.resolve("v1/class-statistics").newBuilder()
                .addQueryParameter("campus", scope.campus).addQueryParameter("semesterId", scope.semesterId)
                .addQueryParameter("classId", scope.classId).addQueryParameter("stage", scope.stage)
                .addQueryParameter("marksBasis", Integer.toString(scope.marksBasis)).build();
        return execute(new Request.Builder().url(url).header("Accept", "application/json").get().build())
                .map(json -> parseStatistics(json, scope));
    }

    Single<StudentCCClassStatistics> submit(StudentCCContribution contribution, String participant) {
        return writer(participant, null).flatMap(writer -> writer.submit(contribution));
    }

    final class AuthenticatedWriter {
        final Authentication auth;
        final String subject;
        AuthenticatedWriter(Authentication auth) { this.auth = auth; this.subject = auth.subject; }
        Single<StudentCCClassStatistics> submit(StudentCCContribution contribution) {
            try {
                Request.Builder request = new Request.Builder().url(origin.resolve("v1/contributions"))
                        .header("Accept", "application/json").post(RequestBody.create(
                                MediaType.parse("application/json; charset=utf-8"), contribution.toJson().toString()));
                auth.apply(request);
                return execute(request.build()).map(json -> parseStatistics(json, contribution.scope));
            } catch (JSONException error) { return Single.error(new FetchException(FailureKind.INVALID_PAYLOAD, "Invalid contribution.")); }
        }
        Single<Boolean> submitAcademicSummary(boolean cgpaAtLeastNine) {
            return submitAcademicSummary(cgpaAtLeastNine, false);
        }
        Single<Boolean> submitAcademicSummary(boolean cgpaAtLeastNine, boolean shareClassTally) {
            try {
                JSONObject payload = new JSONObject().put("cgpaAtLeastNine", cgpaAtLeastNine);
                if (shareClassTally) payload.put("shareClassTally", true);
                Request.Builder request = new Request.Builder().url(origin.resolve("v1/academic-summary"))
                        .header("Accept", "application/json").post(RequestBody.create(
                                MediaType.parse("application/json; charset=utf-8"), payload.toString()));
                auth.apply(request);
                return execute(request.build()).map(json -> {
                    try {
                        JSONObject reply = new JSONObject(json);
                        if (!"studentcc".equals(reply.getString("source")) || !"saved".equals(reply.getString("status"))) {
                            throw new JSONException("Academic summary was not acknowledged");
                        }
                        return true;
                    } catch (JSONException invalid) {
                        throw new FetchException(FailureKind.INVALID_PAYLOAD, "Cannot confirm the academic summary was saved.");
                    }
                });
            } catch (JSONException invalid) {
                return Single.error(new FetchException(FailureKind.INVALID_PAYLOAD, "Invalid academic summary."));
            }
        }
        Single<Integer> withdraw() {
            Request.Builder request = new Request.Builder().url(origin.resolve("v1/contributions"))
                    .header("Accept", "application/json").delete();
            auth.apply(request);
            return execute(request.build()).map(json -> {
                try {
                    JSONObject object = new JSONObject(json);
                    if (!"studentcc".equals(object.getString("source")) || !"withdrawn".equals(object.getString("status"))) {
                        throw new JSONException("Unexpected withdrawal response");
                    }
                    return integer(object, "deletedContributions", 0, Integer.MAX_VALUE);
                } catch (JSONException error) { throw new FetchException(FailureKind.INVALID_PAYLOAD, "Cannot confirm contribution removal."); }
            });
        }
    }

    Single<Integer> withdraw(String participant) {
        return writer(participant, null).flatMap(AuthenticatedWriter::withdraw);
    }

    Single<AuthenticatedWriter> writer(String participant, String expectedSubject) {
        return authenticated(participant, expectedSubject).map(AuthenticatedWriter::new);
    }

    private Single<Authentication> authenticated(String participant, String expectedSubject) {
        if (!isConfigured()) return notConfigured();
        if (localMode) {
            try {
                UUID value = UUID.fromString(participant);
                if (value.version() != 4 || !value.toString().equals(participant)) throw new IllegalArgumentException();
                String subject = "local:" + participant;
                if (expectedSubject != null && !expectedSubject.equals(subject)) throw new IllegalArgumentException();
                return Single.just(new Authentication(participant, null, subject));
            } catch (RuntimeException error) { return Single.error(new FetchException(FailureKind.AUTHENTICATION, "Local participant identity is unavailable.")); }
        }
        return tokens.bearerToken().map(token -> {
            UUID alias;
            try { alias = UUID.fromString(participant); } catch (RuntimeException error) { throw new FetchException(FailureKind.AUTHENTICATION, "Participant identity is unavailable."); }
            if (alias.version() != 4 || !alias.toString().equals(participant) || token == null || token.token == null
                    || token.token.trim().isEmpty() || token.token.contains("\n") || token.token.contains("\r") || token.subject == null) {
                throw new FetchException(FailureKind.AUTHENTICATION, "StudentCC authentication is unavailable.");
            }
            if (expectedSubject != null && !expectedSubject.equals(token.subject)) throw new FetchException(FailureKind.AUTHENTICATION,
                    "The original sharing identity is required to update or remove these contributions.");
            return new Authentication(participant, token.token, token.subject);
        }).onErrorResumeNext(error -> Single.error(authenticationFailure(error)));
    }

    private static FetchException authenticationFailure(Throwable error) {
        if (error instanceof FetchException) return (FetchException) error;
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof FirebaseNetworkException) {
                return new FetchException(FailureKind.TEMPORARY,
                        "Cannot reach StudentCC authentication. Your saved totals will retry when connected.");
            }
            Throwable next = cause.getCause();
            if (next == cause) break;
            cause = next;
        }
        return new FetchException(FailureKind.AUTHENTICATION, "StudentCC authentication is unavailable.");
    }

    private static final class Authentication {
        final String participant, bearer, subject;
        Authentication(String participant, String bearer, String subject) { this.participant = participant; this.bearer = bearer; this.subject = subject; }
        void apply(Request.Builder request) {
            request.header("X-StudentCC-Participant", participant);
            if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        }
    }

    private Single<String> execute(Request request) {
        return Single.create(emitter -> {
            Call call = client.newCall(request); emitter.setCancellable(call::cancel);
            if (emitter.isDisposed()) return;
            call.enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException error) {
                    if (!emitter.isDisposed()) emitter.tryOnError(new FetchException(FailureKind.TEMPORARY, "Cannot reach StudentCC statistics."));
                }
                @Override public void onResponse(Call call, Response response) {
                    try (Response closed = response) {
                        if (emitter.isDisposed()) return;
                        if (closed.body() == null || closed.body().contentLength() > MAX_RESPONSE_BYTES) {
                            throw new FetchException(FailureKind.INVALID_PAYLOAD, "Invalid statistics response.");
                        }
                        String json = closed.peekBody(MAX_RESPONSE_BYTES + 1).string();
                        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) throw new FetchException(FailureKind.INVALID_PAYLOAD, "Oversized statistics response.");
                        if (!closed.isSuccessful()) throw new FetchException(closed.code() == 401 || closed.code() == 403
                                ? FailureKind.AUTHENTICATION : closed.code() >= 500 || closed.code() == 429
                                ? FailureKind.TEMPORARY : FailureKind.PERMANENT, "StudentCC statistics request could not be completed.");
                        if (!emitter.isDisposed()) emitter.onSuccess(json);
                    } catch (IOException error) {
                        if (!emitter.isDisposed()) emitter.tryOnError(error instanceof FetchException ? error
                                : new FetchException(FailureKind.TEMPORARY, "Cannot read StudentCC statistics."));
                    } catch (RuntimeException error) {
                        if (!emitter.isDisposed()) emitter.tryOnError(new FetchException(FailureKind.INVALID_PAYLOAD, "Invalid statistics response."));
                    }
                }
            });
        });
    }

    static StudentCCClassStatistics parseStatistics(String json, StudentCCClassScope expected) throws FetchException {
        try {
            JSONObject object = new JSONObject(json);
            if (!"studentcc".equals(object.getString("source")) || !expected.campus.equals(object.getString("campus"))
                    || !expected.semesterId.equals(object.getString("semesterId")) || !expected.classId.equals(object.getString("classId"))
                    || !expected.stage.equals(object.getString("stage")) || number(object, "marksBasis") != expected.marksBasis) {
                throw new JSONException("Wrong cohort or assessment basis");
            }
            int count = integer(object, "count", 0, Integer.MAX_VALUE);
            Integer cgpaAtLeastNineCount = object.has("cgpaAtLeastNineCount")
                    ? integer(object, "cgpaAtLeastNineCount", 0, Integer.MAX_VALUE) : null;
            int minimum = integer(object, "minContributors", 1, 1);
            boolean available = count >= minimum;
            if (!(available ? "available" : "insufficient_data").equals(object.getString("status"))) throw new JSONException("Unexpected statistics status");
            if (!available && (object.has("mean") || object.has("variance") || object.has("sd"))) throw new JSONException("Empty cohort has values");
            return new StudentCCClassStatistics(expected, count, minimum, available ? number(object, "mean") : null,
                    available ? number(object, "variance") : null, available ? number(object, "sd") : null,
                    object.isNull("updatedAt") ? 0 : parseTimestamp(object.getString("updatedAt")), cgpaAtLeastNineCount);
        } catch (JSONException | IllegalArgumentException error) { throw new FetchException(FailureKind.INVALID_PAYLOAD, "StudentCC returned incompatible class statistics."); }
    }

    static double number(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) throw new JSONException("Expected finite number");
        return ((Number) value).doubleValue();
    }
    static int integer(JSONObject object, String key, int minimum, int maximum) throws JSONException {
        double value = number(object, key);
        if (value < minimum || value > maximum || value != Math.rint(value)) throw new JSONException("Invalid integer");
        return (int) value;
    }
    private static long parseTimestamp(String value) throws JSONException {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z")) throw new JSONException("Invalid timestamp");
        String normalized = value;
        int dot = value.indexOf('.');
        if (dot >= 0) {
            String fraction = (value.substring(dot + 1, value.length() - 1) + "000").substring(0, 3);
            normalized = value.substring(0, dot) + "." + fraction + "Z";
        }
        SimpleDateFormat format = new SimpleDateFormat(dot >= 0 ? "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" : "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setLenient(false); format.setTimeZone(TimeZone.getTimeZone("UTC"));
        ParsePosition position = new ParsePosition(0); java.util.Date parsed = format.parse(normalized, position);
        if (parsed == null || position.getIndex() != normalized.length()) throw new JSONException("Invalid timestamp");
        return parsed.getTime();
    }
    private static boolean loopback(String host) { return "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host) || "10.0.2.2".equals(host); }
    private static <T> Single<T> notConfigured() { return Single.error(new FetchException(FailureKind.NOT_CONFIGURED, "StudentCC statistics server is not configured.")); }
    private static Single<AuthToken> firebaseToken() {
        return Single.create(emitter -> {
            try {
                FirebaseAuth auth = FirebaseAuth.getInstance(); FirebaseUser existing = auth.getCurrentUser();
                if (existing != null) deliverFirebaseToken(existing, emitter);
                else auth.signInAnonymously().addOnSuccessListener(result -> {
                    if (result.getUser() == null) emitter.tryOnError(new IllegalStateException("No authenticated participant"));
                    else deliverFirebaseToken(result.getUser(), emitter);
                }).addOnFailureListener(emitter::tryOnError);
            } catch (RuntimeException error) { emitter.tryOnError(error); }
        });
    }
    private static void deliverFirebaseToken(FirebaseUser user, io.reactivex.rxjava3.core.SingleEmitter<AuthToken> emitter) {
        if (emitter.isDisposed()) return;
        user.getIdToken(false).addOnSuccessListener(result -> {
            if (!emitter.isDisposed()) emitter.onSuccess(new AuthToken(result.getToken() == null ? "" : result.getToken(), user.getUid()));
        }).addOnFailureListener(emitter::tryOnError);
    }
}
