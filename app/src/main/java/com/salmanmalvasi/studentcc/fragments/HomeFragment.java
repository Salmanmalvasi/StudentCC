package com.salmanmalvasi.studentcc.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.TooltipCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.adapters.StatsCarouselAdapter;
import com.salmanmalvasi.studentcc.adapters.TimetableAdapter;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.helpers.InAppMessageManager;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import com.salmanmalvasi.studentcc.models.AttendanceRecord;
import com.salmanmalvasi.studentcc.models.InAppMessage;
import com.salmanmalvasi.studentcc.models.StatsItem;

public class HomeFragment extends Fragment {

    private final CompositeDisposable disposables = new CompositeDisposable();

    private BroadcastReceiver inAppMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("NEW_IN_APP_MESSAGE".equals(intent.getAction())) {
                // Show the latest unread message as a Snackbar popup
                List<InAppMessage> unread = InAppMessageManager.getInstance().getUnreadMessages();
                if (!unread.isEmpty() && getView() != null) {
                    InAppMessage latest = unread.get(0);
                    Snackbar snackbar = Snackbar.make(getView(), latest.title + ": " + latest.message,
                            Snackbar.LENGTH_LONG);
                    if (latest.hasAction()) {
                        snackbar.setAction("Open", v -> {
                            // Open the action URL in a WebViewActivity or browser
                            Intent webIntent = new Intent(Intent.ACTION_VIEW);
                            webIntent.setData(android.net.Uri.parse(latest.actionUrl));
                            startActivity(webIntent);
                        });
                    }
                    snackbar.show();
                    // Mark as read after showing
                    InAppMessageManager.getInstance().markAsRead(latest.id);
                }
            }
        }
    };

    private FirebaseAnalytics mFirebaseAnalytics;
    private StatsCarouselAdapter statsCarouselAdapter;
    private boolean showingAttendanceCounts = false;
    private int attendedClasses = 0;
    private int totalClasses = 0;
    private float overallAttendance = 0f;

    public HomeFragment() {
        // Required empty public constructor
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register broadcast receiver
        // IntentFilter filter = new IntentFilter("OD_COUNT_UPDATED");
        // if (android.os.Build.VERSION.SDK_INT >=
        // android.os.Build.VERSION_CODES.TIRAMISU) {
        // requireContext().registerReceiver(odCountReceiver, filter,
        // android.content.Context.RECEIVER_NOT_EXPORTED);
        // } else {
        // requireContext().registerReceiver(odCountReceiver, filter);
        // }

        // Manually refresh OD count when fragment resumes
        // refreshODCount();

        // Register in-app message receiver for real-time popups
        IntentFilter msgFilter = new IntentFilter("NEW_IN_APP_MESSAGE");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(inAppMessageReceiver, msgFilter,
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(inAppMessageReceiver, msgFilter);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister broadcast receiver
        // Unregister broadcast receiver
        try {
            // requireContext().unregisterReceiver(odCountReceiver);
        } catch (Exception e) {
            // Receiver might not be registered
        }

        try {
            requireContext().unregisterReceiver(inAppMessageReceiver);
        } catch (Exception e) {
            // Receiver might not be registered
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(requireContext());
        View homeFragment = inflater.inflate(R.layout.fragment_home, container, false);
        float pixelDensity = this.getResources().getDisplayMetrics().density;

        AppBarLayout appBarLayout = homeFragment.findViewById(R.id.app_bar);
        ViewPager2 timetable = homeFragment.findViewById(R.id.view_pager_timetable);

        getParentFragmentManager().setFragmentResultListener("customInsets", this, (requestKey, result) -> {
            int systemWindowInsetLeft = result.getInt("systemWindowInsetLeft");
            int systemWindowInsetTop = result.getInt("systemWindowInsetTop");
            int systemWindowInsetRight = result.getInt("systemWindowInsetRight");
            int bottomNavigationHeight = result.getInt("bottomNavigationHeight");

            appBarLayout.setPadding(
                    systemWindowInsetLeft,
                    systemWindowInsetTop,
                    systemWindowInsetRight,
                    0);

            timetable.setPageTransformer((page, position) -> page.setPadding(
                    systemWindowInsetLeft,
                    0,
                    systemWindowInsetRight,
                    (int) (bottomNavigationHeight + 20 * pixelDensity)));

            // Only one listener can be added per requestKey, so we create a duplicate
            getParentFragmentManager().setFragmentResult("customInsets2", result);
        });

        SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(this.requireContext());

        // Auto-reset weekend overrides/holidays when a new week begins
        Calendar nowCal = Calendar.getInstance();
        int weekOfYear = nowCal.get(Calendar.WEEK_OF_YEAR);
        int weekYear = nowCal.get(Calendar.YEAR);
        String currentWeekKey = weekYear + "-W" + weekOfYear;
        String storedWeekKey = sharedPreferences.getString("week_override_key", "");
        if (!currentWeekKey.equals(storedWeekKey)) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            // Remove weekend overrides
            editor.remove("working_override_0");
            editor.remove("working_override_6");
            // Reset ALL holiday flags (Sun..Sat)
            for (int i = 0; i < 7; i++) {
                editor.putBoolean("holiday_" + i, false);
            }
            editor.putString("week_override_key", currentWeekKey);
            editor.apply();
        }

        // Greeting Logic
        TextView greeting = homeFragment.findViewById(R.id.text_view_greeting);
        SimpleDateFormat hour24 = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
        Calendar calendar = Calendar.getInstance();

        try {
            Date now = hour24.parse(hour24.format(calendar.getTime()));
            assert now != null;

            if (now.before(hour24.parse("05:00"))) {
                greeting.setText(R.string.evening_greeting);
            } else if (now.before(hour24.parse("12:00"))) {
                greeting.setText(R.string.morning_greeting);
            } else if (now.before(hour24.parse("17:00"))) {
                greeting.setText(R.string.afternoon_greeting);
            } else {
                greeting.setText(R.string.evening_greeting);
            }
        } catch (Exception e) {
            // Fallback to morning greeting if parsing fails
            greeting.setText(R.string.morning_greeting);
        }

        String name = sharedPreferences.getString("name", getString(R.string.name));
        TextView nameTextView = homeFragment.findViewById(R.id.text_view_name);
        nameTextView.setText(name);

        TextView dateTextView = homeFragment.findViewById(R.id.text_view_date);
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
        dateTextView.setText(dateFormat.format(new Date()));

        TextView lastUpdatedTextView = homeFragment.findViewById(R.id.text_view_last_updated);
        updateLastUpdatedIndicator(lastUpdatedTextView);

        MaterialCardView calendarCard = homeFragment.findViewById(R.id.calendar_card);

        attendedClasses = sharedPreferences.getInt("attendedClasses", 0);
        totalClasses = sharedPreferences.getInt("totalClasses", 0);
        overallAttendance = (totalClasses > 0) ? ((attendedClasses * 100f) / totalClasses) : 0f;

        setupStatsCarousel(homeFragment, sharedPreferences);
        setupQuickActions(homeFragment);

        // Recalculate attendance directly from Room database to guarantee accuracy
        AppDatabase appDatabase = AppDatabase.getInstance(this.requireContext());
        refreshAttendanceFromDatabase(appDatabase, sharedPreferences);
        io.reactivex.rxjava3.core.Single.zip(
                appDatabase.attendanceRecordDao().getAll(),
                appDatabase.coursesDao().getAllCourses(),
                appDatabase.coursesDao().getAllSlots(),
                (records, courses, slots) -> new Object[] { records, courses, slots })
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    @SuppressWarnings("unchecked")
                    java.util.List<AttendanceRecord> attendanceRecords = (java.util.List<AttendanceRecord>) ((Object[]) result)[0];
                    @SuppressWarnings("unchecked")
                    java.util.List<com.salmanmalvasi.studentcc.models.Course> courses = (java.util.List<com.salmanmalvasi.studentcc.models.Course>) ((Object[]) result)[1];
                    @SuppressWarnings("unchecked")
                    java.util.List<com.salmanmalvasi.studentcc.models.Slot> slots = (java.util.List<com.salmanmalvasi.studentcc.models.Slot>) ((Object[]) result)[2];

                    java.util.Map<Integer, String> courseCodeById = new java.util.HashMap<>();
                    for (com.salmanmalvasi.studentcc.models.Course c : courses) {
                        courseCodeById.put(c.id, c.code != null ? c.code : "");
                    }
                    java.util.Map<Integer, java.util.List<String>> slotsByCourseId = new java.util.HashMap<>();
                    for (com.salmanmalvasi.studentcc.models.Slot s : slots) {
                        if (s.courseId != null && s.slot != null && !s.slot.isEmpty()) {
                            slotsByCourseId.computeIfAbsent(s.courseId, k -> new java.util.ArrayList<>()).add(s.slot);
                        }
                    }

                    java.util.Map<String, Integer> weightByCourseDate2 = new java.util.HashMap<>();
                    for (AttendanceRecord record : attendanceRecords) {
                        if (record.status != null) {
                            String status = record.status.trim().toLowerCase();
                            boolean isOD = status.equals("od") || status.equals("on duty") || status.contains("od")
                                    || status.contains("on duty") || status.contains("duty");
                            if (!isOD)
                                continue;

                            String dateKey = record.dateString != null ? record.dateString
                                    : String.valueOf(record.date);
                            String key = (record.courseId != null ? record.courseId : -1) + "|" + dateKey;

                            int weight = 1;
                            if (record.courseId != null) {
                                String code = courseCodeById.getOrDefault(record.courseId, "");
                                java.util.List<String> courseSlots = slotsByCourseId.get(record.courseId);
                                boolean isLabDouble = code != null && code.endsWith("P") && !(code.startsWith("BSTS"));
                                boolean eActsAsLab = false;
                                if (!isLabDouble && code != null && code.endsWith("E") && courseSlots != null) {
                                    for (String raw : courseSlots) {
                                        String s = raw.replaceAll("\\s", "");
                                        if (s.matches(".*L\\d+.*")) {
                                            eActsAsLab = true;
                                        }
                                    }
                                }
                                if (isLabDouble || eActsAsLab) {
                                    weight = 2;
                                }
                            }
                            Integer prev = weightByCourseDate2.get(key);
                            if (prev == null || weight > prev) {
                                weightByCourseDate2.put(key, weight);
                            }
                        }
                    }
                    int odUsed = 0;
                    for (Integer w : weightByCourseDate2.values()) {
                        odUsed += (w != null ? w : 0);
                    }

                    if (statsCarouselAdapter != null) {
                        statsCarouselAdapter.updateStat(1, odUsed + "/40", "OD Classes");
                    }

                }, throwable -> {
                    if (statsCarouselAdapter != null) {
                        statsCarouselAdapter.updateStat(1, "0/40", "OD Classes");
                    }
                    android.util.Log.e("HomeFragment", "Error calculating OD completed", throwable);
                });

        // Calendar Card Setup
        // Removed dynamic upcoming event loading to force "View schedule" text
        // loadUpcomingEvents(upcomingEventsText);

        if (calendarCard != null) {
            calendarCard.setOnClickListener(v -> {
                // Open CalendarFragment in a dialog or new activity
                CalendarFragment calendarFragment = new CalendarFragment();
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.frame_layout_fragment_container, calendarFragment)
                        .addToBackStack(null)
                        .commit();
            });
        }

        // Timetable Setup
        TabLayout days = homeFragment.findViewById(R.id.tab_layout_days);
        String[] dayStrings = {
                getString(R.string.sunday),
                getString(R.string.monday),
                getString(R.string.tuesday),
                getString(R.string.wednesday),
                getString(R.string.thursday),
                getString(R.string.friday),
                getString(R.string.saturday)
        };

        timetable.setAdapter(new TimetableAdapter());

        new TabLayoutMediator(days, timetable, (tab, position) -> {
            // Use 3-letter day abbreviations for clean segmented bar
            String[] dayAbbreviations = { "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT" };
            String dayAbbreviation = dayAbbreviations[position];
            tab.setText(dayAbbreviation);
            tab.view.setContentDescription(dayStrings[position]);
            TooltipCompat.setTooltipText(tab.view, dayStrings[position]);

            // Tab text colors are handled by XML attributes in the layout

            // Indicate holiday state in tab appearance
            boolean isHoliday = sharedPreferences.getBoolean("holiday_" + position, false);
            if (isHoliday) {
                tab.view.setAlpha(0.5f);
            } else {
                tab.view.setAlpha(1.0f);
            }

            // Long press to configure day
            tab.view.setOnLongClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putInt("day_of_week", position);
                mFirebaseAnalytics.logEvent("configure_day", bundle);
                // For Saturday (6) / Sunday (0) allow choosing a working day mapping
                if (position == 0 || position == 6) {
                    String[] dayAbbreviationsFull = { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
                            "Saturday" };
                    String[] options = {
                            "Set as Holiday",
                            "Work as Monday",
                            "Work as Tuesday",
                            "Work as Wednesday",
                            "Work as Thursday",
                            "Work as Friday",
                            "Clear Working Override"
                    };

                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(dayAbbreviationsFull[position] + " options")
                            .setItems(options, (dialog, which) -> {
                                switch (which) {
                                    case 0: // Set as Holiday
                                        sharedPreferences.edit()
                                                .putBoolean("holiday_" + position, true)
                                                .putInt("working_override_" + position, -1)
                                                .apply();
                                        v.setAlpha(0.5f);
                                        break;
                                    case 1: // Work as Monday
                                    case 2: // Work as Tuesday
                                    case 3: // Work as Wednesday
                                    case 4: // Work as Thursday
                                    case 5: // Work as Friday
                                        // Fix: which=1 means Monday, which=2 means Tuesday, etc.
                                        // So we need to map which=1->1 (Monday), which=2->2 (Tuesday), etc.
                                        int targetDay = which; // map to 1..5 (Mon..Fri)
                                        sharedPreferences.edit()
                                                .putBoolean("holiday_" + position, false)
                                                .putInt("working_override_" + position, targetDay)
                                                .apply();
                                        v.setAlpha(1.0f);
                                        break;
                                    case 6: // Clear Working Override
                                        sharedPreferences.edit()
                                                .putInt("working_override_" + position, -1)
                                                .apply();
                                        // keep current holiday flag
                                        v.setAlpha(sharedPreferences.getBoolean("holiday_" + position, false) ? 0.5f
                                                : 1.0f);
                                        break;
                                }

                                if (timetable.getAdapter() != null) {
                                    timetable.getAdapter().notifyItemChanged(position);
                                }
                            })
                            .show();
                } else {
                    // Weekday: simple holiday toggle
                    boolean current = sharedPreferences.getBoolean("holiday_" + position, false);
                    sharedPreferences.edit().putBoolean("holiday_" + position, !current).apply();
                    v.setAlpha(!current ? 0.5f : 1.0f);
                    if (timetable.getAdapter() != null) {
                        timetable.getAdapter().notifyItemChanged(position);
                    }
                    Toast.makeText(requireContext(), (!current ? dayStrings[position] + " marked as holiday"
                            : dayStrings[position] + " set to working day"), Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }).attach();

        // This is required to set the tooltip text again since it gets reset to the
        // tab's text
        for (int i = 0; i < days.getTabCount(); ++i) {
            TabLayout.Tab tab = days.getTabAt(i);
            int position = i;

            if (tab == null) {
                continue;
            }

            tab.view.addOnLayoutChangeListener((view, i0, i1, i2, i3, i4, i5, i6, i7) -> {
                view.setContentDescription(dayStrings[position]);
                TooltipCompat.setTooltipText(tab.view, dayStrings[position]);
            });
        }

        // Set current day as default, but allow easy navigation to other days
        // Fix: Calendar.DAY_OF_WEEK returns 1=Sunday, 2=Monday, etc.
        // We need: 0=Sunday, 1=Monday, etc.
        int currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
        timetable.setCurrentItem(currentDayOfWeek);

        // Add day selection listener for better UX
        days.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                Bundle bundle = new Bundle();
                bundle.putInt("day_of_week", tab.getPosition());
                mFirebaseAnalytics.logEvent("select_day", bundle);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Optional: Add any cleanup logic
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Optional: Add logic for when the same tab is selected again
            }
        });

        // Dynamic Sync Buttons with animations & live progress bar
        View syncButton = homeFragment.findViewById(R.id.image_button_sync);
        View quickSyncButton = homeFragment.findViewById(R.id.image_button_quick_sync);
        com.google.android.material.progressindicator.LinearProgressIndicator progressHomeSync = homeFragment
                .findViewById(R.id.progress_home_sync);
        if (syncButton != null) {
            TooltipCompat.setTooltipText(syncButton, syncButton.getContentDescription());
        }
        if (quickSyncButton != null) {
            TooltipCompat.setTooltipText(quickSyncButton, "Quick Sync (Ultra-Fast)");
        }

        final android.animation.ObjectAnimator[] rotateAnim = new android.animation.ObjectAnimator[1];
        final android.animation.ObjectAnimator[] flashPulseAnim = new android.animation.ObjectAnimator[1];

        // Normal Sync Action
        if (syncButton != null) {
            syncButton.setOnClickListener(view -> {
                Bundle bundle = new Bundle();
                bundle.putString(FirebaseAnalytics.Param.SOURCE, "HomeFragment");
                mFirebaseAnalytics.logEvent("sync_data", bundle);

                com.salmanmalvasi.studentcc.sync.DataFreshnessManager.getInstance().invalidateDataset(requireContext(),
                        com.salmanmalvasi.studentcc.sync.DataFreshnessManager.DATASET_ATTENDANCE_SUMMARY);
                com.salmanmalvasi.studentcc.sync.DataFreshnessManager.getInstance().invalidateDataset(requireContext(),
                        com.salmanmalvasi.studentcc.sync.DataFreshnessManager.DATASET_MARKS);

                syncButton.setEnabled(false);
                if (quickSyncButton != null)
                    quickSyncButton.setEnabled(false);
                if (rotateAnim[0] == null) {
                    rotateAnim[0] = android.animation.ObjectAnimator.ofFloat(syncButton, "rotation", 0f, 360f);
                    rotateAnim[0].setDuration(900);
                    rotateAnim[0].setRepeatCount(android.animation.ValueAnimator.INFINITE);
                    rotateAnim[0].setInterpolator(new android.view.animation.LinearInterpolator());
                }
                rotateAnim[0].start();
                if (progressHomeSync != null) {
                    progressHomeSync.setVisibility(View.VISIBLE);
                }

                Bundle syncBundle = new Bundle();
                syncBundle.putBoolean("quick_sync", false);
                getParentFragmentManager().setFragmentResult("syncData", syncBundle);
            });
        }

        // Quick Sync (Bolt/Flash Button) — syncs attendance, detailed attendance, marks
        // via legacy VTOPService
        if (quickSyncButton != null) {
            quickSyncButton.setOnClickListener(view -> {
                Bundle bundle = new Bundle();
                bundle.putString(FirebaseAnalytics.Param.SOURCE, "QuickSyncButton");
                mFirebaseAnalytics.logEvent("quick_sync_data", bundle);

                com.salmanmalvasi.studentcc.sync.DataFreshnessManager.getInstance().invalidateDataset(requireContext(),
                        com.salmanmalvasi.studentcc.sync.DataFreshnessManager.DATASET_ATTENDANCE_SUMMARY);
                com.salmanmalvasi.studentcc.sync.DataFreshnessManager.getInstance().invalidateDataset(requireContext(),
                        com.salmanmalvasi.studentcc.sync.DataFreshnessManager.DATASET_MARKS);

                if (flashPulseAnim[0] == null) {
                    android.animation.PropertyValuesHolder pvhX = android.animation.PropertyValuesHolder
                            .ofFloat("scaleX", 1f, 1.22f, 1f);
                    android.animation.PropertyValuesHolder pvhY = android.animation.PropertyValuesHolder
                            .ofFloat("scaleY", 1f, 1.22f, 1f);
                    flashPulseAnim[0] = android.animation.ObjectAnimator.ofPropertyValuesHolder(quickSyncButton, pvhX,
                            pvhY);
                    flashPulseAnim[0].setDuration(600);
                    flashPulseAnim[0].setRepeatCount(android.animation.ValueAnimator.INFINITE);
                }
                flashPulseAnim[0].start();

                Bundle syncBundle = new Bundle();
                syncBundle.putBoolean("quick_sync", true);
                getParentFragmentManager().setFragmentResult("syncData", syncBundle);
            });
        }

        // Listen for sync state changes
        getParentFragmentManager().setFragmentResultListener("syncDataState", this, (requestKey, result) -> {
            if (result.getBoolean("isLoading")) {
                if (syncButton != null)
                    syncButton.setEnabled(false);
                if (quickSyncButton != null)
                    quickSyncButton.setEnabled(false);
                if (rotateAnim[0] != null && !rotateAnim[0].isRunning() && syncButton != null) {
                    rotateAnim[0].start();
                }
                if (progressHomeSync != null) {
                    progressHomeSync.setVisibility(View.VISIBLE);
                }
            } else {
                if (syncButton != null) {
                    syncButton.setEnabled(true);
                    if (rotateAnim[0] != null) {
                        rotateAnim[0].cancel();
                        syncButton.setRotation(0f);
                    }
                }
                if (quickSyncButton != null) {
                    quickSyncButton.setEnabled(true);
                    if (flashPulseAnim[0] != null) {
                        flashPulseAnim[0].cancel();
                        quickSyncButton.setScaleX(1f);
                        quickSyncButton.setScaleY(1f);
                    }
                }
                if (progressHomeSync != null) {
                    progressHomeSync.setVisibility(View.GONE);
                }
            }
        });

        // Listen for validated background updates and refresh in-place without restart
        getParentFragmentManager().setFragmentResultListener("dataUpdated", this, (requestKey, result) -> {
            if (isAdded()) {
                SharedPreferences sp = SettingsRepository.getSharedPreferences(requireContext());
                refreshAttendanceFromDatabase(appDatabase, sp);
                TextView lastUpdatedTv = homeFragment.findViewById(R.id.text_view_last_updated);
                updateLastUpdatedIndicator(lastUpdatedTv);
                if (timetable != null && timetable.getAdapter() != null) {
                    timetable.getAdapter().notifyDataSetChanged();
                }
            }
        });

        return homeFragment;
    }

    private void updateLastUpdatedIndicator(TextView lastUpdatedTextView) {
        if (lastUpdatedTextView == null || getContext() == null)
            return;
        String formatted = com.salmanmalvasi.studentcc.sync.DataFreshnessManager.getInstance()
                .getFormattedLastUpdated(requireContext(),
                        com.salmanmalvasi.studentcc.sync.DataFreshnessManager.DATASET_ATTENDANCE_SUMMARY);
        if (!"Not updated yet".equals(formatted)) {
            lastUpdatedTextView.setText(formatted);
            lastUpdatedTextView.setVisibility(View.VISIBLE);
        } else {
            lastUpdatedTextView.setVisibility(View.GONE);
        }
    }

    private void refreshAttendanceFromDatabase(AppDatabase appDatabase, SharedPreferences sharedPreferences) {
        disposables.add(
                appDatabase.attendanceDao().getAll()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(attendances -> {
                            if (attendances != null && !attendances.isEmpty()) {
                                int sumAttended = 0;
                                int sumTotal = 0;
                                for (com.salmanmalvasi.studentcc.models.Attendance a : attendances) {
                                    if (a.attended != null)
                                        sumAttended += a.attended;
                                    if (a.total != null)
                                        sumTotal += a.total;
                                }
                                if (sumTotal > 0) {
                                    attendedClasses = sumAttended;
                                    totalClasses = sumTotal;
                                    overallAttendance = (sumAttended * 100f) / sumTotal;
                                    sharedPreferences.edit()
                                            .putInt("attendedClasses", attendedClasses)
                                            .putInt("totalClasses", totalClasses)
                                            .putFloat("overallAttendance", overallAttendance)
                                            .apply();

                                    if (statsCarouselAdapter != null) {
                                        String value = showingAttendanceCounts
                                                ? attendedClasses + "/" + totalClasses
                                                : String.format(java.util.Locale.ENGLISH, "%.2f%%", overallAttendance);
                                        statsCarouselAdapter.updateStat(0, value,
                                                getAttendanceSubtitle(sharedPreferences));
                                    }
                                }
                            }
                        }, throwable -> {
                            android.util.Log.e("HomeFragment", "Error loading attendance from DB", throwable);
                        }));
    }

    private void setupStatsCarousel(View homeFragment, SharedPreferences sharedPreferences) {
        RecyclerView statsCarousel = homeFragment.findViewById(R.id.recycler_stats_carousel);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL,
                false);
        statsCarousel.setLayoutManager(layoutManager);
        new PagerSnapHelper().attachToRecyclerView(statsCarousel);

        List<StatsItem> stats = new ArrayList<>();
        stats.add(new StatsItem(R.drawable.ic_theory,
                String.format(java.util.Locale.ENGLISH, "%.2f%%", overallAttendance),
                getAttendanceSubtitle(sharedPreferences)));
        stats.add(new StatsItem(R.drawable.ic_notifications, "--/40", "OD Classes"));

        statsCarouselAdapter = new StatsCarouselAdapter();
        statsCarouselAdapter.setStats(stats);
        statsCarouselAdapter.setOnStatClickListener((position, item) -> {
            if (position == 0) {
                toggleAttendanceDisplay(sharedPreferences);
            }
        });
        statsCarousel.setAdapter(statsCarouselAdapter);
    }

    private String getAttendanceSubtitle(SharedPreferences sharedPreferences) {
        String studentType = sharedPreferences.getString("student_type", "");
        if (!"day_scholar".equals(studentType) || totalClasses <= 0) {
            return "Attendance";
        }

        double target = 0.85;
        if (overallAttendance < 85f) {
            int x = (int) Math.ceil((target * totalClasses - attendedClasses) / (1 - target));
            return "Attend " + x + " more for 85%";
        }

        int y = (int) Math.floor((attendedClasses - target * totalClasses) / target);
        return "Can skip " + y + " classes";
    }

    private void toggleAttendanceDisplay(SharedPreferences sharedPreferences) {
        showingAttendanceCounts = !showingAttendanceCounts;
        String value = showingAttendanceCounts
                ? attendedClasses + "/" + totalClasses
                : String.format(java.util.Locale.ENGLISH, "%.2f%%", overallAttendance);
        statsCarouselAdapter.updateStat(0, value, getAttendanceSubtitle(sharedPreferences));

        Bundle bundle = new Bundle();
        bundle.putString("display_mode", showingAttendanceCounts ? "counts" : "percentage");
        mFirebaseAnalytics.logEvent("toggle_attendance_display", bundle);
    }

    private void setupQuickActions(View homeFragment) {
        Chip skipAdvisor = homeFragment.findViewById(R.id.chip_skip_advisor);
        Chip attendanceCalc = homeFragment.findViewById(R.id.chip_attendance_calc);
        Chip faculty = homeFragment.findViewById(R.id.chip_faculty);
        Chip placementTracker = homeFragment.findViewById(R.id.chip_placement_tracker);
        Chip calendar = homeFragment.findViewById(R.id.chip_calendar);

        if (skipAdvisor != null)
            skipAdvisor.setOnClickListener(v -> openFeatureFragment(new SkipAdvisorFragment()));
        if (attendanceCalc != null)
            attendanceCalc.setOnClickListener(v -> openFeatureFragment(new AttendanceCalculatorFragment()));
        if (faculty != null)
            faculty.setOnClickListener(v -> openFeatureFragment(new FacultyDetailsFragment()));
        if (placementTracker != null) {
            placementTracker.setOnClickListener(v -> openFeatureFragment(new PlacementTrackerFragment()));
        }
        Chip addWidget = homeFragment.findViewById(R.id.chip_add_widget);
        if (addWidget != null) {
            addWidget.setOnClickListener(v -> com.salmanmalvasi.studentcc.helpers.WidgetHelper.showWidgetPickerDialog(requireContext()));
        }

        calendar.setOnClickListener(v -> {
            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.frame_layout_fragment_container, new CalendarFragment())
                    .addToBackStack(null)
                    .commit();
        });

        // Widget Prompt Card
        View cardWidgetPrompt = homeFragment.findViewById(R.id.card_widget_prompt);
        if (cardWidgetPrompt != null) {
            SharedPreferences sp = SettingsRepository.getSharedPreferences(requireContext());
            boolean dismissed = sp.getBoolean("widget_prompt_dismissed", false);
            boolean alreadyInstalled = com.salmanmalvasi.studentcc.helpers.WidgetHelper.hasAnyWidgetInstalled(requireContext());

            if (!dismissed && !alreadyInstalled) {
                cardWidgetPrompt.setVisibility(View.VISIBLE);

                View btnAdd = homeFragment.findViewById(R.id.btn_prompt_add_widget);
                if (btnAdd != null) {
                    btnAdd.setOnClickListener(v -> {
                        com.salmanmalvasi.studentcc.helpers.WidgetHelper.showWidgetPickerDialog(requireContext());
                        cardWidgetPrompt.setVisibility(View.GONE);
                        sp.edit().putBoolean("widget_prompt_dismissed", true).apply();
                    });
                }

                View btnDismiss = homeFragment.findViewById(R.id.btn_dismiss_widget_prompt);
                if (btnDismiss != null) {
                    btnDismiss.setOnClickListener(v -> {
                        cardWidgetPrompt.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                            cardWidgetPrompt.setVisibility(View.GONE);
                        });
                        sp.edit().putBoolean("widget_prompt_dismissed", true).apply();
                    });
                }
            } else {
                cardWidgetPrompt.setVisibility(View.GONE);
            }
        }
    }

    private void openFeatureFragment(Fragment fragment) {
        if (!isAdded()) {
            return;
        }
        FragmentActivity activity = requireActivity();
        activity.getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, 0, 0, R.anim.slide_out_right)
                .add(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();
    }
}
