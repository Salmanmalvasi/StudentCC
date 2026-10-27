package com.salmanmalvasi.studentcc.sync;

import android.util.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Dns;

/**
 * Intelligent, adaptive DNS provider for VTOP API endpoints.
 *
 * Automatically prioritizes verified reachable IP addresses for multi-homed VTOP hosts
 * (e.g. vtopcc5.vit.ac.in resolving to [122.187.117.188, 118.95.161.165]).
 *
 * Guarantees:
 * 1. Delegates to Dns.SYSTEM to strictly respect system DNS, caching TTLs, and dynamic network changes.
 * 2. Never hardcodes static IPs.
 * 3. Preserves original hostname for TLS SNI and server certificate verification (*.vit.ac.in).
 * 4. Reorders returned addresses so the known-working IP is attempted first, eliminating connection timeouts.
 * 5. Retains all addresses in the list so OkHttp's retryOnConnectionFailure can still fail over if needed.
 */
public class VTOPAdaptiveDns implements Dns {
    private static final String TAG = "VTOPAdaptiveDns";

    // Holds the IP address string of the most recent verified successful connection
    private static final AtomicReference<String> lastKnownWorkingIp = new AtomicReference<>(null);

    /**
     * Records an IP address that successfully completed a TCP & TLS handshake.
     * Future DNS lookups will prioritize this IP at the front of the route list.
     */
    public static void recordSuccessfulIp(String hostAddress) {
        if (hostAddress != null && !hostAddress.equals(lastKnownWorkingIp.get())) {
            lastKnownWorkingIp.set(hostAddress);
            Log.d(TAG, "Recorded verified reachable IP for VTOP: " + hostAddress);
        }
    }

    /**
     * Resets the preferred IP (e.g., when switching network interfaces).
     */
    public static void clearPreferredIp() {
        lastKnownWorkingIp.set(null);
    }

    /**
     * Returns the currently preferred IP, or null if none recorded.
     */
    public static String getPreferredIp() {
        return lastKnownWorkingIp.get();
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> systemAddresses = Dns.SYSTEM.lookup(hostname);
        if (systemAddresses == null || systemAddresses.size() <= 1) {
            return systemAddresses;
        }

        String preferredIp = lastKnownWorkingIp.get();
        if (preferredIp == null) {
            return systemAddresses;
        }

        // Search for the preferred address in the resolved list
        InetAddress preferredAddr = null;
        List<InetAddress> remaining = new ArrayList<>(systemAddresses.size());

        for (InetAddress addr : systemAddresses) {
            if (preferredAddr == null && preferredIp.equals(addr.getHostAddress())) {
                preferredAddr = addr;
            } else {
                remaining.add(addr);
            }
        }

        if (preferredAddr != null) {
            List<InetAddress> prioritized = new ArrayList<>(systemAddresses.size());
            prioritized.add(preferredAddr);
            prioritized.addAll(remaining);
            Log.d(TAG, "Prioritized preferred IP " + preferredIp + " ahead of " + remaining.size() + " alternate route(s) for " + hostname);
            return prioritized;
        }

        return systemAddresses;
    }
}
