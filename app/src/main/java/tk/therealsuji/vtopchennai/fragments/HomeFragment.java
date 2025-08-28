package tk.therealsuji.vtopchennai.fragments;

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


import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.TimetableAdapter;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.widgets.AttendanceInfoCard;
import tk.therealsuji.vtopchennai.widgets.CircularProgressDrawable;
import android.widget.ProgressBar;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class HomeFragment extends Fragment {

    public HomeFragment() {
        // Required empty public constructor
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onResume() {
        super.onResume();


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
        int primaryColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, android.R.color.black);
        greeting.setTextColor(primaryColor);
        nameTextView.setTextColor(primaryColor);

        // Attendance Card
        CircularProgressDrawable attendanceProgress = homeFragment.findViewById(R.id.attendance_progress);
        TextView attendancePercentage = homeFragment.findViewById(R.id.attendance_percentage);
        
        // Today's Classes Card
        TextView todaysClassesCount = homeFragment.findViewById(R.id.todays_classes_count);

        int overallAttendance = sharedPreferences.getInt("overallAttendance", 0);

        // Get attended and total counts for toggle display
        int attendedClasses = sharedPreferences.getInt("attendedClasses", 0);
        int totalClasses = sharedPreferences.getInt("totalClasses", 0);

        android.util.Log.d("HomeFragment", "Real attendance data - Overall: " + overallAttendance + "%, Attended: " + attendedClasses + ", Total: " + totalClasses);

        // Make variables final for lambda expression
        final int finalAttendedClasses = attendedClasses;
        final int finalTotalClasses = totalClasses;
        final int finalOverallAttendance = overallAttendance;

        attendanceProgress.setProgress(overallAttendance);
        attendancePercentage.setText(overallAttendance + "%");

        // Set different colors and backgrounds based on attendance level and current theme
        View attendanceBackground = homeFragment.findViewById(R.id.attendance_background);
        if (attendanceBackground != null) {
            if (overallAttendance >= 85) {
                // High attendance - Use theme primary color
                attendanceBackground.setBackgroundResource(R.drawable.attendance_percentage_background_high);
                // Use theme-based text color
                attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnPrimary, android.R.color.white));
            } else if (overallAttendance >= 75) {
                // Medium attendance - Use theme secondary color
                attendanceBackground.setBackgroundResource(R.drawable.attendance_percentage_background_medium);
                // Use theme-based text color
                attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSecondary, android.R.color.white));
            } else {
                // Low attendance - Use theme error color
                attendanceBackground.setBackgroundResource(R.drawable.attendance_percentage_background_low);
                // Use theme-based text color
                attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnError, android.R.color.white));
            }
        }

        // Add click listener to toggle between percentage and counts
        attendanceProgress.setOnClickListener(v -> {
            if (attendancePercentage.getText().toString().contains("%")) {
                // Show counts
                attendancePercentage.setText(finalAttendedClasses + "/" + finalTotalClasses);
                // Use theme-based text color based on attendance level
                if (overallAttendance >= 85) {
                    attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnPrimary, android.R.color.white));
                } else if (overallAttendance >= 75) {
                    attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSecondary, android.R.color.white));
                } else {
                    attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnError, android.R.color.white));
                }
            } else {
                // Show percentage
                attendancePercentage.setText(finalOverallAttendance + "%");
                // Use theme-based text color based on attendance level
                if (overallAttendance >= 85) {
                    attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnPrimary, android.R.color.white));
                } else if (overallAttendance >= 75) {
                    attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSecondary, android.R.color.white));
                } else {
                    attendancePercentage.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnError, android.R.color.white));
                }
            }
        });

        // Calculate today's classes count
        Calendar today = Calendar.getInstance();
        int dayOfWeek = today.get(Calendar.DAY_OF_WEEK);
        // Convert to 1-based index (Sunday = 1, Monday = 2, etc.) for the DAO
        int dayIndex = dayOfWeek;
        
        // Check if today is marked as holiday
        boolean isTodayHoliday = sharedPreferences.getBoolean("holiday_" + (dayOfWeek - 1), false);
        
        if (isTodayHoliday) {
            // Show "Holiday" instead of class count
            todaysClassesCount.setText("Holiday");
        } else {
            // Get today's classes count from database
            AppDatabase appDatabase = AppDatabase.getInstance(this.requireContext());
            appDatabase.timetableDao().get(dayIndex)
                    .subscribeOn(Schedulers.single())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(timetables -> {
                        int classCount = timetables.size();
                        todaysClassesCount.setText(String.valueOf(classCount));
                    }, throwable -> {
                        // Fallback to 0 if error
                        todaysClassesCount.setText("0");
                    });
        }

        // Spotlight Button
        View spotlightButton = homeFragment.findViewById(R.id.image_button_spotlight);
        TooltipCompat.setTooltipText(spotlightButton, spotlightButton.getContentDescription());
        spotlightButton.setOnClickListener(view -> SettingsRepository.openRecyclerViewFragment(
                this.requireActivity(),
                R.string.spotlight,
                RecyclerViewFragment.TYPE_SPOTLIGHT
        ));

        // Sync Button
        View syncButton = homeFragment.findViewById(R.id.image_button_sync);
        TooltipCompat.setTooltipText(syncButton, syncButton.getContentDescription());
        syncButton.setOnClickListener(view -> {
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

        BadgeDrawable spotlightBadge = BadgeDrawable.create(requireContext());
        spotlightBadge.setBadgeGravity(BadgeDrawable.TOP_END);
        spotlightBadge.setHorizontalOffset((int) (24 * pixelDensity));
        spotlightBadge.setVerticalOffset((int) (24 * pixelDensity));
        spotlightBadge.setVisible(false);

        spotlightButton.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @OptIn(markerClass = ExperimentalBadgeUtils.class)
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                BadgeUtils.attachBadgeDrawable(spotlightBadge, spotlightButton);
                spotlightButton.removeOnLayoutChangeListener(this);
            }
        });

        getParentFragmentManager().setFragmentResultListener("unreadCount", this, (requestKey, result) -> {
            int spotlightCount = result.getInt("spotlight");
            spotlightBadge.setNumber(spotlightCount);
            spotlightBadge.setVisible(spotlightCount != 0);
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
                // Optional: Add any additional logic when a day is selected
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
