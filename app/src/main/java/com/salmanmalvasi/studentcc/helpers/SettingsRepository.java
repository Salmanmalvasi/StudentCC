package com.salmanmalvasi.studentcc.helpers;

import static android.content.Context.DOWNLOAD_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Html;
import android.text.format.DateFormat;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import com.salmanmalvasi.studentcc.BuildConfig;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.activities.WebViewActivity;
import com.salmanmalvasi.studentcc.fragments.RecyclerViewFragment;
import com.salmanmalvasi.studentcc.fragments.ViewPagerFragment;
import com.salmanmalvasi.studentcc.models.Exam;
import com.salmanmalvasi.studentcc.models.Timetable;
import com.salmanmalvasi.studentcc.receivers.ExamNotificationReceiver;
import com.salmanmalvasi.studentcc.receivers.TimetableNotificationReceiver;

public class SettingsRepository {
    public static final String APP_BASE_URL = "https://salmanmalvasi.github.io";
    public static final String APP_ABOUT_URL = APP_BASE_URL + "/studentcc-landing.html";
    public static final String APP_PRIVACY_URL = "https://salmanmalvasi.github.io/privacy-policy.html";

    public static final String DEVELOPER_BASE_URL = "https://github.com/Salmanmalvasi";

    public static final String GITHUB_BASE_URL = "https://github.com/Salmanmalvasi/StudentCC";
    public static final String GITHUB_FEATURE_URL = GITHUB_BASE_URL + "/issues";
    public static final String GITHUB_ISSUE_URL = GITHUB_BASE_URL + "/issues";
    public static final String ABOUT_JSON_URL = GITHUB_BASE_URL + "/releases/latest/download/about.json";
    public static final String DEFAULT_APK_URL = GITHUB_BASE_URL + "/releases/latest/download/StudentCC.apk";
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    public static final String VTOP_BASE_URL = "https://vtopcc.vit.ac.in/vtop";
    public static final String VTOP_API_BASE_URL = "https://vtopcc5.vit.ac.in";
    public static final String VTOP_API_KEY = "S@fe@VITCC123$#%";
    public static final String HEADER_API_KEY = "S-API-KEY";

    public static final int THEME_DAY = 1;
    public static final int THEME_NIGHT = 2;
    public static final int THEME_SYSTEM_DAY = 3;
    public static final int THEME_SYSTEM_NIGHT = 4;

    public static final int NOTIFICATION_ID_EXAMS = 1;
    public static final int NOTIFICATION_ID_TIMETABLE = 2;
    public static final int NOTIFICATION_ID_VTOP_DOWNLOAD = 3;

    public static int getTheme(Context context) {
        String appearance = getSharedPreferences(context).getString("appearance", "system");

        if (appearance.equals("dark")) {
            return THEME_NIGHT;
        } else if (appearance.equals("light")) {
            return THEME_DAY;
        }

        return getSystemTheme(context);
    }

    public static int getSystemTheme(Context context) {
        int currentNightMode = context.getApplicationContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            return THEME_SYSTEM_NIGHT;
        } else {
            return THEME_SYSTEM_DAY;
        }
    }

    public static void applyDynamicColors(Activity activity, boolean amoledMode) {
        DynamicColorsOptions.Builder dynamicColorsOptions = new DynamicColorsOptions.Builder();
        if (amoledMode) dynamicColorsOptions.setThemeOverlay(R.style.ThemeOverlay_VTOP_Amoled);
        DynamicColors.applyToActivityIfAvailable(activity, dynamicColorsOptions.build());
    }

    public static float getCGPA(Context context) {
        return getSharedPreferences(context).getFloat("cgpa", 0);
    }

    public static boolean isRefreshRequired(Context context) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, -7);

        Date now = c.getTime();
        Date lastRefreshed = new Date(getSharedPreferences(context).getLong("lastRefreshed", 0));

        return !lastRefreshed.after(now);
    }

    public static boolean isSignedIn(Context context) {
        return getSharedPreferences(context).getBoolean("isVTOPSignedIn", false);
    }

    public static final String PREF_KEY_NATIVE_SYNC_ENABLED = "developer_native_sync_engine_enabled";

    public static boolean isNativeSyncEngineEnabled(Context context) {
        if (context == null) return false;
        return getSharedPreferences(context).getBoolean(PREF_KEY_NATIVE_SYNC_ENABLED, false);
    }

    public static void setNativeSyncEngineEnabled(Context context, boolean enabled) {
        if (context == null) return;
        getSharedPreferences(context).edit().putBoolean(PREF_KEY_NATIVE_SYNC_ENABLED, enabled).apply();
    }

    public static void signOut(Context context) {
        AppDatabase.deleteDatabase(context);
        getSharedPreferences(context).edit().clear().apply();

        SharedPreferences encryptedSharedPreferences = getEncryptedSharedPreferences(context);

        if (encryptedSharedPreferences != null) {
            encryptedSharedPreferences.edit().clear().apply();
        }
    }

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences("com.salmanmalvasi.studentcc", Context.MODE_PRIVATE);
    }

    public static SharedPreferences getEncryptedSharedPreferences(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    "credentials",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static String getAuthorizedUsername(Context context) {
        if (context == null) return "";
        try {
            SharedPreferences enc = getEncryptedSharedPreferences(context);
            if (enc != null) {
                String u = enc.getString("username", "");
                if (u != null && !u.trim().isEmpty()) {
                    String clean = u.trim().toUpperCase(Locale.US);
                    getSharedPreferences(context).edit().putString("username", clean).apply();
                    return clean;
                }
            }
        } catch (Exception ignored) {}

        try {
            String u = getSharedPreferences(context).getString("username", "");
            return u != null ? u.trim().toUpperCase(Locale.US) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    public static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasFileReadPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }

        return hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static boolean hasFileWritePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }

        return hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean hasNotificationPermission(Context context) {
        // On android version 32 and below, notifications are always allowed
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            return true;
        }
        return hasPermission(context, Manifest.permission.POST_NOTIFICATIONS);
    }

    public static boolean isNotificationPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return hasPermission(context, Manifest.permission.POST_NOTIFICATIONS);
        }
        return true; // For Android 12 and below, notifications are enabled by default
    }

    public static Observable<JSONObject> fetchAboutJson(boolean useVersion) {
        return Observable.fromCallable(() -> {
                    try {
                        JSONObject about = downloadJson(ABOUT_JSON_URL);
                        cacheAboutJson(about);
                        return about;
                    } catch (Exception ignored) {
                        return new JSONObject();
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private static JSONObject downloadJson(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setInstanceFollowRedirects(true);

        try (InputStream inputStream = connection.getInputStream();
             InputStreamReader reader = new InputStreamReader(inputStream)) {
            StringBuilder response = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                response.append(buffer, 0, read);
            }

            JSONObject json = new JSONObject(response.toString());
            if (!json.has("authorisedUserAgent")) {
                json.put("authorisedUserAgent", DEFAULT_USER_AGENT);
            }
            if (!json.has("downloadUrl")) {
                json.put("downloadUrl", DEFAULT_APK_URL);
            }
            return json;
        } finally {
            connection.disconnect();
        }
    }

    private static void cacheAboutJson(JSONObject about) {
        Context context = AppContextProvider.getContext();
        if (context == null) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(context);
        prefs.edit()
                .putString("about_download_url", about.optString("downloadUrl", DEFAULT_APK_URL))
                .putString("about_authorised_user_agent", about.optString("authorisedUserAgent", DEFAULT_USER_AGENT))
                .apply();
    }

    public static void openDownloadPage(Context context) {
        SharedPreferences prefs = getSharedPreferences(context);
        String downloadUrl = prefs.getString("about_download_url", DEFAULT_APK_URL);
        downloadApkUpdate(context, downloadUrl);
    }

    public static void downloadApkUpdate(Context context, String url) {
        Toast.makeText(context, context.getString(R.string.downloading_update), Toast.LENGTH_SHORT).show();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(context.getString(R.string.app_name));
        request.setDescription(context.getString(R.string.downloading_update));
        request.setMimeType("application/vnd.android.package-archive");
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE | DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "StudentCC-update.apk");

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            downloadManager.enqueue(request);
        }
    }

    public static void openRecyclerViewFragment(FragmentActivity fragmentActivity, int titleId, int contentType) {
        RecyclerViewFragment recyclerViewFragment = new RecyclerViewFragment();
        Bundle bundle = new Bundle();

        bundle.putInt("title_id", titleId);
        bundle.putInt("content_type", contentType);

        recyclerViewFragment.setArguments(bundle);

        fragmentActivity.getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, 0, 0, R.anim.slide_out_right)
                .add(android.R.id.content, recyclerViewFragment)
                .addToBackStack(null)
                .commit();
    }

    public static void openViewPagerFragment(FragmentActivity fragmentActivity, int titleId, int contentType) {
        ViewPagerFragment viewPagerFragment = new ViewPagerFragment();
        Bundle bundle = new Bundle();

        bundle.putInt("title_id", titleId);
        bundle.putInt("content_type", contentType);

        viewPagerFragment.setArguments(bundle);

        fragmentActivity.getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, 0, 0, R.anim.slide_out_right)
                .add(android.R.id.content, viewPagerFragment)
                .addToBackStack(null)
                .commit();
    }

    public static void openWebViewActivity(Context context, String title, String url) {
        Intent intent = new Intent(context, WebViewActivity.class)
                .putExtra("url", url)
                .putExtra("title", title);
        context.startActivity(intent);
    }

    public static void openBrowser(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(intent);
    }

    public static void downloadFile(Context context, String filePath, String fileName, String mimetype, Uri uri, String cookie) {
        Toast.makeText(context, Html.fromHtml(context.getString(R.string.downloading_file, fileName), Html.FROM_HTML_MODE_LEGACY), Toast.LENGTH_SHORT).show();

        DownloadManager.Request request = new DownloadManager.Request(uri);
        String encodedFilePath = Uri.encode("StudentCC/" + filePath + "/" + fileName);
        request.addRequestHeader("cookie", cookie);
        request.allowScanningByMediaScanner();
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, encodedFilePath);
        request.setMimeType(mimetype);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE | DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle(fileName);

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);
        downloadManager.enqueue(request);
    }

    public static String getSystemFormattedTime(Context context, String time) throws ParseException {
        if (DateFormat.is24HourFormat(context)) {
            return time;
        } else {
            SimpleDateFormat hour12 = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
            SimpleDateFormat hour24 = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

            return hour12.format(Objects.requireNonNull(hour24.parse(time)));
        }
    }

    public static Bitmap getBitmapFromVectorDrawable(Drawable drawable) {
        if (drawable == null) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    public static void clearNotificationPendingIntents(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        Intent notificationIntent = new Intent(context, TimetableNotificationReceiver.class);

        int alarmCount = sharedPreferences.getInt("alarmCount", 0);
        while (alarmCount >= 0) {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, --alarmCount, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
            alarmManager.cancel(pendingIntent);
        }

        sharedPreferences.edit().remove("alarmCount").apply();
    }

    public static void setTimetableNotifications(Context context, Timetable timetable) throws ParseException {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Calendar calendar = Calendar.getInstance();
        Intent notificationIntent = new Intent(context, TimetableNotificationReceiver.class);
        SharedPreferences sharedPreferences = getSharedPreferences(context);

        SimpleDateFormat hour24 = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH);

        int alarmCount = sharedPreferences.getInt("alarmCount", 0);
        int day = calendar.get(Calendar.DAY_OF_WEEK) - 1;

        Integer[] slots = {
                timetable.sunday,
                timetable.monday,
                timetable.tuesday,
                timetable.wednesday,
                timetable.thursday,
                timetable.friday,
                timetable.saturday
        };

        Date today = dateFormat.parse(dateFormat.format(calendar.getTime()));
        Date now = hour24.parse(hour24.format(calendar.getTime()));

        for (int i = 0; i < slots.length; ++i) {
            if (slots[i] == null) {
                continue;
            }

            assert today != null;
            Calendar alarm = Calendar.getInstance();
            alarm.setTime(today);

            if (i == day) {
                Date startTime = hour24.parse(timetable.startTime);
                assert startTime != null;

                if (startTime.before(now)) {
                    alarm.add(Calendar.DATE, 7);
                }
            } else if (i > day) {
                alarm.add(Calendar.DATE, i - day);
            } else {
                alarm.add(Calendar.DATE, 7 - day + i);
            }

            alarm.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timetable.startTime.split(":")[0]));
            alarm.set(Calendar.MINUTE, Integer.parseInt(timetable.startTime.split(":")[1]));
            alarm.set(Calendar.SECOND, 0);
            alarm.set(Calendar.MILLISECOND, 0);

            // Schedule exact alarms for the next 8 occurrences (8 weeks) of each class slot.
            // Using setExactAndAllowWhileIdle because setRepeating is inexact on Android 6+ and
            // can be deferred by hours due to Doze mode — which is why class notifications weren't firing.
            for (int week = 0; week < 8; week++) {
                if (alarm.getTimeInMillis() > System.currentTimeMillis()) {
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, alarmCount++,
                            notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.getTimeInMillis(), pendingIntent);
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarm.getTimeInMillis(), pendingIntent);
                    }

                    // Also schedule the 30-min early reminder
                    Calendar earlyAlarm = (Calendar) alarm.clone();
                    earlyAlarm.add(Calendar.MINUTE, -30);
                    if (earlyAlarm.getTimeInMillis() > System.currentTimeMillis()) {
                        PendingIntent earlyPendingIntent = PendingIntent.getBroadcast(context, alarmCount++,
                                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, earlyAlarm.getTimeInMillis(), earlyPendingIntent);
                        } else {
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP, earlyAlarm.getTimeInMillis(), earlyPendingIntent);
                        }
                    }
                }
                alarm.add(Calendar.DATE, 7); // advance to same slot next week
            }
        }

        sharedPreferences.edit().putInt("alarmCount", alarmCount).apply();
    }

    public static void setExamNotifications(Context context, List<Exam> exams) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Calendar calendar = Calendar.getInstance();
        Intent notificationIntent = new Intent(context, ExamNotificationReceiver.class);
        SharedPreferences sharedPreferences = getSharedPreferences(context);

        int alarmCount = sharedPreferences.getInt("alarmCount", 0);

        Date now = calendar.getTime();

        for (int i = 0; i < exams.size(); ++i) {
            Exam exam = exams.get(i);

            if (exam.startTime == null || now.after(new Date(exam.startTime))) {
                continue;
            }

            Calendar alarm = Calendar.getInstance();
            alarm.setTime(new Date(exam.startTime));
            alarm.add(Calendar.MINUTE, -30);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, alarmCount++, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarm.getTimeInMillis(), pendingIntent);
        }

        sharedPreferences.edit().putInt("alarmCount", alarmCount).apply();
    }

    /**
     * WARNING: This function is to be used ONLY for the purpose of testing and not in production.
     * This function returns a naive OkHttpClient that accepts any and all certificates,
     * and hence can lead to attacks.
     */
    @Deprecated
    @SuppressLint({"BadHostnameVerifier", "CustomX509TrustManager", "TrustAllX509TrustManager"})
    public static OkHttpClient getNaiveOkHttpClient() {
        try {
            TrustManager[] trustAllCertificates = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] x509Certificates, String s) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] x509Certificates, String s) {
                        }

                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCertificates, new SecureRandom());

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCertificates[0])
                    .hostnameVerifier((s, sslSession) -> true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
