package com.salmanmalvasi.studentcc.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * Thread-safe, in-memory-only CookieJar for OkHttpClient.
 * Never persists session cookies to disk or plaintext storage.
 * Enforces expiration, domain/path matching, and safe inspection without leaking secret values.
 */
public class InMemoryCookieJar implements CookieJar {
    private final List<Cookie> cookies = new CopyOnWriteArrayList<>();

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> newCookies) {
        if (newCookies == null || newCookies.isEmpty()) {
            return;
        }
        for (Cookie newCookie : newCookies) {
            // Remove existing cookie with the same name, domain, and path
            for (Cookie existing : cookies) {
                if (existing.name().equals(newCookie.name()) &&
                        existing.domain().equalsIgnoreCase(newCookie.domain()) &&
                        existing.path().equals(newCookie.path())) {
                    cookies.remove(existing);
                    break;
                }
            }
            // Retain if not expired
            if (newCookie.expiresAt() > System.currentTimeMillis()) {
                cookies.add(newCookie);
            }
        }
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        if (url == null) {
            return Collections.emptyList();
        }
        List<Cookie> validCookies = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Cookie cookie : cookies) {
            if (cookie.expiresAt() <= now) {
                cookies.remove(cookie);
            } else if (cookie.matches(url) || isHostMatch(cookie.domain(), url.host())) {
                validCookies.add(cookie);
            }
        }
        return validCookies;
    }

    /**
     * Checks whether an unexpired JSESSIONID or session cookie exists for the specified host,
     * without exposing or logging the cookie's value.
     */
    public boolean hasValidSession(String host) {
        long now = System.currentTimeMillis();
        for (Cookie cookie : cookies) {
            if (cookie.expiresAt() > now && isSessionCookie(cookie.name())) {
                if (host == null || host.isEmpty() ||
                        cookie.domain().equalsIgnoreCase(host) ||
                        host.endsWith(cookie.domain()) ||
                        cookie.domain().endsWith(host)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSessionCookie(String cookieName) {
        if (cookieName == null) return false;
        String n = cookieName.trim().toUpperCase();
        return n.equals("JSESSIONID") || n.equals("SESSION") || n.contains("SESSION");
    }

    /**
     * Returns whether any unexpired cookie exists matching the specified host.
     */
    public boolean hasAnyValidCookie(String host) {
        long now = System.currentTimeMillis();
        for (Cookie cookie : cookies) {
            if (cookie.expiresAt() > now) {
                if (host == null || host.isEmpty() ||
                        cookie.domain().equalsIgnoreCase(host) ||
                        host.endsWith(cookie.domain()) ||
                        cookie.domain().endsWith(host)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether any cookie with the given name is present and unexpired.
     */
    public boolean hasCookie(String cookieName) {
        if (cookieName == null) return false;
        long now = System.currentTimeMillis();
        for (Cookie cookie : cookies) {
            if (cookie.expiresAt() > now && cookieName.equalsIgnoreCase(cookie.name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns list of names of currently stored cookies (privacy-safe, no values).
     */
    public List<String> getCookieNames() {
        List<String> names = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Cookie cookie : cookies) {
            if (cookie.expiresAt() > now) {
                names.add(cookie.name());
            }
        }
        return names;
    }

    /**
     * Returns privacy-safe descriptors for all currently stored cookies (name, domain, path, secure, httpOnly),
     * completely excluding the secret cookie values.
     */
    public List<String> getCookieDescriptors() {
        List<String> descriptors = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Cookie cookie : cookies) {
            descriptors.add("name=" + cookie.name() +
                    "; domain=" + cookie.domain() +
                    "; path=" + cookie.path() +
                    "; secure=" + cookie.secure() +
                    "; httpOnly=" + cookie.httpOnly() +
                    "; expired=" + (cookie.expiresAt() <= now));
        }
        return descriptors;
    }

    /**
     * Returns count of currently stored valid cookies.
     */
    public int getCookieCount() {
        return cookies.size();
    }

    /**
     * Clears all in-memory cookies.
     */
    public void clear() {
        cookies.clear();
    }
    private static boolean isHostMatch(String cookieDomain, String urlHost) {
        if (cookieDomain == null || urlHost == null) return false;
        String cd = cookieDomain.toLowerCase(java.util.Locale.ROOT).replaceFirst("^\\.", "");
        String uh = urlHost.toLowerCase(java.util.Locale.ROOT);
        return uh.equals(cd) || uh.endsWith("." + cd);
    }

}
