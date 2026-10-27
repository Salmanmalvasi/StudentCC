package com.salmanmalvasi.studentcc.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import android.Manifest;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.helpers.GoogleCalendarHelper;
import com.salmanmalvasi.studentcc.models.CalendarEvent;
import android.content.pm.PackageManager;

public class CalendarFragment extends Fragment {
    private static final int FUTURE_MONTH_COUNT = 4;

    private RecyclerView calendarGrid;
    private RecyclerView eventsRecycler;
    private TabLayout monthTabs;
    private TextView monthTitle;
    private TextView eventsTitle;
    private TextView eventsEmptyText;

    private CalendarAdapter calendarAdapter;
    private EventsAdapter eventsAdapter;
    private CompositeDisposable disposables = new CompositeDisposable();

    private final Map<String, List<CalendarEvent>> eventsByMonth = new HashMap<>();
    private final List<String> monthLabels = new ArrayList<>();
    private final List<CalendarEvent> allCalendarEvents = new ArrayList<>();
    private String currentMonth = "";
    private int selectedDay = -1;

    private ActivityResultLauncher<String[]> calendarPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        calendarPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean readGranted = Boolean.TRUE.equals(result.get(Manifest.permission.READ_CALENDAR));
                    boolean writeGranted = Boolean.TRUE.equals(result.get(Manifest.permission.WRITE_CALENDAR));
                    if (readGranted && writeGranted) {
                        performGoogleCalendarSync();
                    } else {
                        showPermissionDeniedFallbackDialog();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        calendarGrid = view.findViewById(R.id.calendar_grid);
        eventsRecycler = view.findViewById(R.id.events_recycler);
        monthTabs = view.findViewById(R.id.month_tabs);
        monthTitle = view.findViewById(R.id.month_title);
        eventsTitle = view.findViewById(R.id.events_title);
        eventsEmptyText = view.findViewById(R.id.events_empty_text);

        androidx.appcompat.widget.Toolbar toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        }

        calendarGrid.setLayoutManager(new GridLayoutManager(requireContext(), 7));
        eventsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        calendarAdapter = new CalendarAdapter();
        calendarAdapter.setOnDayClickListener(dayItem -> {
            if (dayItem.day > 0) {
                showEventsForDay(currentMonth, dayItem.day);
            }
        });

        eventsAdapter = new EventsAdapter();

        calendarGrid.setAdapter(calendarAdapter);
        eventsRecycler.setAdapter(eventsAdapter);

        updateEventsDisplay(Collections.emptyList(), getString(R.string.calendar_select_day_hint));

        // Add to Google Calendar card and button setup
        View cardAddToCalendar = view.findViewById(R.id.card_add_to_calendar);
        View btnAddToCalendar = view.findViewById(R.id.btn_add_to_calendar);

        View.OnClickListener calendarClickListener = v -> onAddToCalendarClicked();

        if (cardAddToCalendar != null) {
            cardAddToCalendar.setOnClickListener(calendarClickListener);
        }
        if (btnAddToCalendar != null) {
            btnAddToCalendar.setOnClickListener(calendarClickListener);
        }

        loadCalendarData();

        return view;
    }

    private void loadCalendarData() {
        disposables.add(AppDatabase.getInstance(requireContext())
                .calendarEventDao()
                .getAll()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(events -> {
                    allCalendarEvents.clear();
                    if (events != null) {
                        allCalendarEvents.addAll(events);
                    }
                    eventsByMonth.clear();
                    monthLabels.clear();

                    for (CalendarEvent event : events) {
                        String monthKey = normalizeMonth(event.month);
                        eventsByMonth.computeIfAbsent(monthKey, key -> new ArrayList<>()).add(event);
                    }

                    monthLabels.addAll(buildMonthList());
                    if (monthLabels.isEmpty()) {
                        monthLabels.add(getCurrentMonthLabel());
                    }

                    selectedDay = -1;
                    updateMonthTabs();
                }, error -> Toast
                        .makeText(requireContext(), "Error loading calendar: " + error.getMessage(), Toast.LENGTH_LONG)
                        .show()));
    }

    private List<String> buildMonthList() {
        LinkedHashSet<String> labelSet = new LinkedHashSet<>(generateFutureMonths(FUTURE_MONTH_COUNT));
        labelSet.addAll(eventsByMonth.keySet());
        List<String> sorted = new ArrayList<>(labelSet);
        sorted.sort(Comparator.comparing(this::parseMonth));
        return sorted;
    }

    private List<String> generateFutureMonths(int count) {
        List<String> futureMonths = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        for (int i = 0; i < count; i++) {
            futureMonths.add(buildMonthLabel(calendar));
            calendar.add(Calendar.MONTH, 1);
        }
        return futureMonths;
    }

    private String normalizeMonth(String month) {
        if (month == null || month.trim().isEmpty()) {
            return getCurrentMonthLabel();
        }
        return buildMonthLabel(parseMonth(month.trim()));
    }

    private String buildMonthLabel(Calendar calendar) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
        return sdf.format(calendar.getTime());
    }

    private String getCurrentMonthLabel() {
        return buildMonthLabel(Calendar.getInstance());
    }

    private void updateMonthTabs() {
        monthTabs.removeAllTabs();
        monthTabs.clearOnTabSelectedListeners();

        for (String label : monthLabels) {
            monthTabs.addTab(monthTabs.newTab().setText(label));
        }

        TabLayout.OnTabSelectedListener listener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() < monthLabels.size()) {
                    currentMonth = monthLabels.get(tab.getPosition());
                    selectedDay = -1;
                    calendarAdapter.setSelectedDay(-1);
                    displayMonth(currentMonth);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                if (tab.getPosition() < monthLabels.size()) {
                    currentMonth = monthLabels.get(tab.getPosition());
                    selectedDay = -1;
                    calendarAdapter.setSelectedDay(-1);
                    displayMonth(currentMonth);
                }
            }
        };

        monthTabs.addOnTabSelectedListener(listener);
        if (!monthLabels.isEmpty()) {
            // Prefer to land on the current month tab if it exists, else first
            String currentMonthLabel = getCurrentMonthLabel();
            int currentIndex = monthLabels.indexOf(currentMonthLabel);
            TabLayout.Tab target = null;
            if (currentIndex >= 0 && currentIndex < monthTabs.getTabCount()) {
                target = monthTabs.getTabAt(currentIndex);
            }
            if (target == null) {
                target = monthTabs.getTabAt(0);
            }
            if (target != null)
                target.select();
        }
    }

    private void displayMonth(String month) {
        if (month == null || month.isEmpty()) {
            month = getCurrentMonthLabel();
        }
        currentMonth = month;
        monthTitle.setText(month);

        List<CalendarEvent> monthEvents = eventsByMonth.getOrDefault(month, Collections.emptyList());

        Calendar calendar = parseMonth(month);
        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        if (firstDayOfWeek == 0)
            firstDayOfWeek = 7;
        firstDayOfWeek = (firstDayOfWeek - 1) % 7;

        List<DayItem> dayItems = new ArrayList<>();
        for (int i = 0; i < firstDayOfWeek; i++) {
            dayItems.add(new DayItem(0, new ArrayList<>()));
        }

        Map<Integer, List<CalendarEvent>> byDate = new HashMap<>();
        for (CalendarEvent event : monthEvents) {
            byDate.computeIfAbsent(event.date, key -> new ArrayList<>()).add(event);
        }

        Calendar today = Calendar.getInstance();
        int todayDay = -1;
        boolean isCurrentMonth = today.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == calendar.get(Calendar.MONTH);

        for (int day = 1; day <= daysInMonth; day++) {
            List<CalendarEvent> dayEvents = byDate.getOrDefault(day, new ArrayList<>());

            // Sunday is always a holiday if nothing else is present (UniCC-style)
            Calendar dayCalendar = (Calendar) calendar.clone();
            dayCalendar.set(Calendar.DAY_OF_MONTH, day);
            int dayOfWeek = dayCalendar.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek == Calendar.SUNDAY && dayEvents.isEmpty()) {
                dayEvents.add(new CalendarEvent(month, day, "Sunday", "Holiday", "", "Weekend"));
            }

            dayItems.add(new DayItem(day, dayEvents));

            // Track today's date to auto-select it
            if (isCurrentMonth && day == today.get(Calendar.DAY_OF_MONTH) && selectedDay == -1) {
                todayDay = day;
            }
        }

        calendarAdapter.setData(dayItems);
        // Auto-select today if we're in the current month and no day selected yet
        if (selectedDay == -1 && todayDay > 0) {
            selectedDay = todayDay;
            showEventsForDay(month, todayDay);
        }
        calendarAdapter.setSelectedDay(selectedDay);

        String placeholder = monthEvents.isEmpty() ? getString(R.string.calendar_no_events_month_hint)
                : getString(R.string.calendar_select_day_hint);
        updateEventsDisplay(Collections.emptyList(), placeholder);
    }

    private void showEventsForDay(String month, int day) {
        selectedDay = day;
        calendarAdapter.setSelectedDay(day);

        List<CalendarEvent> monthEvents = eventsByMonth.getOrDefault(month, Collections.emptyList());
        List<CalendarEvent> dayEvents = new ArrayList<>();
        for (CalendarEvent event : monthEvents) {
            if (event.date == day) {
                dayEvents.add(event);
            }
        }

        String placeholder = dayEvents.isEmpty()
                ? getString(R.string.calendar_no_events_day_hint)
                : "";
        updateEventsDisplay(dayEvents, placeholder);

        if (!dayEvents.isEmpty()) {
            View view = getView();
            if (view != null) {
                androidx.core.widget.NestedScrollView scrollView = view.findViewById(R.id.calendar_scroll);
                if (scrollView != null) {
                    scrollView.post(() -> {
                        scrollView.smoothScrollTo(0, eventsTitle.getTop());
                    });
                }
            }
        }
    }

    private void updateEventsDisplay(List<CalendarEvent> dayEvents, String emptyMessage) {
        eventsAdapter.setData(dayEvents);
        if (dayEvents.isEmpty()) {
            eventsRecycler.setVisibility(View.GONE);
            eventsEmptyText.setText(emptyMessage);
            eventsEmptyText.setVisibility(View.VISIBLE);
        } else {
            eventsEmptyText.setVisibility(View.GONE);
            eventsRecycler.setVisibility(View.VISIBLE);
        }
    }

    private Calendar parseMonth(String month) {
        Calendar calendar = Calendar.getInstance();
        if (month == null || month.trim().isEmpty()) {
            return calendar;
        }
        String clean = month.trim();
        String[] patterns = {
                "MMMM yyyy",
                "MMM yyyy",
                "MMM-yyyy",
                "MMMM-yyyy",
                "yyyy-MM",
                "MM-yyyy"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ENGLISH);
                sdf.setLenient(true);
                Date date = sdf.parse(clean);
                if (date != null) {
                    calendar.setTime(date);
                    return calendar;
                }
            } catch (Exception ignored) {
            }
        }
        return calendar;
    }

    private boolean isHolidayOrNonInstructional(CalendarEvent event) {
        if (event == null) return false;
        if ("Holiday".equalsIgnoreCase(event.type)) return true;
        String text = event.text != null ? event.text.toLowerCase(Locale.ENGLISH) : "";
        String cat = event.category != null ? event.category.toLowerCase(Locale.ENGLISH) : "";
        String type = event.type != null ? event.type.toLowerCase(Locale.ENGLISH) : "";

        if (text.contains("no instructional") || text.contains("non-instructional") ||
                text.contains("non instructional") || text.contains("not an instructional") ||
                text.contains("holiday") || text.contains("vacation") || text.contains("recess") ||
                text.contains("break") || text.contains("leave")) {
            return true;
        }
        if (cat.contains("no instructional") || cat.contains("non-instructional") ||
                cat.contains("non instructional") || cat.contains("holiday") || cat.contains("vacation")) {
            return true;
        }
        if (type.contains("no instructional") || type.contains("non-instructional") ||
                type.contains("non instructional") || type.contains("holiday")) {
            return true;
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    private long lastSyncClickTime = 0;

    private void onAddToCalendarClicked() {
        long now = System.currentTimeMillis();
        if (now - lastSyncClickTime < 1000) return;
        lastSyncClickTime = now;

        if (GoogleCalendarHelper.hasCalendarPermissions(requireContext())) {
            performGoogleCalendarSync();
        } else {
            calendarPermissionLauncher.launch(new String[]{
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
            });
        }
    }

    private void performGoogleCalendarSync() {
        if (!isAdded()) return;

        androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Syncing to Google Calendar")
                .setMessage("Adding academic events, exams, and holidays to your Google Calendar...")
                .setCancelable(false)
                .show();

        io.reactivex.rxjava3.core.Single<List<CalendarEvent>> eventsSource;
        if (allCalendarEvents != null && !allCalendarEvents.isEmpty()) {
            eventsSource = io.reactivex.rxjava3.core.Single.just(new ArrayList<>(allCalendarEvents));
        } else {
            eventsSource = AppDatabase.getInstance(requireContext())
                    .calendarEventDao()
                    .getAll();
        }

        disposables.add(eventsSource
                .subscribeOn(Schedulers.io())
                .map(events -> GoogleCalendarHelper.syncEventsToDeviceCalendar(requireContext(), events, false))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    handleSyncResult(result);
                }, error -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(requireContext(), "Sync failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }));
    }

    private void handleSyncResult(GoogleCalendarHelper.SyncResult result) {
        if (!isAdded()) return;

        if (result.success && result.insertedCount > 0) {
            String msg = "Successfully added " + result.insertedCount + " academic events directly to " + result.calendarAccount + "!\n\nHolidays, instructional days, and exams are now on your calendar.";

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Google Calendar Synced")
                    .setMessage(msg)
                    .setPositiveButton("Open Google Calendar", (dialog, which) -> {
                        GoogleCalendarHelper.openGoogleCalendarApp(requireContext());
                    })
                    .setNeutralButton("Import via .ics", (dialog, which) -> {
                        exportAndOpenIcsCalendar();
                    })
                    .setNegativeButton("Done", null)
                    .show();
        } else if (result.success && result.alreadyExistsCount > 0 && result.alreadyExistsCount == result.totalConsidered) {
            String msg = "All " + result.alreadyExistsCount + " academic events are already present in " + result.calendarAccount + ".\n\nIf you don't see them in Google Calendar, ensure that " + result.calendarAccount + " is checked in Google Calendar's side drawer, or tap 'Re-import via .ics'.";

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Already Synced")
                    .setMessage(msg)
                    .setPositiveButton("Open Google Calendar", (dialog, which) -> {
                        GoogleCalendarHelper.openGoogleCalendarApp(requireContext());
                    })
                    .setNeutralButton("Re-import via .ics", (dialog, which) -> {
                        exportAndOpenIcsCalendar();
                    })
                    .setNegativeButton("Done", null)
                    .show();
        } else {
            String reason = result.errorMessage != null ? result.errorMessage : "Direct device sync could not write events to the calendar.";
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Import to Google Calendar")
                    .setMessage(reason + "\n\nYou can import the full academic calendar directly into Google Calendar with 1 tap.")
                    .setPositiveButton("Import into Google Calendar", (dialog, which) -> {
                        exportAndOpenIcsCalendar();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void showPermissionDeniedFallbackDialog() {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Calendar Permission Needed")
                .setMessage("StudentCC needs Calendar permission to automatically sync academic dates to your Google Calendar.\n\nAlternatively, you can open the calendar file (.ics) directly in Google Calendar without permissions.")
                .setPositiveButton("Open in Google Calendar", (dialog, which) -> {
                    exportAndOpenIcsCalendar();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exportAndOpenIcsCalendar() {
        if (!isAdded()) return;
        List<CalendarEvent> eventsToExport = allCalendarEvents != null && !allCalendarEvents.isEmpty()
                ? new ArrayList<>(allCalendarEvents)
                : null;

        io.reactivex.rxjava3.core.Single<List<CalendarEvent>> eventsSource;
        if (eventsToExport != null) {
            eventsSource = io.reactivex.rxjava3.core.Single.just(eventsToExport);
        } else {
            eventsSource = AppDatabase.getInstance(requireContext())
                    .calendarEventDao()
                    .getAll();
        }

        disposables.add(eventsSource
                .subscribeOn(Schedulers.io())
                .map(events -> GoogleCalendarHelper.createIcsFile(requireContext(), events, false))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(file -> {
                    if (file != null && file.exists()) {
                        GoogleCalendarHelper.openIcsInGoogleCalendar(requireContext(), file);
                    } else {
                        Toast.makeText(requireContext(), "Failed to create calendar file", Toast.LENGTH_SHORT).show();
                    }
                }, error -> {
                    Toast.makeText(requireContext(), "Error exporting calendar: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }));
    }

    static class DayItem {
        final int day;
        final List<CalendarEvent> events;

        DayItem(int day, List<CalendarEvent> events) {
            this.day = day;
            this.events = events;
        }
    }

    class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.DayViewHolder> {
        private List<DayItem> days = new ArrayList<>();
        private int highlightedDay = -1;
        private DayClickListener dayClickListener;

        void setData(List<DayItem> dayItems) {
            this.days = new ArrayList<>(dayItems);
            notifyDataSetChanged();
        }

        void setOnDayClickListener(DayClickListener listener) {
            this.dayClickListener = listener;
        }

        void setSelectedDay(int day) {
            this.highlightedDay = day;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calendar_day, parent, false);
            return new DayViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
            DayItem item = days.get(position);

            if (item.day <= 0) {
                holder.dayText.setText("");
                holder.eventIndicator.setVisibility(View.GONE);
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
                holder.itemView.setClickable(false);
                return;
            }

            holder.dayText.setText(String.valueOf(item.day));
            holder.itemView.setClickable(true);

            // Default text color - use theme-aware color
            TypedValue typedValue = new TypedValue();
            holder.itemView.getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
            int textColor = typedValue.data;

            int backgroundColor = Color.TRANSPARENT;
            int indicatorColor = ContextCompat.getColor(requireContext(), R.color.event_indicator);
            holder.eventIndicator.setVisibility(View.GONE);

            if (!item.events.isEmpty()) {
                holder.eventIndicator.setVisibility(View.VISIBLE);
                boolean hasHoliday = false;
                boolean hasInstructional = false;

                for (CalendarEvent event : item.events) {
                    if (isHolidayOrNonInstructional(event)) {
                        hasHoliday = true;
                    } else if ("Instructional Day".equalsIgnoreCase(event.type)
                            || (event.text != null && event.text.toLowerCase(Locale.ENGLISH).contains("instructional"))) {
                        hasInstructional = true;
                    }
                }

                if (hasHoliday) {
                    backgroundColor = ContextCompat.getColor(requireContext(), R.color.holiday_bg);
                    indicatorColor = ContextCompat.getColor(requireContext(), R.color.holiday_indicator);
                    // Use theme-aware text color for holiday background (RED)
                    textColor = ContextCompat.getColor(requireContext(), R.color.holiday_text);
                } else if (hasInstructional) {
                    backgroundColor = ContextCompat.getColor(requireContext(), R.color.instructional_bg);
                    indicatorColor = ContextCompat.getColor(requireContext(), R.color.instructional_indicator);
                    // Use theme-aware text color for instructional background
                    textColor = ContextCompat.getColor(requireContext(), R.color.instructional_text);
                } else {
                    backgroundColor = ContextCompat.getColor(requireContext(), R.color.event_bg);
                    indicatorColor = ContextCompat.getColor(requireContext(), R.color.event_indicator);
                    // Use theme-aware text color for event background
                    textColor = ContextCompat.getColor(requireContext(), R.color.event_text);
                }
            }

            holder.dayContainer.setBackgroundResource(R.drawable.bg_calendar_day);
            if (item.day == highlightedDay) {
                holder.dayContainer.setBackgroundTintList(android.content.res.ColorStateList
                        .valueOf(ContextCompat.getColor(requireContext(), R.color.calendar_selected_bg)));
                textColor = ContextCompat.getColor(requireContext(), R.color.calendar_selected_day_text);
            } else {
                holder.dayContainer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(backgroundColor));
            }

            holder.dayContainer.setOnClickListener(v -> {
                if (dayClickListener != null) {
                    dayClickListener.onDaySelected(item);
                }
            });

            holder.dayText.setTextColor(textColor);
            if (holder.eventIndicator.getVisibility() == View.VISIBLE) {
                holder.eventIndicator.setBackgroundColor(indicatorColor);
            }
        }

        @Override
        public int getItemCount() {
            return days.size();
        }

        class DayViewHolder extends RecyclerView.ViewHolder {
            final View dayContainer;
            final TextView dayText;
            final View eventIndicator;

            DayViewHolder(@NonNull View itemView) {
                super(itemView);
                dayContainer = itemView.findViewById(R.id.day_container);
                dayText = itemView.findViewById(R.id.day_text);
                eventIndicator = itemView.findViewById(R.id.event_indicator);
            }
        }

    }

    interface DayClickListener {
        void onDaySelected(DayItem item);
    }

    class EventsAdapter extends RecyclerView.Adapter<EventsAdapter.EventViewHolder> {
        private List<CalendarEvent> events = new ArrayList<>();

        void setData(List<CalendarEvent> data) {
            this.events = data;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calendar_event, parent, false);
            return new EventViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
            CalendarEvent event = events.get(position);
            holder.dateText.setText(String.valueOf(event.date));
            holder.eventText.setText(event.text);
            holder.categoryText.setText(event.category);

            int color;
            if (isHolidayOrNonInstructional(event)) {
                color = ContextCompat.getColor(requireContext(), R.color.holiday_indicator);
            } else if ("Instructional Day".equalsIgnoreCase(event.type) ||
                    (event.text != null && event.text.toLowerCase(Locale.ENGLISH).contains("instructional"))) {
                color = ContextCompat.getColor(requireContext(), R.color.instructional_indicator);
            } else {
                color = ContextCompat.getColor(requireContext(), R.color.event_indicator);
            }
            holder.colorBar.setBackgroundColor(color);
        }

        @Override
        public int getItemCount() {
            return events.size();
        }

        class EventViewHolder extends RecyclerView.ViewHolder {
            final TextView dateText;
            final TextView eventText;
            final TextView categoryText;
            final View colorBar;

            EventViewHolder(@NonNull View itemView) {
                super(itemView);
                dateText = itemView.findViewById(R.id.event_date);
                eventText = itemView.findViewById(R.id.event_text);
                categoryText = itemView.findViewById(R.id.event_category);
                colorBar = itemView.findViewById(R.id.event_color_bar);
            }
        }
    }
}