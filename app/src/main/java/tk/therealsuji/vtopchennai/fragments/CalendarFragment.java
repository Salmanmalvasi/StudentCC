package tk.therealsuji.vtopchennai.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.models.CalendarEvent;

public class CalendarFragment extends Fragment {
    private static final int FUTURE_MONTH_COUNT = 4;

    private RecyclerView calendarGrid;
    private RecyclerView eventsRecycler;
    private TabLayout monthTabs;
    private TextView monthTitle;
    private TextView eventsEmptyText;

    private CalendarAdapter calendarAdapter;
    private EventsAdapter eventsAdapter;
    private CompositeDisposable disposables = new CompositeDisposable();

    private final Map<String, List<CalendarEvent>> eventsByMonth = new HashMap<>();
    private final List<String> monthLabels = new ArrayList<>();
    private String currentMonth = "";
    private int selectedDay = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        calendarGrid = view.findViewById(R.id.calendar_grid);
        eventsRecycler = view.findViewById(R.id.events_recycler);
        monthTabs = view.findViewById(R.id.month_tabs);
        monthTitle = view.findViewById(R.id.month_title);
        eventsEmptyText = view.findViewById(R.id.events_empty_text);

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
            }, error -> Toast.makeText(requireContext(), "Error loading calendar: " + error.getMessage(), Toast.LENGTH_LONG).show()));
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
            TabLayout.Tab first = monthTabs.getTabAt(0);
            if (first != null) {
                first.select();
            }
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
        if (firstDayOfWeek == 0) firstDayOfWeek = 7;
        firstDayOfWeek = (firstDayOfWeek - 1) % 7;

        List<DayItem> dayItems = new ArrayList<>();
        for (int i = 0; i < firstDayOfWeek; i++) {
            dayItems.add(new DayItem(0, new ArrayList<>()));
        }

        Map<Integer, List<CalendarEvent>> byDate = new HashMap<>();
        for (CalendarEvent event : monthEvents) {
            byDate.computeIfAbsent(event.date, key -> new ArrayList<>()).add(event);
        }

        for (int day = 1; day <= daysInMonth; day++) {
            List<CalendarEvent> dayEvents = byDate.getOrDefault(day, new ArrayList<>());
            dayItems.add(new DayItem(day, dayEvents));
        }

        calendarAdapter.setData(dayItems);
        calendarAdapter.setSelectedDay(selectedDay);

        String placeholder = monthEvents.isEmpty() ?
            getString(R.string.calendar_no_events_month_hint) : getString(R.string.calendar_select_day_hint);
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
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
            Date date = sdf.parse(month);
            Calendar calendar = Calendar.getInstance();
            if (date != null) {
                calendar.setTime(date);
            }
            return calendar;
        } catch (ParseException e) {
            return Calendar.getInstance();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
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
        private final String[] weekDays = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        private List<DayItem> days = new ArrayList<>();
        private int highlightedDay = -1;
        private DayClickListener dayClickListener;

        void setData(List<DayItem> dayItems) {
            this.days = new ArrayList<>();
            for (String weekDay : weekDays) {
                this.days.add(new DayItem(-1, new ArrayList<>()));
            }
            this.days.addAll(dayItems);
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

            if (position < 7) {
                holder.dayText.setText(weekDays[position]);
                holder.dayText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                holder.dayText.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorPrimaryBlue));
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
                holder.eventIndicator.setVisibility(View.GONE);
                holder.itemView.setClickable(false);
                return;
            }

            if (item.day == 0) {
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
                    if ("Holiday".equals(event.type)) {
                        hasHoliday = true;
                    } else if ("Instructional Day".equals(event.type)) {
                        hasInstructional = true;
                    }
                }

                if (hasHoliday) {
                    backgroundColor = ContextCompat.getColor(requireContext(), R.color.holiday_bg);
                    indicatorColor = ContextCompat.getColor(requireContext(), R.color.holiday_indicator);
                    // Use theme-aware text color for holiday background
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

            if (item.day == highlightedDay) {
                backgroundColor = ContextCompat.getColor(requireContext(), R.color.calendar_selected_bg);
                textColor = ContextCompat.getColor(requireContext(), R.color.calendar_selected_day_text);
            }

            holder.itemView.setBackgroundColor(backgroundColor);
            holder.dayText.setTextColor(textColor);
            if (holder.eventIndicator.getVisibility() == View.VISIBLE) {
                holder.eventIndicator.setBackgroundColor(indicatorColor);
            }

            holder.itemView.setOnClickListener(v -> {
                if (dayClickListener != null) {
                    dayClickListener.onDaySelected(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return days.size();
        }

        class DayViewHolder extends RecyclerView.ViewHolder {
            final TextView dayText;
            final View eventIndicator;

            DayViewHolder(@NonNull View itemView) {
                super(itemView);
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
            if ("Holiday".equals(event.type)) {
                color = ContextCompat.getColor(requireContext(), R.color.holiday_indicator);
            } else if ("Instructional Day".equals(event.type)) {
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