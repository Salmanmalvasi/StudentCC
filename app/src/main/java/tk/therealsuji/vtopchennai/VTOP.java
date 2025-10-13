package tk.therealsuji.vtopchennai;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

import tk.therealsuji.vtopchennai.helpers.FirebaseHelper;
import tk.therealsuji.vtopchennai.helpers.FirebaseCrashlyticsHelper;
import tk.therealsuji.vtopchennai.helpers.FirebaseConfigHelper;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;

public class VTOP extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase services
        FirebaseHelper.getInstance().initializeFirebase(this);

        // Initialize Firebase Crashlytics
        FirebaseCrashlyticsHelper.initialize(this);

        // Initialize Firebase Remote Config
        FirebaseConfigHelper.initialize(this);

        // Log that Firebase In-App Messaging is ready
        android.util.Log.d("VTOP", "Firebase In-App Messaging initialized and ready");

        // Disable dynamic colors to use our custom black and white theme
        // DynamicColors.applyToActivitiesIfAvailable(this);

        int theme = SettingsRepository.getTheme(this);
        if (theme == SettingsRepository.THEME_DAY) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (theme == SettingsRepository.THEME_NIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
