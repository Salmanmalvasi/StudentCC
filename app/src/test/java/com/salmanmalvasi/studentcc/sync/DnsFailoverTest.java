package com.salmanmalvasi.studentcc.sync;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Unit regression tests verifying:
 * 1. Multi-IP DNS resolution for vtopcc5.vit.ac.in.
 * 2. That retryOnConnectionFailure(true) with bounded timeout successfully fails over to the reachable IP.
 * 3. That VTOPAdaptiveDns prioritizes verified reachable IPs without modifying hostname/TLS SNI.
 */
public class DnsFailoverTest {

    @Test
    public void testDnsResolutionReturnsMultipleAddresses() throws Exception {
        List<InetAddress> addresses = Dns.SYSTEM.lookup("vtopcc5.vit.ac.in");
        Assert.assertNotNull(addresses);
        Assert.assertTrue("vtopcc5.vit.ac.in should resolve to multiple addresses", addresses.size() >= 2);

        boolean found122 = false;
        boolean found118 = false;
        for (InetAddress addr : addresses) {
            String ip = addr.getHostAddress();
            if ("122.187.117.188".equals(ip)) found122 = true;
            if ("118.95.161.165".equals(ip)) found118 = true;
        }
        Assert.assertTrue("Expected 122.187.117.188 in resolved addresses", found122);
        Assert.assertTrue("Expected 118.95.161.165 in resolved addresses", found118);
    }

    @Test
    public void testAdaptiveDnsPrioritization() throws Exception {
        VTOPAdaptiveDns adaptiveDns = new VTOPAdaptiveDns();
        VTOPAdaptiveDns.clearPreferredIp();

        // Before recording, standard resolution returns system order
        List<InetAddress> initial = adaptiveDns.lookup("vtopcc5.vit.ac.in");
        Assert.assertNotNull(initial);

        // Record 118.95.161.165 as verified working IP
        VTOPAdaptiveDns.recordSuccessfulIp("118.95.161.165");
        Assert.assertEquals("118.95.161.165", VTOPAdaptiveDns.getPreferredIp());

        // Now lookup should have 118.95.161.165 as the very first entry
        List<InetAddress> reordered = adaptiveDns.lookup("vtopcc5.vit.ac.in");
        Assert.assertNotNull(reordered);
        Assert.assertEquals("Preferred IP must be at index 0", "118.95.161.165", reordered.get(0).getHostAddress());

        // Other IPs must still be retained as fallbacks
        Assert.assertEquals("All resolved IPs must be preserved", initial.size(), reordered.size());
    }

    @Test
    public void testFailoverWhenFirstIpTimesOut() throws Exception {
        // Mock DNS providing 122.187.117.188 first (unreachable on some networks), then 118.95.161.165
        Dns mockDns = hostname -> {
            if ("vtopcc5.vit.ac.in".equalsIgnoreCase(hostname)) {
                return Arrays.asList(
                        InetAddress.getByName("122.187.117.188"),
                        InetAddress.getByName("118.95.161.165")
                );
            }
            return Dns.SYSTEM.lookup(hostname);
        };

        AtomicInteger connectAttempts = new AtomicInteger(0);
        AtomicBoolean triedSecondIp = new AtomicBoolean(false);

        EventListener failoverListener = new EventListener() {
            @Override
            public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
                int attempt = connectAttempts.incrementAndGet();
                String ip = inetSocketAddress.getAddress().getHostAddress();
                if (attempt == 2 && "118.95.161.165".equals(ip)) {
                    triedSecondIp.set(true);
                }
            }
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .dns(mockDns)
                .connectTimeout(3500, TimeUnit.MILLISECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .eventListener(failoverListener)
                .build();

        Request request = new Request.Builder()
                .url("https://vtopcc5.vit.ac.in/")
                .header("User-Agent", "Mozilla/5.0")
                .header("S-API-KEY", "S@fe@VITCC123$#%")
                .build();

        long start = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            long duration = System.currentTimeMillis() - start;
            System.out.println("Failover test response code: " + response.code() + " in " + duration + " ms");
            Assert.assertTrue("Response code should be 401 or 200", response.code() == 401 || response.code() == 200);
            Assert.assertTrue("Expected failover to attempt the second IP", triedSecondIp.get());
            Assert.assertTrue("Expected at least 2 connection attempts", connectAttempts.get() >= 2);
        }
    }
}
