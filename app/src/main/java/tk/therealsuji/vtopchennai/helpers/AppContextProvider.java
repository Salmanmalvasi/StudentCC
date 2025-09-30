package tk.therealsuji.vtopchennai.helpers;

import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Main Application class that provides context and initializes Firebase
 * Provides application context for static access
 */
public class AppContextProvider extends Application {
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();

        // Firebase initialization removed for testing - can be re-added later

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

        // Clean up old in-app messages periodically
        InAppMessageManager.getInstance().clearOldMessages();
    }

    public static Context getContext() {
        return context;
    }
}
