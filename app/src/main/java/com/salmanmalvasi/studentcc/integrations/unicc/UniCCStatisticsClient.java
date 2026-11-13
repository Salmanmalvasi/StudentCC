package com.salmanmalvasi.studentcc.integrations.unicc;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CookieJar;
import okhttp3.Dispatcher;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Public aggregate GET client, deliberately independent of StudentCC's authenticated clients. */
public final class UniCCStatisticsClient {
    private static final String API_ORIGIN = "https://api-unicc.arya22.dev/";
    private static final long MAX_RESPONSE_BYTES = 64 * 1024;
    private final HttpUrl origin;
    private final OkHttpClient client;

    enum FailureKind { NO_DATA, INVALID_PAYLOAD, TEMPORARY, PERMANENT }

    static final class FetchException extends IOException {
        final FailureKind kind;
        FetchException(FailureKind kind, String message) { super(message); this.kind = kind; }
    }

    public UniCCStatisticsClient() { this(HttpUrl.parse(API_ORIGIN)); }

    /** Package-private alternate origin for isolated MockWebServer tests. */
    UniCCStatisticsClient(HttpUrl origin) {
        if (origin == null) throw new IllegalArgumentException("UniCC origin is required");
        this.origin = origin;
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(4);
        dispatcher.setMaxRequestsPerHost(4);
        client = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .cookieJar(CookieJar.NO_COOKIES)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    Single<UniCCClassStatistics> fetchStatistics(String classId) {
        return Single.create(emitter -> {
            HttpUrl url = origin.newBuilder().encodedPath("/api/attendance/marks")
                    .query(null).addQueryParameter("classId", classId).build();
            Request request = new Request.Builder().url(url)
                    .header("Accept", "application/json").get().build();
            Call call = client.newCall(request);
            emitter.setCancellable(call::cancel);
            if (emitter.isDisposed()) return;
            call.enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException error) {
                    if (!emitter.isDisposed()) emitter.tryOnError(new FetchException(
                            FailureKind.TEMPORARY, "Cannot reach UniCC class statistics."));
                }

                @Override public void onResponse(Call call, Response response) {
                    try (Response closedResponse = response) {
                        if (emitter.isDisposed()) return;
                        ResponseBody body = closedResponse.body();
                        if (body == null || body.contentLength() > MAX_RESPONSE_BYTES) {
                            throw new FetchException(FailureKind.INVALID_PAYLOAD,
                                    "UniCC returned an invalid statistics response.");
                        }
                        // Bound allocation even when the remote response omits Content-Length.
                        String json = closedResponse.peekBody(MAX_RESPONSE_BYTES + 1).string();
                        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                            throw new FetchException(FailureKind.INVALID_PAYLOAD,
                                    "UniCC returned an oversized statistics response.");
                        }
                        if (closedResponse.code() == 404 || isKnownMissingData(closedResponse.code(), json)) {
                            throw new FetchException(FailureKind.NO_DATA,
                                    "No UniCC samples are available for this class yet.");
                        }
                        if (!closedResponse.isSuccessful()) {
                            throw new FetchException(closedResponse.code() >= 500 || closedResponse.code() == 429
                                    ? FailureKind.TEMPORARY : FailureKind.PERMANENT,
                                    "UniCC class statistics are currently unavailable.");
                        }
                        UniCCClassStatistics statistics = parseStatistics(json);
                        if (!emitter.isDisposed()) emitter.onSuccess(statistics);
                    } catch (FetchException error) {
                        if (!emitter.isDisposed()) emitter.tryOnError(error);
                    } catch (IOException error) {
                        if (!emitter.isDisposed()) emitter.tryOnError(new FetchException(
                                FailureKind.TEMPORARY, "Cannot read UniCC class statistics."));
                    } catch (RuntimeException error) {
                        if (!emitter.isDisposed()) emitter.tryOnError(new FetchException(
                                FailureKind.INVALID_PAYLOAD, "UniCC returned invalid statistics."));
                    }
                }
            });
        });
    }

    static UniCCClassStatistics parseStatistics(String json) throws FetchException {
        try {
            JSONObject object = new JSONObject(json);
            double mean = number(object, "mean");
            double variance = number(object, "variance");
            double sd = number(object, "sd");
            double count = number(object, "count");
            if (count == 0) throw new FetchException(FailureKind.NO_DATA,
                    "No UniCC samples are available for this class yet.");
            if (count < 1 || count > Integer.MAX_VALUE || count != Math.rint(count)) {
                throw new IllegalArgumentException("Invalid sample count");
            }
            // Reject a changed basis if the server starts publishing metadata in future.
            if (object.has("marksBasis") && number(object, "marksBasis") != UniCCClassStatistics.CURRENT_MARKS_BASIS) {
                throw new IllegalArgumentException("Unsupported marks basis");
            }
            return new UniCCClassStatistics(mean, variance, sd, (int) count);
        } catch (JSONException | IllegalArgumentException error) {
            throw new FetchException(FailureKind.INVALID_PAYLOAD,
                    "UniCC returned statistics incompatible with internal marks out of 60.");
        }
    }

    private static double number(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof Number)) throw new JSONException("Expected numeric " + key);
        double numeric = ((Number) value).doubleValue();
        if (!Double.isFinite(numeric)) throw new JSONException("Expected finite " + key);
        return numeric;
    }

    private static boolean isKnownMissingData(int code, String json) {
        if (code != 500) return false;
        try {
            String error = new JSONObject(json).optString("error", "");
            return error.contains("No data found for classID") || error.equals("Class statistics not found");
        } catch (JSONException ignored) { return false; }
    }
}
