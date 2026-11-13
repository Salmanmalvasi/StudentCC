package com.salmanmalvasi.studentcc.helpers;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.salmanmalvasi.studentcc.BuildConfig;
import com.salmanmalvasi.studentcc.R;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Downloads an update once and resumes its installer handoff when the app is visible. */
public final class ApkUpdateManager {
    private static final String ID = "apk_update_download_id";
    private static final String URL = "apk_update_download_url";
    private static final String PATH = "apk_update_download_path";
    private static final String HASH = "apk_update_sha256";
    private static final String LAUNCHED = "apk_update_installer_launched";
    private static final String PERMISSION = "apk_update_permission_pending";
    private static final AtomicBoolean checking = new AtomicBoolean();

    private ApkUpdateManager() {}

    public static BroadcastReceiver receiver(Activity activity) {
        return new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                long id = SettingsRepository.getSharedPreferences(context).getLong(ID, -1);
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())
                        && id > 0 && id == intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -2)) {
                    resume(activity, false);
                }
            }
        };
    }

    public static synchronized void download(Context context, String url, String hash) {
        if (!GitHubReleaseMetadata.isTrustedApkUrl(url)) {
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show();
            return;
        }
        SharedPreferences prefs = SettingsRepository.getSharedPreferences(context);
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show();
            return;
        }
        if (prefs.getLong(ID, -1) > 0 && url.equals(prefs.getString(URL, ""))) {
            if (context instanceof Activity) resume((Activity) context, true);
            Toast.makeText(context, R.string.downloading_update, Toast.LENGTH_SHORT).show();
            return;
        }
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) {
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            long oldId = prefs.getLong(ID, -1);
            if (oldId > 0) manager.remove(oldId);
            String relativePath = "updates/StudentCC-" + System.currentTimeMillis() + ".apk";
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(context.getString(R.string.app_name));
            request.setDescription(context.getString(R.string.downloading_update));
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, relativePath);
            long id = manager.enqueue(request);
            prefs.edit().putLong(ID, id).putString(URL, url)
                    .putString(PATH, new File(directory, relativePath).getAbsolutePath())
                    .putString(HASH, hash.matches("[a-fA-F0-9]{64}") ? hash : "")
                    .putBoolean(LAUNCHED, false).putBoolean(PERMISSION, false).apply();
            Toast.makeText(context, R.string.downloading_update, Toast.LENGTH_SHORT).show();
        } catch (Exception failure) {
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show();
        }
    }

    public static void resume(Activity activity, boolean userInitiated) {
        SharedPreferences prefs = SettingsRepository.getSharedPreferences(activity);
        long id = prefs.getLong(ID, -1);
        if (id <= 0 || (!userInitiated && prefs.getBoolean(LAUNCHED, false))
                || !checking.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager == null) return;
                int status;
                try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        clear(prefs);
                        return;
                    }
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                }
                if (status == DownloadManager.STATUS_FAILED) {
                    clear(prefs);
                    activity.runOnUiThread(() -> Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show());
                    return;
                }
                if (status != DownloadManager.STATUS_SUCCESSFUL) return;
                File apk = new File(prefs.getString(PATH, ""));
                PackageInfo info = activity.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                long version = info == null ? 0 : (Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode);
                if (info != null && BuildConfig.APPLICATION_ID.equals(info.packageName) && version <= BuildConfig.VERSION_CODE) {
                    manager.remove(id);
                    clear(prefs);
                    return;
                }
                if (info == null || !BuildConfig.APPLICATION_ID.equals(info.packageName)) {
                    manager.remove(id);
                    clear(prefs);
                    activity.runOnUiThread(() -> Toast.makeText(activity, R.string.update_invalid_apk, Toast.LENGTH_LONG).show());
                    return;
                }
                String hash = prefs.getString(HASH, "");
                if (!hash.isEmpty() && !hash.equalsIgnoreCase(sha256(apk))) {
                    manager.remove(id);
                    clear(prefs);
                    activity.runOnUiThread(() -> Toast.makeText(activity, R.string.update_invalid_apk, Toast.LENGTH_LONG).show());
                    return;
                }
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || prefs.getLong(ID, -1) != id) return;
                    if (!userInitiated && prefs.getBoolean(LAUNCHED, false)) return;
                    try {
                        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                            // Returning from Settings retries only when permission was granted.
                            if (prefs.getBoolean(PERMISSION, false) && !userInitiated) {
                                prefs.edit().putBoolean(PERMISSION, false).putBoolean(LAUNCHED, true).apply();
                                return;
                            }
                            prefs.edit().putBoolean(PERMISSION, true).apply();
                            Toast.makeText(activity, R.string.update_allow_install, Toast.LENGTH_LONG).show();
                            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + BuildConfig.APPLICATION_ID)));
                            return;
                        }
                        Uri uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".fileprovider", apk);
                        Intent install = new Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        activity.startActivity(install);
                        prefs.edit().putBoolean(LAUNCHED, true).putBoolean(PERMISSION, false).apply();
                    } catch (Exception failure) {
                        Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception failure) {
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show());
            } finally {
                checking.set(false);
            }
        }, "StudentCC-update-check").start();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
        return hash.toString();
    }

    private static void clear(SharedPreferences prefs) {
        prefs.edit().remove(ID).remove(URL).remove(PATH).remove(HASH).remove(LAUNCHED).remove(PERMISSION).apply();
    }
}
