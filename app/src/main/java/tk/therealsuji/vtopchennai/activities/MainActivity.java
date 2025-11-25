package tk.therealsuji.vtopchennai.activities;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.ImageView;
import android.view.View;
import android.app.Dialog;
import android.media.MediaPlayer;
import android.os.Handler;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.app.NotificationCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;


import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.BuildConfig;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.activities.LoginActivity;
import tk.therealsuji.vtopchennai.fragments.HomeFragment;
import tk.therealsuji.vtopchennai.helpers.FirebaseHelper;
import tk.therealsuji.vtopchennai.helpers.InAppMessagingHelper;
import tk.therealsuji.vtopchennai.helpers.HostelDataHelper;
import tk.therealsuji.vtopchennai.helpers.FirebaseAnalyticsHelper;
import tk.therealsuji.vtopchennai.fragments.PerformanceFragment;
import tk.therealsuji.vtopchennai.fragments.GPACalculatorFragment;
import tk.therealsuji.vtopchennai.fragments.HostelInfoFragment;
import tk.therealsuji.vtopchennai.fragments.ProfileFragment;
import tk.therealsuji.vtopchennai.fragments.dialogs.UpdateDialogFragment;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.helpers.FirebaseMessagingHelper;
import tk.therealsuji.vtopchennai.helpers.FirebaseCrashlyticsHelper;
import tk.therealsuji.vtopchennai.helpers.FirebaseConfigHelper;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.helpers.SpotlightDefaults;
import tk.therealsuji.vtopchennai.helpers.VTOPHelper;

import android.Manifest;

public class MainActivity extends AppCompatActivity {
    BottomNavigationView bottomNavigationView;
    CompositeDisposable compositeDisposable = new CompositeDisposable();
    VTOPHelper vtopHelper;
    private boolean classTimeMonitorStarted = false;
    private Dialog syncVideoDialog;
    private VideoView syncVideoView;
    private ImageView muteIndicator;
    private MediaPlayer mediaPlayer;
    private boolean isVideoMuted = false;
    private Handler muteIndicatorHandler = new Handler();

    static final String HOME_FRAGMENT_TAG = "HOME_FRAGMENT_TAG";
    static final String PERFORMANCE_FRAGMENT_TAG = "PERFORMANCE_FRAGMENT_TAG";
    static final String GPA_CALCULATOR_FRAGMENT_TAG = "GPA_CALCULATOR_FRAGMENT_TAG";
    static final String HOSTEL_INFO_FRAGMENT_TAG = "HOSTEL_INFO_FRAGMENT_TAG";
    static final String PROFILE_FRAGMENT_TAG = "PROFILE_FRAGMENT_TAG";

    ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, R.string.notification_permission_granted, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show();
                }
            });

    ActivityResultLauncher<Intent> requestFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    try {
                        ArrayList<String> filePaths = new ArrayList<>();

                        if (result.getData().getClipData() != null) {
                            ClipData clipData = result.getData().getClipData();
                            for (int i = 0; i < clipData.getItemCount(); ++i) {
                                Uri uri = clipData.getItemAt(i).getUri();
                                filePaths.add(this.copyFileToCache(uri));
                            }
                        } else if (result.getData().getData() != null) {
                            Uri uri = result.getData().getData();
                            filePaths.add(this.copyFileToCache(uri));
                        } else {
                            return;
                        }

                        Bundle fileUri = new Bundle();
                        fileUri.putStringArrayList("paths", filePaths);
                        getSupportFragmentManager().setFragmentResult("file", fileUri);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

    private String copyFileToCache(Uri uri) throws Exception {
        DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
        assert documentFile != null && documentFile.getName() != null;

        String fileName = documentFile.getName();
        File file = new File(getCacheDir() + "/Moodle", fileName);
        file.deleteOnExit();

        if ((file.getParentFile() == null || !file.getParentFile().mkdir()) && !file.createNewFile() && !file.exists()) {
            throw new Exception("Failed to copy one or more files.");
        }

        InputStream inputStream = getContentResolver().openInputStream(uri);
        assert inputStream != null;
        FileUtils.copyInputStreamToFile(inputStream, file);

        return file.getPath();
    }

    private void sendLaundryCountdownNotification(int days, String room) {
        final String CHANNEL_ID = "laundry_notifications";
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Laundry Notifications", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(channel);
        }
        String text;
        if (days == 0) {
            text = "Your laundry is scheduled today for room " + room;
        } else if (days > 0) {
            text = "Your laundry is in " + days + " day" + (days == 1 ? "" : "s") + " (Room " + room + ")";
        } else {
            text = "Laundry schedule info unavailable";
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(tk.therealsuji.vtopchennai.R.drawable.ic_update_available)
                .setContentTitle("Laundry Reminder")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        nm.notify(2013, builder.build());
    }

    public ActivityResultLauncher<String> getRequestPermissionLauncher() {
        return this.requestPermissionLauncher;
    }

    public ActivityResultLauncher<Intent> getRequestFileLauncher() {
        return this.requestFileLauncher;
    }

    private void syncData() {
        vtopHelper.bind();
        vtopHelper.start();
    }

    private void showSyncVideoDialog() {
        if (syncVideoDialog == null) {
            // Inflate the video dialog layout
            android.view.View dialogView = getLayoutInflater().inflate(R.layout.layout_dialog_sync_video, null);
            syncVideoView = dialogView.findViewById(R.id.video_view_sync);
            muteIndicator = dialogView.findViewById(R.id.image_view_mute_indicator);

            // Create fullscreen dialog
            syncVideoDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            syncVideoDialog.setContentView(dialogView);
            syncVideoDialog.setCancelable(false);
            
            // Get user's selected video theme from preferences
            SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(this);
            String videoTheme = sharedPreferences.getString("sync_video_theme", "marvel");
            isVideoMuted = sharedPreferences.getBoolean("sync_video_muted", false);
            
            // Determine video file based on theme
            String videoFileName;
            if (videoTheme.equals("got")) {
                videoFileName = "got.mp4";
            } else if (videoTheme.equals("f1")) {
                videoFileName = "f1.mp4";
            } else if (videoTheme.equals("matrix")) {
                videoFileName = "matrix.mp4";
            } else {
                videoFileName = "marvel.mp4";
            }
            
            // Set up video from assets folder
            try {
                // Copy video from assets to cache and play it
                java.io.File cacheFile = new java.io.File(getCacheDir(), videoFileName);
                if (!cacheFile.exists()) {
                    java.io.InputStream inputStream = getAssets().open(videoFileName);
                    java.io.FileOutputStream outputStream = new java.io.FileOutputStream(cacheFile);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                    outputStream.close();
                    inputStream.close();
                }
                syncVideoView.setVideoPath(cacheFile.getAbsolutePath());
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Error loading video from assets", e);
            }

            // Set video to loop and apply mute setting
            syncVideoView.setOnPreparedListener(mp -> {
                mediaPlayer = mp;
                mp.setLooping(true);
                
                // Set volume based on mute preference
                if (isVideoMuted) {
                    mp.setVolume(0f, 0f);
                } else {
                    mp.setVolume(1f, 1f);
                }
                
                mp.start();
            });

            // Handle errors
            syncVideoView.setOnErrorListener((mp, what, extra) -> {
                android.util.Log.e("MainActivity", "Video playback error: " + what + ", " + extra);
                return true;
            });

            // Add tap-to-mute functionality
            syncVideoView.setOnClickListener(v -> {
                if (mediaPlayer != null) {
                    isVideoMuted = !isVideoMuted;
                    
                    // Toggle volume
                    if (isVideoMuted) {
                        mediaPlayer.setVolume(0f, 0f);
                    } else {
                        mediaPlayer.setVolume(1f, 1f);
                    }
                    
                    // Save preference
                    SettingsRepository.getSharedPreferences(MainActivity.this)
                            .edit()
                            .putBoolean("sync_video_muted", isVideoMuted)
                            .apply();
                    
                    // Show mute indicator
                    showMuteIndicator();
                }
            });
        }

        if (!syncVideoDialog.isShowing()) {
            syncVideoDialog.show();
            syncVideoView.start();
        }
    }

    private void showMuteIndicator() {
        if (muteIndicator != null) {
            // Update icon based on mute state
            if (isVideoMuted) {
                muteIndicator.setImageResource(android.R.drawable.ic_lock_silent_mode);
            } else {
                muteIndicator.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            }
            
            // Show indicator
            muteIndicator.setVisibility(View.VISIBLE);
            muteIndicator.setAlpha(0f);
            muteIndicator.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
            
            // Remove any pending hide callbacks
            muteIndicatorHandler.removeCallbacksAndMessages(null);
            
            // Hide after 1.5 seconds
            muteIndicatorHandler.postDelayed(() -> {
                if (muteIndicator != null) {
                    muteIndicator.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction(() -> {
                                if (muteIndicator != null) {
                                    muteIndicator.setVisibility(View.GONE);
                                }
                            })
                            .start();
                }
            }, 1500);
        }
    }

    private void hideSyncVideoDialog() {
        if (syncVideoDialog != null && syncVideoDialog.isShowing()) {
            if (syncVideoView != null && syncVideoView.isPlaying()) {
                syncVideoView.stopPlayback();
            }
            syncVideoDialog.dismiss();
        }
    }

    private void resetSyncVideoDialog() {
        hideSyncVideoDialog();
        
        // Remove pending handler callbacks
        muteIndicatorHandler.removeCallbacksAndMessages(null);
        
        // Clean up references
        if (syncVideoDialog != null) {
            syncVideoDialog = null;
        }
        if (syncVideoView != null) {
            syncVideoView = null;
        }
        if (muteIndicator != null) {
            muteIndicator = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer = null;
        }
    }

    private void hideBottomNavigationView() {
        this.bottomNavigationView.clearAnimation();
        this.bottomNavigationView.post(() -> this.bottomNavigationView.animate().translationY(bottomNavigationView.getMeasuredHeight()));

        if (Build.VERSION.SDK_INT >= 29) {
            this.getWindow().getDecorView().post(() -> {
                int gestureLeft = this.getWindow().getDecorView().getRootWindowInsets().getSystemGestureInsets().left;

                if (gestureLeft == 0) {
                    this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                }
            });
        }
    }

    private void showBottomNavigationView() {
        this.bottomNavigationView.clearAnimation();
        this.bottomNavigationView.post(() -> this.bottomNavigationView.animate().translationY(0));

        this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
    }

    private void restartActivity() {
        Intent intent = new Intent(this, this.getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void checkAndUpdateBottomNavigation() {
        // Get student type from encrypted SharedPreferences using SettingsRepository
        SharedPreferences encryptedSharedPreferences = SettingsRepository.getEncryptedSharedPreferences(this);

        if (encryptedSharedPreferences != null) {
            String studentType = encryptedSharedPreferences.getString("student_type", "");
            android.util.Log.d("MainActivity", "Student type: '" + studentType + "' (length: " + studentType.length() + ")");

            // Check all student type related data for debugging
            String username = encryptedSharedPreferences.getString("username", "");
            String gender = encryptedSharedPreferences.getString("gender", "");
            android.util.Log.d("MainActivity", "Username: '" + username + "', Gender: '" + gender + "'");

            // Always hide hostel tab for day scholars, use contains to be safe
            if ("day_scholar".equals(studentType) || studentType.contains("day_scholar") || studentType.isEmpty()) {
                // Hide the hostel info menu item
                MenuItem hostelItem = bottomNavigationView.getMenu().findItem(R.id.item_hostel_info);
                if (hostelItem != null) {
                    hostelItem.setVisible(false);
                    android.util.Log.d("MainActivity", "Hiding hostel tab for day scholar or empty student type");
                } else {
                    android.util.Log.e("MainActivity", "Hostel menu item not found!");
                }
            } else {
                // Show the hostel info menu item for hostellers only
                MenuItem hostelItem = bottomNavigationView.getMenu().findItem(R.id.item_hostel_info);
                if (hostelItem != null) {
                    hostelItem.setVisible(true);
                    android.util.Log.d("MainActivity", "Showing hostel tab for hosteller");
                } else {
                    android.util.Log.e("MainActivity", "Hostel menu item not found!");
                }
                android.util.Log.d("MainActivity", "Equality check: " + "day_scholar".equals(studentType));
            }
        } else {
            android.util.Log.e("MainActivity", "Encrypted SharedPreferences is null - hiding hostel tab by default");
            // If we can't get the preferences, hide the hostel tab by default
            MenuItem hostelItem = bottomNavigationView.getMenu().findItem(R.id.item_hostel_info);
            if (hostelItem != null) {
                hostelItem.setVisible(false);
            }
        }
    }

    private void startClassTimeMonitorServiceIfNeeded() {
        if (classTimeMonitorStarted) {
            return;
        }

        Intent serviceIntent = new Intent(this, tk.therealsuji.vtopchennai.services.ClassTimeMonitorService.class);
        try {
            startService(serviceIntent);
            classTimeMonitorStarted = true;
        } catch (RuntimeException exception) {
            android.util.Log.w("MainActivity", "Unable to start ClassTimeMonitorService", exception);
        }
    }

    private void signOut() {
        FirebaseAnalyticsHelper.trackLogout(this);
        SettingsRepository.signOut(this);
        this.startActivity(new Intent(this, LoginActivity.class));
        this.finish();
    }

    private void applySelectedTheme() {
        SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(this);
        int selectedTheme = sharedPreferences.getInt("selectedTheme", 0);

        switch (selectedTheme) {
            case 1:
                setTheme(R.style.Theme_VTOP_Red);
                break;
            case 2:
                setTheme(R.style.Theme_VTOP_Blue);
                break;
            case 3:
                setTheme(R.style.Theme_VTOP_Purple);
                break;
            case 4:
                setTheme(R.style.Theme_VTOP_Green);
                break;
            case 5:
                setTheme(R.style.Theme_VTOP_Black);
                break;
            default:
                setTheme(R.style.Theme_VTOP);
                break;
        }
    }

    private void getUnreadCount() {
    AppDatabase appDatabase = AppDatabase.getInstance(this.getApplicationContext());
    Bundle unreadCount = new Bundle();

    SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(this);
    boolean eventsViewed = sharedPreferences.getBoolean("has_viewed_events_announcement", false);
    int defaultSpotlightUnread = eventsViewed ? 0 : SpotlightDefaults.getEventsAnnouncementsCount();

    Observable.concat(
            Observable.fromSingle(
                appDatabase.spotlightDao()
                    .getUnreadCount()
                    .map(count -> count + defaultSpotlightUnread)
                    .onErrorReturnItem(defaultSpotlightUnread)
            ),
            Observable.fromSingle(appDatabase.marksDao().getMarksUnreadCount())
        )
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onNext(@NonNull Integer count) {
                        if (!unreadCount.containsKey("spotlight")) {
                            unreadCount.putInt("spotlight", count);
                        } else if (!unreadCount.containsKey("marks")) {
                            unreadCount.putInt("marks", count);
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                        int homeCount = unreadCount.getInt("spotlight");
                        int performanceCount = unreadCount.getInt("marks");

                        // Only show badge on performance tab, not home tab (spotlight badge is handled in HomeFragment)
                        BadgeDrawable performanceBadge = bottomNavigationView.getOrCreateBadge(R.id.item_performance);

                        performanceBadge.setNumber(performanceCount);
                        performanceBadge.setVisible(performanceCount != 0);

                        // Remove any existing badge from home tab
                        bottomNavigationView.removeBadge(R.id.item_home);

                        getSupportFragmentManager().setFragmentResult("unreadCount", unreadCount);
                    }
                });
    }

    /**
     * Persist the last seen app version so future updates can react once without clearing sessions.
     */
    private void ensureVersionTracking() {
        SharedPreferences versionPrefs = getSharedPreferences("app_version_prefs", MODE_PRIVATE);
        int currentVersionCode = BuildConfig.VERSION_CODE;
        int lastVersionCode = versionPrefs.getInt("lastVersionCode", 0);

        if (lastVersionCode != currentVersionCode) {
            versionPrefs.edit().putInt("lastVersionCode", currentVersionCode).apply();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    // Record the running app version early so update-specific logic can act once per release
        ensureVersionTracking();

        boolean amoledMode = SettingsRepository.getSharedPreferences(this).getBoolean("amoledMode", false);
        // Disable dynamic colors (use custom theme)
        applySelectedTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Analytics
        FirebaseAnalyticsHelper.initialize(this);
        FirebaseAnalyticsHelper.trackScreenView(this, "MainActivity", "MainActivity");

        // Initialize Firebase Messaging
        initializeFirebaseMessaging();

        // Initialize Firebase Remote Config
        initializeFirebaseRemoteConfig();

        // Set up Crashlytics user context
        setupCrashlyticsContext();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);



        this.bottomNavigationView = findViewById(R.id.bottom_navigation);
        Menu bottomMenu = this.bottomNavigationView.getMenu();
        int maxNavigationItems = this.bottomNavigationView.getMaxItemCount();
        if (bottomMenu.size() > maxNavigationItems) {
            for (int index = bottomMenu.size() - 1; index >= maxNavigationItems; index--) {
                int itemId = bottomMenu.getItem(index).getItemId();
                bottomMenu.removeItem(itemId);
            }
            android.util.Log.w("MainActivity", "Trimmed bottom navigation items to avoid exceeding the maximum of " + maxNavigationItems);
        }

        Bundle customInsets = new Bundle();
        customInsets.putInt("systemWindowInsetLeft", 0);
        customInsets.putInt("systemWindowInsetTop", 0);
        customInsets.putInt("systemWindowInsetRight", 0);
        customInsets.putInt("systemWindowInsetBottom", 0);
        customInsets.putInt("bottomNavigationHeight", 0);

        findViewById(R.id.frame_layout_fragment_container)
                .setOnApplyWindowInsetsListener((view, windowInsets) -> {
                    int systemWindowInsetLeft = windowInsets.getSystemWindowInsetLeft();
                    int systemWindowInsetTop = windowInsets.getSystemWindowInsetTop();
                    int systemWindowInsetRight = windowInsets.getSystemWindowInsetRight();
                    int systemWindowInsetBottom = windowInsets.getSystemWindowInsetBottom();

                    customInsets.putInt("systemWindowInsetLeft", systemWindowInsetLeft);
                    customInsets.putInt("systemWindowInsetTop", systemWindowInsetTop);
                    customInsets.putInt("systemWindowInsetRight", systemWindowInsetRight);
                    customInsets.putInt("systemWindowInsetBottom", systemWindowInsetBottom);

                    getSupportFragmentManager().setFragmentResult("customInsets", customInsets);

                    // Broadcast bottom navigation height
                    bottomNavigationView.post(() -> {
                        customInsets.putInt("bottomNavigationHeight", bottomNavigationView.getMeasuredHeight());
                        getSupportFragmentManager().setFragmentResult("customInsets", customInsets);
                    });

                    return windowInsets;
                });

        getSupportFragmentManager().setFragmentResultListener("bottomNavigationVisibility", this, (requestKey, result) -> {
            if (result.getBoolean("isVisible")) {
                this.showBottomNavigationView();
            } else {
                this.hideBottomNavigationView();
            }
        });

        Bundle syncDataState = new Bundle();
        syncDataState.putBoolean("isLoading", false);
        getSupportFragmentManager().setFragmentResultListener("syncData", this, (requestKey, result) -> this.syncData());
        getSupportFragmentManager().setFragmentResultListener("getUnreadCount", this, (requestKey, result) -> this.getUnreadCount());
        // Dynamic colors listener disabled

        this.getUnreadCount();

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!SettingsRepository.hasNotificationPermission(this)) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Check student type and hide hostel info tab if day scholar
        checkAndUpdateBottomNavigation();
        
        // Test in-app messaging with a custom event (for debugging)
        testInAppMessaging();
        
        // Add device as test device for Firebase In-App Messaging
        addAsTestDevice();
        
        // Initialize and test In-App Messaging
        initializeInAppMessaging();

        this.bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment;
            String selectedFragmentTag;

            if (item.getItemId() == R.id.item_performance) {
                FirebaseAnalyticsHelper.trackTabSwitch(this, "Performance");
                selectedFragmentTag = PERFORMANCE_FRAGMENT_TAG;
                selectedFragment = getSupportFragmentManager().findFragmentByTag(selectedFragmentTag);

                if (selectedFragment == null) {
                    selectedFragment = new PerformanceFragment();
                }
            } else if (item.getItemId() == R.id.item_gpa_calculator) {
                FirebaseAnalyticsHelper.trackTabSwitch(this, "GPA Calculator");
                selectedFragmentTag = GPA_CALCULATOR_FRAGMENT_TAG;
                selectedFragment = getSupportFragmentManager().findFragmentByTag(selectedFragmentTag);

                if (selectedFragment == null) {
                    selectedFragment = new GPACalculatorFragment();
                }
            } else if (item.getItemId() == R.id.item_hostel_info) {
                FirebaseAnalyticsHelper.trackTabSwitch(this, "Hostel Info");
                // Check if user is day scholar - if so, redirect to home
                SharedPreferences encryptedSharedPreferences = getSharedPreferences("encrypted_prefs", MODE_PRIVATE);
                String studentType = encryptedSharedPreferences.getString("student_type", "");

                if ("day_scholar".equals(studentType)) {
                    // Redirect to home if day scholar tries to access hostel info
                    FirebaseAnalyticsHelper.trackCustomEvent(this, "day_scholar_hostel_access_attempt", new Bundle());
                    selectedFragmentTag = HOME_FRAGMENT_TAG;
                    selectedFragment = getSupportFragmentManager().findFragmentByTag(selectedFragmentTag);
                    if (selectedFragment == null) {
                        selectedFragment = new HomeFragment();
                    }
                    // Also update the selected item to home
                    bottomNavigationView.setSelectedItemId(R.id.item_home);
                } else {
                    selectedFragmentTag = HOSTEL_INFO_FRAGMENT_TAG;
                    selectedFragment = getSupportFragmentManager().findFragmentByTag(selectedFragmentTag);
                    if (selectedFragment == null) {
                        selectedFragment = new HostelInfoFragment();
                    }
                }
            } else if (item.getItemId() == R.id.item_profile) {
                FirebaseAnalyticsHelper.trackTabSwitch(this, "Profile");
                getSupportFragmentManager().setFragmentResult("syncDataState", syncDataState);

                selectedFragmentTag = PROFILE_FRAGMENT_TAG;
                selectedFragment = getSupportFragmentManager().findFragmentByTag(selectedFragmentTag);

                if (selectedFragment == null) {
                    selectedFragment = new ProfileFragment();
                }
            } else {
                FirebaseAnalyticsHelper.trackTabSwitch(this, "Home");
                selectedFragmentTag = HOME_FRAGMENT_TAG;
                selectedFragment = getSupportFragmentManager().findFragmentByTag(selectedFragmentTag);

                if (selectedFragment == null) {
                    selectedFragment = new HomeFragment();
                }
            }

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frame_layout_fragment_container, selectedFragment, selectedFragmentTag)
                    .commit();

            return true;
        });

        int selectedItem = R.id.item_home;
        Serializable launchFragment = this.getIntent().getSerializableExtra("launchFragment");
        String launchSubFragment = this.getIntent().getStringExtra("launchSubFragment");

        if (savedInstanceState != null) {
            selectedItem = savedInstanceState.getInt("selectedItem");
        } else if (launchFragment != null) {
            // If the application is launched from notifications
            if (launchFragment.equals(ProfileFragment.class)) {
                selectedItem = R.id.item_profile;
            }

            if (launchSubFragment != null) {
                Bundle launchSubFragmentBundle = new Bundle();
                launchSubFragmentBundle.putString("subFragment", launchSubFragment);
                getSupportFragmentManager().setFragmentResult("launchSubFragment", launchSubFragmentBundle);
            }
        }

        this.bottomNavigationView.setSelectedItemId(selectedItem);
        this.vtopHelper = new VTOPHelper(this, new VTOPHelper.Initiator() {
            @Override
            public void onLoading(boolean isLoading) {
                syncDataState.putBoolean("isLoading", isLoading);
                getSupportFragmentManager().setFragmentResult("syncDataState", syncDataState);
                
                // Show video when syncing starts, hide when it stops
                if (isLoading) {
                    showSyncVideoDialog();
                } else {
                    hideSyncVideoDialog();
                }
            }

            @Override
            public void onForceSignOut() {
                hideSyncVideoDialog();
                signOut();
            }

            @Override
            public void onComplete() {
                hideSyncVideoDialog();
                // After a manual sync completes, sync hostel data and push laundry countdown notification for hostellers
                try {
                    SharedPreferences enc = tk.therealsuji.vtopchennai.helpers.SettingsRepository.getEncryptedSharedPreferences(MainActivity.this);
                    String student = enc.getString("student_type", "");
                    if ("hosteller".equals(student)) {
                        // Sync hostel data (laundry and mess menu)
                        HostelDataHelper.getInstance(MainActivity.this).syncHostelData();

                        String block = enc.getString("hostel_block", "");
                        String room = enc.getString("room_number", "");
                        int days = tk.therealsuji.vtopchennai.helpers.HostelDataHelper.getInstance(MainActivity.this).getDaysUntilNextLaundry(block, room);
                        sendLaundryCountdownNotification(days, room);
                    }
                } catch (Exception ignored) {
                }

                restartActivity();
            }
        });

        /* Keep legacy loading logic reference (collapsed) */
        Context context = this;
        SettingsRepository.fetchAboutJson(true)
                .subscribe(new Observer<JSONObject>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(@NonNull JSONObject about) {
                        try {
                            int versionCode = about.getInt("versionCode");
                            String versionName = about.getString("tagName");
                            String releaseNotes = about.getString("releaseNotes");

                            if (versionCode > BuildConfig.VERSION_CODE) {
                                FragmentManager fragmentManager = getSupportFragmentManager();
                                FragmentTransaction transaction = fragmentManager.beginTransaction();
                                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                                transaction.add(android.R.id.content, UpdateDialogFragment.newInstance(versionName, releaseNotes)).addToBackStack(null).commit();

                                return;
                            }
                        } catch (Exception ignored) {
                        }

                        if (SettingsRepository.isRefreshRequired(context)) {
                            new MaterialAlertDialogBuilder(context)
                                    .setMessage(R.string.sync_message)
                                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss())
                                    .setPositiveButton(R.string.sync, (dialogInterface, i) -> syncData())
                                    .setTitle(R.string.sync_title)
                                    .show();
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                    }
                });


    }

    @Override
    protected void onStart() {
        super.onStart();
        startClassTimeMonitorServiceIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update bottom navigation based on student type
        checkAndUpdateBottomNavigation();

        // Reset video dialog when resuming to ensure new theme is loaded if changed
        if (syncVideoDialog != null && !syncVideoDialog.isShowing()) {
            resetSyncVideoDialog();
        }
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selectedItem", this.bottomNavigationView.getSelectedItemId());
    }

    @Override
    public void onConfigurationChanged(@androidx.annotation.NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (this.bottomNavigationView.getTranslationY() != 0) {
            this.hideBottomNavigationView();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        this.vtopHelper.bind();
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.vtopHelper.unbind();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
        
        // Clean up video dialog
        resetSyncVideoDialog();
    }

    /**
     * Initialize Firebase messaging for in-app messages
     */
    private void initializeFirebaseMessaging() {
        try {
            FirebaseMessagingHelper messagingHelper = FirebaseMessagingHelper.getInstance(this);
            messagingHelper.initializeMessaging();

            // Track app open event for automatic in-app messaging
            trackAppOpenEvent();

            // Log for debugging
            android.util.Log.d("MainActivity", "Firebase messaging initialized successfully");
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to initialize Firebase messaging: " + e.getMessage());
        }
    }

    /**
     * Track app open event for Firebase In-App Messaging triggers
     */
    private void trackAppOpenEvent() {
        try {
            // Track app_open event for automatic in-app messaging
            Bundle bundle = new Bundle();
            bundle.putString("screen_name", "MainActivity");
            bundle.putString("user_type", "student");
            bundle.putLong("timestamp", System.currentTimeMillis());

            FirebaseAnalyticsHelper.trackCustomEvent(this, "app_open", bundle);

            // Track session start
            Bundle sessionBundle = new Bundle();
            sessionBundle.putString("session_type", "app_launch");
            FirebaseAnalyticsHelper.trackCustomEvent(this, "session_start", sessionBundle);

            android.util.Log.d("MainActivity", "App open event tracked for in-app messaging");
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to track app open event: " + e.getMessage());
        }
    }

    /**
     * Initialize Firebase Remote Config
     */
    private void initializeFirebaseRemoteConfig() {
        try {
            FirebaseConfigHelper.fetchAndActivate();

            // Check for maintenance mode
            if (FirebaseConfigHelper.getBoolean(FirebaseConfigHelper.Keys.MAINTENANCE_MODE)) {
                String maintenanceMessage = FirebaseConfigHelper.getString(FirebaseConfigHelper.Keys.MAINTENANCE_MESSAGE);
                showMaintenanceDialog(maintenanceMessage);
            }

            // Log feature flags
            FirebaseCrashlyticsHelper.log("Remote Config - Features enabled: " +
                    "Attendance: " + FirebaseConfigHelper.getBoolean(FirebaseConfigHelper.Keys.FEATURE_FLAG_ATTENDANCE) +
                    ", Marks: " + FirebaseConfigHelper.getBoolean(FirebaseConfigHelper.Keys.FEATURE_FLAG_MARKS) +
                    ", Timetable: " + FirebaseConfigHelper.getBoolean(FirebaseConfigHelper.Keys.FEATURE_FLAG_TIMETABLE));

            android.util.Log.d("MainActivity", "Firebase Remote Config initialized");
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to initialize Remote Config: " + e.getMessage());
            FirebaseCrashlyticsHelper.recordException(e);
        }
    }

    /**
     * Set up Crashlytics user context
     */
    private void setupCrashlyticsContext() {
        try {
            // Set user properties for crash reporting
            String userId = SettingsRepository.getSharedPreferences(this).getString("user_id", "anonymous");
            FirebaseCrashlyticsHelper.setUserId(userId);
            FirebaseCrashlyticsHelper.setCustomKey("app_version", BuildConfig.VERSION_NAME);
            FirebaseCrashlyticsHelper.setCustomKey("build_type", BuildConfig.BUILD_TYPE);

            // Log app state
            FirebaseCrashlyticsHelper.logAppState("MainActivity", "app_launch");

            android.util.Log.d("MainActivity", "Crashlytics context set up");
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to set up Crashlytics context: " + e.getMessage());
        }
    }

    /**
     * Show maintenance dialog if maintenance mode is enabled
     */
    private void showMaintenanceDialog(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Maintenance Mode")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    /**
     * Test in-app messaging with custom events (for debugging)
     */
    private void testInAppMessaging() {
        // Wait 3 seconds after app launch, then trigger custom events
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                // Trigger multiple events to test different campaign triggers
                Bundle testBundle = new Bundle();
                testBundle.putString("test_mode", "true");
                testBundle.putString("user_type", "student");
                
                // Test different event names
                FirebaseAnalyticsHelper.trackCustomEvent(this, "test_in_app_message", testBundle);
                FirebaseAnalyticsHelper.trackCustomEvent(this, "welcome_message", testBundle);
                FirebaseAnalyticsHelper.trackCustomEvent(this, "app_ready", testBundle);
                
                // Also trigger the built-in app_open event
                FirebaseAnalyticsHelper.trackCustomEvent(this, "app_open", testBundle);
                
                android.util.Log.d("MainActivity", "Test in-app messaging events triggered");
                
                // Use FirebaseHelper to trigger In-App Messaging
                FirebaseHelper.getInstance().testInAppMessaging();
                
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Failed to trigger test in-app messaging: " + e.getMessage());
                FirebaseCrashlyticsHelper.recordException(e);
            }
        }, 3000); // 3 second delay
    }

    /**
     * Trigger Firebase In-App Messaging display directly
     */
    private void triggerFirebaseInAppMessaging() {
        try {
            // Use FirebaseHelper to trigger events
            FirebaseHelper firebaseHelper = FirebaseHelper.getInstance();
            firebaseHelper.triggerInAppMessage("app_open");
            firebaseHelper.triggerInAppMessage("welcome_message");
            
            android.util.Log.d("MainActivity", "Firebase In-App Messaging trigger events sent");
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to trigger Firebase In-App Messaging: " + e.getMessage());
        }
    }

    /**
     * Add device as test device for Firebase In-App Messaging
     */
    private void addAsTestDevice() {
        try {
            // Use FirebaseHelper to configure In-App Messaging
            FirebaseHelper firebaseHelper = FirebaseHelper.getInstance();
            firebaseHelper.setInAppMessagingEnabled(true);
            
            String deviceId = android.provider.Settings.Secure.getString(
                    getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            
            android.util.Log.d("MainActivity", "Device configured for Firebase In-App Messaging");
            android.util.Log.d("MainActivity", "To add this device as test device in Firebase Console:");
            android.util.Log.d("MainActivity", "1. Go to Firebase Console → In-App Messaging → Test devices");
            android.util.Log.d("MainActivity", "2. Add this device ID: " + deviceId);
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to add as test device: " + e.getMessage());
        }
    }

    /**
     * Initialize In-App Messaging helper
     */
    private void initializeInAppMessaging() {
        try {
            InAppMessagingHelper inAppHelper = InAppMessagingHelper.getInstance();
            inAppHelper.initialize(this);
            
            // Log device setup instructions
            InAppMessagingHelper.logTestDeviceInstructions(this);
            
            // Test in-app messaging after a delay
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                inAppHelper.testInAppMessaging();
            }, 5000); // 5 second delay
            
            android.util.Log.d("MainActivity", "In-App Messaging helper initialized");
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to initialize In-App Messaging helper: " + e.getMessage());
        }
    }
}
