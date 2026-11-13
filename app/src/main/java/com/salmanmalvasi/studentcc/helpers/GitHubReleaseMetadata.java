package com.salmanmalvasi.studentcc.helpers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.Locale;

/** Keeps a release's description and APK tied to the same immutable GitHub tag. */
public final class GitHubReleaseMetadata {
    private GitHubReleaseMetadata() {}

    public static JSONObject apkAsset(JSONObject release) throws JSONException {
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            throw new JSONException("Only published stable releases can update the app");
        }
        JSONArray assets = release.optJSONArray("assets");
        JSONObject onlyApk = null;
        int apkCount = 0;
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null || !asset.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
            if (!isTrustedApkUrl(asset.optString("browser_download_url"))) continue;
            if ("StudentCC.apk".equalsIgnoreCase(asset.optString("name"))) return asset;
            onlyApk = asset;
            apkCount++;
        }
        if (apkCount == 1) return onlyApk;
        throw new JSONException("Release needs an unambiguous StudentCC APK");
    }

    public static String aboutAssetUrl(JSONObject release) {
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && "about.json".equals(asset.optString("name"))) {
                String url = asset.optString("browser_download_url");
                if (trustedAssetUrl(url, ".json")) return url;
            }
        }
        return "";
    }

    public static JSONObject fromRelease(JSONObject release, JSONObject about) throws JSONException {
        JSONObject apk = apkAsset(release);
        String tag = release.optString("tag_name").trim();
        if (tag.isEmpty()) throw new JSONException("Release tag is missing");
        JSONObject result = about == null ? new JSONObject() : new JSONObject(about.toString());
        if (result.has("tagName") && !tag.equals(result.optString("tagName"))) {
            throw new JSONException("Release metadata belongs to a different tag");
        }
        result.put("tagName", tag);
        result.put("versionName", result.optString("versionName", tag.replaceFirst("^[vV]", "")));
        result.put("downloadUrl", apk.getString("browser_download_url"));
        String notes = release.optString("body").trim();
        if (!notes.isEmpty()) result.put("releaseNotes", notes);
        else if (!result.has("releaseNotes")) result.put("releaseNotes", "StudentCC " + tag);
        String digest = apk.optString("digest");
        if (digest.matches("sha256:[a-fA-F0-9]{64}")) result.put("sha256", digest.substring(7));
        return result;
    }

    public static boolean isNewer(JSONObject about, int installedCode, String installedVersion) {
        if (!isTrustedApkUrl(about.optString("downloadUrl"))) return false;
        int code = about.optInt("versionCode", 0);
        if (code > 0) return code > installedCode;
        // Manually uploaded APKs can omit about.json. Check their stable numeric tag,
        // then verify the actual APK version before handing it to Android's installer.
        String version = about.optString("versionName", about.optString("tagName"));
        String[] remote = parts(version), local = parts(installedVersion);
        if (remote == null || local == null) return false;
        for (int i = 0; i < Math.max(remote.length, local.length); i++) {
            long a = i < remote.length ? Long.parseLong(remote[i]) : 0;
            long b = i < local.length ? Long.parseLong(local[i]) : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    private static String[] parts(String version) {
        String numeric = version.replaceFirst("^[vV]", "").trim();
        if (!numeric.matches("[0-9]{1,9}(\\.[0-9]{1,9}){1,3}")) return null;
        return numeric.split("\\.");
    }

    public static boolean isTrustedApkUrl(String url) { return trustedAssetUrl(url, ".apk"); }

    private static boolean trustedAssetUrl(String url, String extension) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1 && uri.getQuery() == null && uri.getFragment() == null
                    && (path.startsWith("/salmanmalvasi/studentcc/releases/download/")
                    || path.startsWith("/salmanmalvasi/studentcc/releases/latest/download/"))
                    && path.endsWith(extension) && !path.contains("/../");
        } catch (Exception ignored) { return false; }
    }
}
