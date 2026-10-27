package com.salmanmalvasi.studentcc.sync;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Protocol;

/**
 * Temporary, debug-only network diagnostics EventListener for VTOP API requests.
 * Tracks DNS resolution, multi-IP connection attempts, failover events, and latency.
 *
 * Privacy Rules:
 * Never logs credentials, passwords, registration numbers, request/response bodies,
 * API keys, session cookies, auth headers, marks, or attendance records.
 */
public class VTOPNetworkDiagnosticsListener extends EventListener {
    private static final String TAG = "VTOPNetDiagnostics";

    public static final Factory FACTORY = new Factory() {
        private final AtomicInteger callSequence = new AtomicInteger(0);

        @Override
        public EventListener create(Call call) {
            return new VTOPNetworkDiagnosticsListener(callSequence.incrementAndGet());
        }
    };

    private final int callId;
    private long callStartTimeMs;
    private long connectStartTimeMs;
    private int connectionAttemptCount = 0;

    public VTOPNetworkDiagnosticsListener(int callId) {
        this.callId = callId;
    }

    @Override
    public void callStart(Call call) {
        callStartTimeMs = System.currentTimeMillis();
        String method = call.request().method();
        String path = call.request().url().encodedPath();
        Log.i(TAG, String.format("[Call #%d] START: %s %s", callId, method, path));
    }

    @Override
    public void dnsStart(Call call, String domainName) {
        Log.d(TAG, String.format("[Call #%d] DNS START: resolving domain '%s'", callId, domainName));
    }

    @Override
    public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
        StringBuilder sb = new StringBuilder();
        if (inetAddressList != null) {
            for (int i = 0; i < inetAddressList.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(inetAddressList.get(i).getHostAddress());
            }
        }
        Log.i(TAG, String.format("[Call #%d] DNS END: domain '%s' resolved to %d address(es): [%s]",
                callId, domainName, inetAddressList != null ? inetAddressList.size() : 0, sb.toString()));
    }

    @Override
    public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
        connectStartTimeMs = System.currentTimeMillis();
        connectionAttemptCount++;
        String targetIp = inetSocketAddress.getAddress() != null ? inetSocketAddress.getAddress().getHostAddress() : "unknown";
        int port = inetSocketAddress.getPort();
        Log.i(TAG, String.format("[Call #%d] CONNECT START (Attempt #%d): targeting %s:%d via proxy=%s",
                callId, connectionAttemptCount, targetIp, port, proxy != null ? proxy.type() : "DIRECT"));
    }

    @Override
    public void connectEnd(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, Protocol protocol) {
        long durationMs = System.currentTimeMillis() - connectStartTimeMs;
        String targetIp = inetSocketAddress.getAddress() != null ? inetSocketAddress.getAddress().getHostAddress() : "unknown";
        Log.i(TAG, String.format("[Call #%d] CONNECT SUCCESS: connected to %s:%d in %d ms (protocol=%s)",
                callId, targetIp, inetSocketAddress.getPort(), durationMs, protocol));

        // Record reachable IP for adaptive DNS prioritization
        if (inetSocketAddress.getAddress() != null) {
            VTOPAdaptiveDns.recordSuccessfulIp(targetIp);
        }
    }

    @Override
    public void connectFailed(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, Protocol protocol, IOException ioe) {
        long durationMs = System.currentTimeMillis() - connectStartTimeMs;
        String targetIp = inetSocketAddress.getAddress() != null ? inetSocketAddress.getAddress().getHostAddress() : "unknown";
        Log.w(TAG, String.format("[Call #%d] CONNECT FAILED: target %s:%d failed after %d ms. Error: %s",
                callId, targetIp, inetSocketAddress.getPort(), durationMs, ioe.getMessage()));
    }

    @Override
    public void connectionAcquired(Call call, Connection connection) {
        if (connection != null && connection.route() != null) {
            InetSocketAddress socketAddress = connection.route().socketAddress();
            String ip = socketAddress.getAddress() != null ? socketAddress.getAddress().getHostAddress() : "unknown";
            Log.d(TAG, String.format("[Call #%d] CONNECTION ACQUIRED: reusing active pooled connection to %s:%d",
                    callId, ip, socketAddress.getPort()));
        }
    }

    @Override
    public void callEnd(Call call) {
        long totalDurationMs = System.currentTimeMillis() - callStartTimeMs;
        String path = call.request().url().encodedPath();
        Log.i(TAG, String.format("[Call #%d] CALL END: %s completed successfully in %d ms (total connection attempts: %d)",
                callId, path, totalDurationMs, connectionAttemptCount));
    }

    @Override
    public void callFailed(Call call, IOException ioe) {
        long totalDurationMs = System.currentTimeMillis() - callStartTimeMs;
        String path = call.request().url().encodedPath();
        boolean isCanceled = call.isCanceled();
        Log.e(TAG, String.format("[Call #%d] CALL FAILED: %s failed after %d ms. Canceled=%b, Error: %s",
                callId, path, totalDurationMs, isCanceled, ioe.getMessage()));
    }
}
