package tk.therealsuji.vtopchennai.fragments;

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.OptIn;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.badge.ExperimentalBadgeUtils;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.analytics.FirebaseAnalytics;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.models.AttendanceRecord;



import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.TimetableAdapter;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.models.AttendanceRecord;
import tk.therealsuji.vtopchennai.widgets.AttendanceInfoCard;
import tk.therealsuji.vtopchennai.widgets.CircularProgressDrawable;
import android.widget.ProgressBar;

import com.google.android.material.snackbar.Snackbar;
import tk.therealsuji.vtopchennai.helpers.InAppMessageManager;
import tk.therealsuji.vtopchennai.models.InAppMessage;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class HomeFragment extends Fragment {

    private BroadcastReceiver inAppMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("NEW_IN_APP_MESSAGE".equals(intent.getAction())) {
                // Show the latest unread message as a Snackbar popup
                List<InAppMessage> unread = InAppMessageManager.getInstance().getUnreadMessages();
                if (!unread.isEmpty() && getView() != null) {
                    InAppMessage latest = unread.get(0);
                    Snackbar snackbar = Snackbar.make(getView(), latest.title + ": " + latest.message, Snackbar.LENGTH_LONG);
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
    private BadgeDrawable spotlightBadge;

    public HomeFragment() {
        // Required empty public constructor
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void createFallbackBadge(View spotlightButton) {
        try {
            SharedPreferences sharedPreferences = requireContext().getSharedPreferences("StudentCC", Context.MODE_PRIVATE);
            boolean eventsViewed = sharedPreferences.getBoolean("has_viewed_events_announcement", false);

            if (eventsViewed) {
                // Don't show fallback badge if events have been viewed
                return;
            }

            // Remove any existing custom badges first
            removeCustomBadges(spotlightButton);

            // Create a red badge as fallback
            android.widget.TextView fallbackBadge = new android.widget.TextView(requireContext());
            fallbackBadge.setText("Events");
            fallbackBadge.setTextColor(android.graphics.Color.WHITE);
            fallbackBadge.setTextSize(10);
            fallbackBadge.setPadding(8, 4, 8, 4);
            fallbackBadge.setTag("custom_events_badge"); // Tag for easy identification

            // Create a rounded background programmatically
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            background.setCornerRadius(12);
            background.setColor(android.graphics.Color.RED);
            fallbackBadge.setBackground(background);

            // Position the badge
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            );
            params.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            params.setMargins(0, (int) (4 * getResources().getDisplayMetrics().density),
                            (int) (4 * getResources().getDisplayMetrics().density), 0);

            fallbackBadge.setLayoutParams(params);
            fallbackBadge.setGravity(android.view.Gravity.CENTER);

            // Add to parent if it's a FrameLayout
            if (spotlightButton.getParent() instanceof android.widget.FrameLayout) {
                ((android.widget.FrameLayout) spotlightButton.getParent()).addView(fallbackBadge);
            }
        } catch (Exception e) {
            // Silently handle badge creation errors
        }
    }

    private void removeCustomBadges(View spotlightButton) {
        try {
            if (spotlightButton.getParent() instanceof android.widget.FrameLayout) {
                android.widget.FrameLayout parent = (android.widget.FrameLayout) spotlightButton.getParent();
                for (int i = parent.getChildCount() - 1; i >= 0; i--) {
                    View child = parent.getChildAt(i);
                    if (child instanceof android.widget.TextView && "custom_events_badge".equals(child.getTag())) {
                        parent.removeView(child);
                    }
                }
            }
        } catch (Exception e) {
            // Silently handle badge removal errors
        }
    }

    @OptIn(markerClass = ExperimentalBadgeUtils.class)
    private void attachMaterialBadge(View spotlightButton) {
        try {
            BadgeUtils.attachBadgeDrawable(spotlightBadge, spotlightButton);
        } catch (Exception e) {
            // Silently handle material badge attachment errors
        }
    }

    private boolean hasExistingEventsBadge(View spotlightButton) {
        try {
            if (spotlightButton.getParent() instanceof android.widget.FrameLayout) {
                android.widget.FrameLayout parent = (android.widget.FrameLayout) spotlightButton.getParent();
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    if (child instanceof android.widget.TextView && "custom_events_badge".equals(child.getTag())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private BroadcastReceiver odCountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("OD_COUNT_UPDATED".equals(intent.getAction())) {
                int odUsed = intent.getIntExtra("odUsed", 0);
                android.util.Log.d("HomeFragment", "Received OD count broadcast: " + odUsed + " used");
                TextView odRemainingCount = getView().findViewById(R.id.od_remaining_count);
                if (odRemainingCount != null) {
                    android.util.Log.d("HomeFragment", "Updating OD display: " + odUsed + "/48");
                    odRemainingCount.setText(odUsed + "/48");

                    // Update text color based on completed OD classes
                    if (odUsed >= 43) { // 43+ out of 48 (most OD used)
                        odRemainingCount.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorError, ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)));
                    } else if (odUsed >= 33) { // 33+ out of 48 (moderate OD used)
                        odRemainingCount.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSecondary, ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)));
                    } else {
                        odRemainingCount.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)));
                    }
                }
            }
        }
    };

    private void refreshODCount() {
        AppDatabase appDatabase = AppDatabase.getInstance(requireContext());
        io.reactivex.rxjava3.core.Single.zip(
                appDatabase.attendanceRecordDao().getAll(),
                appDatabase.coursesDao().getAllCourses(),
                appDatabase.coursesDao().getAllSlots(),
                (records, courses, slots) -> new Object[]{records, courses, slots}
        )
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    @SuppressWarnings("unchecked")
                    java.util.List<AttendanceRecord> attendanceRecords = (java.util.List<AttendanceRecord>) ((Object[]) result)[0];
                    @SuppressWarnings("unchecked")
                    java.util.List<tk.therealsuji.vtopchennai.models.Course> courses = (java.util.List<tk.therealsuji.vtopchennai.models.Course>) ((Object[]) result)[1];
                    @SuppressWarnings("unchecked")
                    java.util.List<tk.therealsuji.vtopchennai.models.Slot> slots = (java.util.List<tk.therealsuji.vtopchennai.models.Slot>) ((Object[]) result)[2];

                    java.util.Map<Integer, String> courseCodeById = new java.util.HashMap<>();
                    for (tk.therealsuji.vtopchennai.models.Course c : courses) {
                        courseCodeById.put(c.id, c.code != null ? c.code : "");
                    }
                    java.util.Map<Integer, java.util.List<String>> slotsByCourseId = new java.util.HashMap<>();
                    for (tk.therealsuji.vtopchennai.models.Slot s : slots) {
                        if (s.courseId != null && s.slot != null && !s.slot.isEmpty()) {
                            slotsByCourseId.computeIfAbsent(s.courseId, k -> new java.util.ArrayList<>()).add(s.slot);
                        }
                    }

                    java.util.Map<String, Integer> weightByCourseDate = new java.util.HashMap<>();

                    for (AttendanceRecord record : attendanceRecords) {
                        if (record.status != null) {
                            String status = record.status.trim().toLowerCase();

                            boolean isOD = status.equals("od") ||
                                    status.equals("on duty") ||
                                    status.contains("od") ||
                                    status.contains("on duty") ||
                                    status.contains("duty");
                            if (!isOD) continue;

                            String dateKey = record.dateString != null ? record.dateString : String.valueOf(record.date);
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
                            Integer prev = weightByCourseDate.get(key);
                            if (prev == null || weight > prev) {
                                weightByCourseDate.put(key, weight);
                            }
                        }
                    }

                    int odUsed = 0;
                    for (Integer w : weightByCourseDate.values()) {
                        odUsed += (w != null ? w : 0);
                    }

                    TextView odRemainingCount = getView().findViewById(R.id.od_remaining_count);
                    if (odRemainingCount != null) {
                        odRemainingCount.setText(odUsed + "/48");

                        // Update text color based on completed OD classes
                        if (odUsed >= 43) { // 43+ out of 48 (most OD used)
                            odRemainingCount.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorError, ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)));
                        } else if (odUsed >= 33) { // 33+ out of 48 (moderate OD used)
                            odRemainingCount.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSecondary, ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)));
                        } else {
                            odRemainingCount.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)));
                        }
                    }
                }, throwable -> {
                    android.util.Log.e("HomeFragment", "Error in manual OD count update", throwable);
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register broadcast receiver
        IntentFilter filter = new IntentFilter("OD_COUNT_UPDATED");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(odCountReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(odCountReceiver, filter);
        }

        // Manually refresh OD count when fragment resumes
        refreshODCount();

    // Register in-app message receiver for real-time popups
    IntentFilter msgFilter = new IntentFilter("NEW_IN_APP_MESSAGE");
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        requireContext().registerReceiver(inAppMessageReceiver, msgFilter, android.content.Context.RECEIVER_NOT_EXPORTED);
    } else {
        requireContext().registerReceiver(inAppMessageReceiver, msgFilter);
    }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister broadcast receiver
        try {
            requireContext().unregisterReceiver(odCountReceiver);
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
                    0
            );

            timetable.setPageTransformer((page, position) -> page.setPadding(
                    systemWindowInsetLeft,
                    0,
                    systemWindowInsetRight,
                    (int) (bottomNavigationHeight + 20 * pixelDensity)
            ));

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

        // Set text colors to primary theme color
        int primaryColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, ContextCompat.getColor(requireContext(), android.R.color.black));
        greeting.setTextColor(primaryColor);
        nameTextView.setTextColor(primaryColor);


        // Attendance Card
        CircularProgressDrawable attendanceProgress = homeFragment.findViewById(R.id.attendance_progress);
        TextView attendancePercentage = homeFragment.findViewById(R.id.attendance_percentage);
        TextView odRemainingCount = homeFragment.findViewById(R.id.od_remaining_count);

        // Robust attendance logic: always use attended/total for percentage, handle edge cases
        int attendedClasses = sharedPreferences.getInt("attendedClasses", 0);
        int totalClasses = sharedPreferences.getInt("totalClasses", 0);
        int overallAttendance = (totalClasses > 0) ? Math.round((attendedClasses * 100f) / totalClasses) : 0;

        android.util.Log.d("HomeFragment", "Attendance - Attended: " + attendedClasses + ", Total: " + totalClasses + ", Calculated: " + overallAttendance + "%");

        attendanceProgress.setProgress(overallAttendance);
        attendancePercentage.setText(overallAttendance + "%");

        // Set color backgrounds based on attendance
        View attendanceBackground = homeFragment.findViewById(R.id.attendance_background);
        if (attendanceBackground != null) {
            if (overallAttendance >= 85) {
                attendanceBackground.setBackgroundResource(R.drawable.attendance_percentage_background_high);
                attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnPrimary, android.graphics.Color.WHITE));
            } else if (overallAttendance >= 75) {
                attendanceBackground.setBackgroundResource(R.drawable.attendance_percentage_background_medium);
                attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSecondary, android.graphics.Color.WHITE));
            } else {
                attendanceBackground.setBackgroundResource(R.drawable.attendance_percentage_background_low);
                attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnError, android.graphics.Color.WHITE));
            }
        }

        // Toggle between percentage and counts
        attendanceProgress.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            if (attendancePercentage.getText().toString().contains("%")) {
                bundle.putString("display_mode", "counts");
                attendancePercentage.setText(attendedClasses + "/" + totalClasses);
            } else {
                bundle.putString("display_mode", "percentage");
                attendancePercentage.setText(overallAttendance + "%");
            }
            // Keep color logic consistent
            if (overallAttendance >= 85) {
                attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnPrimary, android.R.color.white));
            } else if (overallAttendance >= 75) {
                attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSecondary, android.R.color.white));
            } else {
                attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnError, android.R.color.white));
            }
            mFirebaseAnalytics.logEvent("toggle_attendance_display", bundle);
        });

        // Calculate OD remaining count
        AppDatabase appDatabase = AppDatabase.getInstance(this.requireContext());
        io.reactivex.rxjava3.core.Single.zip(
                appDatabase.attendanceRecordDao().getAll(),
                appDatabase.coursesDao().getAllCourses(),
                appDatabase.coursesDao().getAllSlots(),
                (records, courses, slots) -> new Object[]{records, courses, slots}
        )
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    @SuppressWarnings("unchecked")
                    java.util.List<AttendanceRecord> attendanceRecords = (java.util.List<AttendanceRecord>) ((Object[]) result)[0];
                    @SuppressWarnings("unchecked")
                    java.util.List<tk.therealsuji.vtopchennai.models.Course> courses = (java.util.List<tk.therealsuji.vtopchennai.models.Course>) ((Object[]) result)[1];
                    @SuppressWarnings("unchecked")
                    java.util.List<tk.therealsuji.vtopchennai.models.Slot> slots = (java.util.List<tk.therealsuji.vtopchennai.models.Slot>) ((Object[]) result)[2];

                    java.util.Map<Integer, String> courseCodeById = new java.util.HashMap<>();
                    for (tk.therealsuji.vtopchennai.models.Course c : courses) {
                        courseCodeById.put(c.id, c.code != null ? c.code : "");
                    }
                    java.util.Map<Integer, java.util.List<String>> slotsByCourseId = new java.util.HashMap<>();
                    for (tk.therealsuji.vtopchennai.models.Slot s : slots) {
                        if (s.courseId != null && s.slot != null && !s.slot.isEmpty()) {
                            slotsByCourseId.computeIfAbsent(s.courseId, k -> new java.util.ArrayList<>()).add(s.slot);
                        }
                    }

                    java.util.Map<String, Integer> weightByCourseDate2 = new java.util.HashMap<>();
                    for (AttendanceRecord record : attendanceRecords) {
                        if (record.status != null) {
                            String status = record.status.trim().toLowerCase();
                            boolean isOD = status.equals("od") || status.equals("on duty") || status.contains("od") || status.contains("on duty") || status.contains("duty");
                            if (!isOD) continue;

                            String dateKey = record.dateString != null ? record.dateString : String.valueOf(record.date);
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
                    odRemainingCount.setText(odUsed + "/48");

                    // Set text color based on completed OD classes
                    if (odUsed >= 43) { // 43+ out of 48 (most OD used)
                        odRemainingCount.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorError, ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)));
                    } else if (odUsed >= 33) { // 33+ out of 48 (moderate OD used)
                        odRemainingCount.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSecondary, ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)));
                    } else {
                        odRemainingCount.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)));
                    }
                }, throwable -> {
                    // Fallback - assume no OD used if no detailed data available
                    odRemainingCount.setText("0/48");
                    android.util.Log.e("HomeFragment", "Error calculating OD completed", throwable);
                });

        // Spotlight Button
        View spotlightButton = homeFragment.findViewById(R.id.image_button_spotlight);
        TooltipCompat.setTooltipText(spotlightButton, spotlightButton.getContentDescription());
        spotlightButton.setOnClickListener(view -> {
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, "Spotlight");
            mFirebaseAnalytics.logEvent("view_spotlight", bundle);

            // Hide the Events badge and mark as viewed
            if (!sharedPreferences.getBoolean("has_viewed_events_announcement", false)) {
                sharedPreferences.edit().putBoolean("has_viewed_events_announcement", true).apply();
                TextView eventsBadgeClick = homeFragment.findViewById(R.id.text_events_badge);
                eventsBadgeClick.setVisibility(View.GONE); // Hide the text badge

                // Hide the spotlight badge and custom badges
                spotlightBadge.setVisible(false);
                removeCustomBadges(spotlightButton);

                // Track that user clicked on the events announcement
                Bundle eventsBundle = new Bundle();
                eventsBundle.putString("action", "events_badge_clicked");
                mFirebaseAnalytics.logEvent("events_announcement_interaction", eventsBundle);
            }

            SettingsRepository.openRecyclerViewFragment(
                this.requireActivity(),
                R.string.spotlight,
                RecyclerViewFragment.TYPE_SPOTLIGHT
            );
        });
        // Sync Button
        View syncButton = homeFragment.findViewById(R.id.image_button_sync);
        TooltipCompat.setTooltipText(syncButton, syncButton.getContentDescription());
        syncButton.setOnClickListener(view -> {
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.SOURCE, "HomeFragment");
            mFirebaseAnalytics.logEvent("sync_data", bundle);
            // Start sync process using the same mechanism as profile page
            syncButton.setEnabled(false);
            syncButton.setAlpha(0.5f);

            // Trigger sync using fragment result (same as profile page)
            getParentFragmentManager().setFragmentResult("syncData", new Bundle());

            // Listen for sync state changes
            getParentFragmentManager().setFragmentResultListener("syncDataState", this, (requestKey, result) -> {
                if (result.getBoolean("isLoading")) {
                    syncButton.setEnabled(false);
                    syncButton.setAlpha(0.5f);
                } else {
                    syncButton.setEnabled(true);
                    syncButton.setAlpha(1.0f);
                }
            });
        });

        spotlightBadge = BadgeDrawable.create(requireContext());
        spotlightBadge.setBadgeGravity(BadgeDrawable.TOP_END);
        spotlightBadge.setHorizontalOffset((int) (8 * pixelDensity));
        spotlightBadge.setVerticalOffset((int) (8 * pixelDensity));
        spotlightBadge.setBackgroundColor(android.graphics.Color.RED);
        spotlightBadge.setBadgeTextColor(android.graphics.Color.WHITE);
        spotlightBadge.setMaxCharacterCount(6); // Increased to fit "Events"

        // Set initial state - will be updated by fragment result listener
        TextView eventsBadge = homeFragment.findViewById(R.id.text_events_badge);
        eventsBadge.setVisibility(View.GONE); // Hide the text badge

        getParentFragmentManager().setFragmentResultListener("unreadCount", this, (requestKey, result) -> {
            int spotlightCount = result.getInt("spotlight");
            boolean eventsViewed = sharedPreferences.getBoolean("has_viewed_events_announcement", false);

            if (!eventsViewed) {
                // Show "Events" badge for events announcement (handled by createFallbackBadge)
                eventsBadge.setVisibility(View.GONE);
                // Only create the badge if it doesn't already exist
                if (!hasExistingEventsBadge(spotlightButton)) {
                    createFallbackBadge(spotlightButton);
                }
            } else if (spotlightCount > 0) {
                // Show number badge for unread spotlight items using Material badge
                eventsBadge.setVisibility(View.GONE);
                // Remove custom badge and use Material badge for numbers
                removeCustomBadges(spotlightButton);
                spotlightBadge.setNumber(spotlightCount);
                spotlightBadge.setVisible(true);
                attachMaterialBadge(spotlightButton);
            } else {
                // No events badge and no unread items
                eventsBadge.setVisibility(View.GONE);
                spotlightBadge.setVisible(false);
                removeCustomBadges(spotlightButton);
            }
        });

        getParentFragmentManager().setFragmentResult("getUnreadCount", new Bundle());

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
            // Use 3-letter day abbreviations
            String[] dayAbbreviations = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
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
                    String[] dayAbbreviationsFull = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
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
                                        v.setAlpha(sharedPreferences.getBoolean("holiday_" + position, false) ? 0.5f : 1.0f);
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
                    Toast.makeText(requireContext(), (!current ? dayStrings[position] + " marked as holiday" : dayStrings[position] + " set to working day"), Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }).attach();

        // This is required to set the tooltip text again since it gets reset to the tab's text
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

        for (int i = 0; i < days.getTabCount(); ++i) {
            View day = ((ViewGroup) days.getChildAt(0)).getChildAt(i);
            ViewGroup.MarginLayoutParams tabParams = (ViewGroup.MarginLayoutParams) day.getLayoutParams();

            if (i == 0) {
                tabParams.setMarginStart((int) (20 * pixelDensity));
                tabParams.setMarginEnd((int) (5 * pixelDensity));
            } else if (i == days.getTabCount() - 1) {
                tabParams.setMarginStart((int) (5 * pixelDensity));
                tabParams.setMarginEnd((int) (20 * pixelDensity));
            } else {
                tabParams.setMarginStart((int) (5 * pixelDensity));
                tabParams.setMarginEnd((int) (5 * pixelDensity));
            }
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

        return homeFragment;
    }
}
