package tk.therealsuji.vtopchennai.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import tk.therealsuji.vtopchennai.widgets.AttendanceInfoCard;
import tk.therealsuji.vtopchennai.widgets.CircularProgressDrawable;
import android.widget.ProgressBar;

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
        ((TextView) homeFragment.findViewById(R.id.text_view_name)).setText(name);

        // Attendance, Credits, CGPA Cards
        CircularProgressDrawable attendanceProgress = homeFragment.findViewById(R.id.attendance_progress);
        TextView attendancePercentage = homeFragment.findViewById(R.id.attendance_percentage);
        TextView cgpaCircle = homeFragment.findViewById(R.id.cgpa_circle);
        TextView creditsText = homeFragment.findViewById(R.id.credits_text);

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
                // Use black text in dark mode for better visibility
                if (isDarkMode()) {
                    attendancePercentage.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
                } else {
                    attendancePercentage.setTextColor(MaterialColors.getColor(attendancePercentage, R.attr.colorOnPrimary));
                }
            } else if (overallAttendance >= 75) {
                // Medium attendance - Use theme secondary color
                attendanceBackground.setBackgroundResource(R.drawable.attendance_percentage_background_medium);
                // Use black text in dark mode for better visibility
                if (isDarkMode()) {
                    attendancePercentage.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
                } else {
                    attendancePercentage.setTextColor(MaterialColors.getColor(attendancePercentage, R.attr.colorOnSecondary));
                }
            } else {
                // Low attendance - Use theme error color
                attendanceBackground.setBackgroundResource(R.drawable.attendance_percentage_background_low);
                // Use black text in dark mode for better visibility
                if (isDarkMode()) {
                    attendancePercentage.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
                } else {
                    attendancePercentage.setTextColor(MaterialColors.getColor(attendancePercentage, R.attr.colorOnError));
                }
            }
        }

        // Add click listener to toggle between percentage and counts
        attendanceProgress.setOnClickListener(v -> {
            if (attendancePercentage.getText().toString().contains("%")) {
                // Show counts
                attendancePercentage.setText(finalAttendedClasses + "/" + finalTotalClasses);
            } else {
                // Show percentage
                attendancePercentage.setText(finalOverallAttendance + "%");
            }
        });

        float totalCredits;
        try {
            // Support old integer based credits
            totalCredits = sharedPreferences.getInt("totalCredits", 0);
        } catch (Exception ignored) {
            totalCredits = sharedPreferences.getFloat("totalCredits", 0);
        }

        float cgpaValue = sharedPreferences.getFloat("cgpa", 0);

        // Format CGPA with 2 decimal places
        String formattedCGPA = new DecimalFormat("#.00").format(cgpaValue);
        cgpaCircle.setText(formattedCGPA);

        // Format credits text
        String creditsDisplay = totalCredits == (int) totalCredits ?
            String.valueOf((int) totalCredits) + " Credits" :
            String.valueOf(totalCredits) + " Credits";
        creditsText.setText(creditsDisplay);

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
