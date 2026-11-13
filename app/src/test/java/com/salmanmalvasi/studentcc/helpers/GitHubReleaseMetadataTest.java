package com.salmanmalvasi.studentcc.helpers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class GitHubReleaseMetadataTest {
    private static final String APK = "https://github.com/Salmanmalvasi/StudentCC/releases/download/v1.7.0/StudentCC.apk";

    private JSONObject asset(String name, String url) throws Exception {
        return new JSONObject().put("name", name).put("browser_download_url", url);
    }

    private JSONObject release() throws Exception {
        return new JSONObject().put("tag_name", "v1.7.0").put("body", "Fixed class statistics")
                .put("assets", new JSONArray().put(asset("StudentCC.apk", APK)));
    }

    @Test public void usesSameReleaseApkAndActualNotesInsteadOfMovingLatestUrl() throws Exception {
        JSONObject about = new JSONObject().put("versionCode", 13).put("tagName", "v1.7.0")
                .put("downloadUrl", "https://github.com/Salmanmalvasi/StudentCC/releases/latest/download/StudentCC.apk")
                .put("releaseNotes", "Placeholder");
        JSONObject result = GitHubReleaseMetadata.fromRelease(release(), about);
        assertEquals(APK, result.getString("downloadUrl"));
        assertEquals("Fixed class statistics", result.getString("releaseNotes"));
        assertTrue(GitHubReleaseMetadata.isNewer(result, 12, "1.6.0"));
    }

    @Test public void manualReleaseWithoutAboutUsesNumericTag() throws Exception {
        JSONObject result = GitHubReleaseMetadata.fromRelease(release(), null);
        assertEquals("1.7.0", result.getString("versionName"));
        assertTrue(GitHubReleaseMetadata.isNewer(result, 12, "1.6.0"));
        assertFalse(GitHubReleaseMetadata.isNewer(result, 13, "1.7.0"));
        assertFalse(GitHubReleaseMetadata.isNewer(result, 14, "1.8.0"));
    }

    @Test public void comparesNumericComponentsInsteadOfLexicographicStrings() throws Exception {
        JSONObject result = GitHubReleaseMetadata.fromRelease(release().put("tag_name", "v1.10.0"), null);
        assertTrue(GitHubReleaseMetadata.isNewer(result, 15, "1.9.9"));
        assertFalse(GitHubReleaseMetadata.isNewer(result.put("versionName", "1.7.0-beta"), 12, "1.6.0"));
    }

    @Test public void versionCodePreventsSameOrOlderApkEvenWithHigherDisplayVersion() throws Exception {
        JSONObject result = GitHubReleaseMetadata.fromRelease(release(), new JSONObject().put("versionCode", 12));
        assertFalse(GitHubReleaseMetadata.isNewer(result, 12, "1.6.0"));
        assertFalse(GitHubReleaseMetadata.isNewer(result.put("versionCode", 11), 12, "1.6.0"));
    }

    @Test(expected = JSONException.class) public void mismatchedMetadataTagIsRejected() throws Exception {
        GitHubReleaseMetadata.fromRelease(release(), new JSONObject().put("tagName", "v1.6.0"));
    }

    @Test(expected = JSONException.class) public void prereleaseCannotUpdateStableInstall() throws Exception {
        GitHubReleaseMetadata.fromRelease(release().put("prerelease", true), null);
    }

    @Test(expected = JSONException.class) public void draftCannotUpdateStableInstall() throws Exception {
        GitHubReleaseMetadata.fromRelease(release().put("draft", true), null);
    }

    @Test public void supportsOneRenamedApkOnManualRelease() throws Exception {
        JSONObject result = GitHubReleaseMetadata.fromRelease(release().put("assets", new JSONArray()
                .put(asset("StudentCC-1.7.apk", APK.replace("StudentCC.apk", "StudentCC-1.7.apk")))), null);
        assertTrue(result.getString("downloadUrl").endsWith("StudentCC-1.7.apk"));
    }

    @Test(expected = JSONException.class) public void refusesAmbiguousManualApks() throws Exception {
        GitHubReleaseMetadata.fromRelease(release().put("assets", new JSONArray()
                .put(asset("one.apk", APK.replace("StudentCC.apk", "one.apk")))
                .put(asset("two.apk", APK.replace("StudentCC.apk", "two.apk")))), null);
    }

    @Test public void prefersNamedStudentccApkWhenMultipleAssetsExist() throws Exception {
        JSONObject source = release();
        source.getJSONArray("assets").put(asset("debug.apk", APK.replace("StudentCC.apk", "debug.apk")));
        assertEquals(APK, GitHubReleaseMetadata.fromRelease(source, null).getString("downloadUrl"));
    }

    @Test public void rejectsUntrustedUpdateLocations() {
        assertTrue(GitHubReleaseMetadata.isTrustedApkUrl(APK));
        assertFalse(GitHubReleaseMetadata.isTrustedApkUrl(APK.replace("https:", "http:")));
        assertFalse(GitHubReleaseMetadata.isTrustedApkUrl(APK.replace("github.com", "github.com.example.org")));
        assertFalse(GitHubReleaseMetadata.isTrustedApkUrl(APK.replace("/StudentCC/", "/OtherApp/")));
        assertFalse(GitHubReleaseMetadata.isTrustedApkUrl(APK + "?redirect=example.org"));
        assertFalse(GitHubReleaseMetadata.isTrustedApkUrl("https://github.com"));
        assertFalse(GitHubReleaseMetadata.isTrustedApkUrl(APK.replace("/v1.7.0/", "/v1.7.0/../")));
    }

    @Test public void readsMetadataAndDigestFromExactReleaseAssets() throws Exception {
        JSONObject source = release();
        String metadata = APK.replace("StudentCC.apk", "about.json");
        source.getJSONArray("assets").put(asset("about.json", metadata));
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        source.getJSONArray("assets").getJSONObject(0).put("digest", "sha256:" + hash);
        assertEquals(metadata, GitHubReleaseMetadata.aboutAssetUrl(source));
        assertEquals(hash, GitHubReleaseMetadata.fromRelease(source, null).getString("sha256"));
    }
}
