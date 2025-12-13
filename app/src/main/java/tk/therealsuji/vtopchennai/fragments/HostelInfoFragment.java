package tk.therealsuji.vtopchennai.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.LaundryScheduleAdapter;
import tk.therealsuji.vtopchennai.adapters.MessMenuAdapter;
import tk.therealsuji.vtopchennai.helpers.HostelDataHelper;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;

public class HostelInfoFragment extends Fragment {
    private RecyclerView recyclerViewLaundry;
    private RecyclerView recyclerViewMessMenu;

    private HostelDataHelper hostelDataHelper;
    private String studentType, gender, hostelBlock, messType, roomNumber;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hostelDataHelper = HostelDataHelper.getInstance(requireContext());

        // Get saved hostel information
        SharedPreferences encryptedPrefs = SettingsRepository.getEncryptedSharedPreferences(requireContext());
        studentType = encryptedPrefs.getString("student_type", "");
        gender = encryptedPrefs.getString("gender", "");
        hostelBlock = encryptedPrefs.getString("hostel_block", "");
        messType = encryptedPrefs.getString("mess_type", "");
        roomNumber = encryptedPrefs.getString("room_number", "");
    }

    private int currentDayOffset = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_hostel_info, container, false);

        recyclerViewLaundry = view.findViewById(R.id.recycler_view_laundry);
        recyclerViewMessMenu = view.findViewById(R.id.recycler_view_mess_menu);

        // Setup navigation buttons
        view.findViewById(R.id.btn_prev_day).setOnClickListener(v -> {
            currentDayOffset--;
            showMessMenu();
        });

        view.findViewById(R.id.btn_next_day).setOnClickListener(v -> {
            currentDayOffset++;
            showMessMenu();
        });

        // Setup Leave management button
        View btnViewLeaves = view.findViewById(R.id.btn_view_leaves);
        if (btnViewLeaves != null) {
            btnViewLeaves.setOnClickListener(v -> openLeaveFragment());
        }

        setupContent(view);

        return view;
    }

    /**
     * Opens the Leave Fragment to view leave history.
     */
    private void openLeaveFragment() {
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new LeaveFragment())
                    .addToBackStack("leave")
                    .commit();
        }
    }

    private void setupContent(View view) {
        if ("hosteller".equals(studentType)) {
            // Show both laundry schedule and mess menu directly
            showLaundrySchedule();
            if (!"O".equals(messType)) {
                showMessMenu();
            } else {
                view.findViewById(R.id.card_mess_menu).setVisibility(View.GONE);
            }
        } else {
            // Hide content for day scholars
            recyclerViewLaundry.setVisibility(View.GONE);
            view.findViewById(R.id.card_mess_menu).setVisibility(View.GONE);
        }
    }

    private void showLaundrySchedule() {
        // Subscribe to today's schedule updates
        Disposable laundryDisposable = hostelDataHelper.getTodaysLaundryScheduleFromDB(hostelBlock, roomNumber)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        dbSchedules -> {
                            if (!isAdded())
                                return;

                            if (!dbSchedules.isEmpty()) {
                                // Found today's schedule
                                updateLaundryAdapter(dbSchedules);
                            } else {
                                // No schedule for today, check for next schedule
                                checkNextLaundrySchedule();
                            }
                        },
                        throwable -> {
                            android.util.Log.e("HostelInfoFragment", "Error observing laundry schedule", throwable);
                            showLaundryScheduleFallback();
                        });

        disposables.add(laundryDisposable);
    }

    private void checkNextLaundrySchedule() {
        // Check for next schedule (one-shot check to avoid UI flickering)
        Disposable nextDisposable = hostelDataHelper.getNextLaundryScheduleFromDB(hostelBlock, roomNumber)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .firstElement()
                .subscribe(
                        dbSchedules -> {
                            if (!isAdded())
                                return;

                            List<HostelDataHelper.LaundrySchedule> displayList = new ArrayList<>();
                            if (!dbSchedules.isEmpty()) {
                                // Found next schedule, calculate days
                                HostelDataHelper.LaundrySchedule nextSchedule = dbSchedules.get(0);
                                try {
                                    int nextDay = Integer.parseInt(nextSchedule.getDate());
                                    java.util.Calendar calendar = java.util.Calendar.getInstance();
                                    int currentDay = calendar.get(java.util.Calendar.DAY_OF_MONTH);
                                    int daysUntilNext = nextDay - currentDay;

                                    if (daysUntilNext > 0) {
                                        HostelDataHelper.LaundrySchedule countdownSchedule = new HostelDataHelper.LaundrySchedule();
                                        countdownSchedule.setDate("Next laundry in " + daysUntilNext + " day"
                                                + (daysUntilNext == 1 ? "" : "s"));
                                        countdownSchedule.setRoomNumber("Room " + nextSchedule.getRoomNumber());
                                        displayList.add(countdownSchedule);
                                    }
                                } catch (NumberFormatException e) {
                                    // Invalid date format
                                }
                            }

                            if (displayList.isEmpty()) {
                                // Fallback to memory check if DB yielded nothing (e.g. before first sync)
                                showLaundryScheduleFallback();
                            } else {
                                updateLaundryAdapter(displayList);
                            }
                        },
                        throwable -> showLaundryScheduleFallback());

        // We don't add this to disposables to avoid memory leaks if it hangs,
        // but firstElement() should complete or error.
        // Actually, better to add it.
        disposables.add(nextDisposable);
    }

    private void showLaundryScheduleFallback() {
        HostelDataHelper.LaundrySchedule todaysSchedule = hostelDataHelper.getTodaysLaundrySchedule(hostelBlock,
                roomNumber);
        List<HostelDataHelper.LaundrySchedule> schedules = new ArrayList<>();

        if (todaysSchedule != null) {
            schedules.add(todaysSchedule);
        } else {
            // Show countdown to next laundry
            int daysUntilNext = hostelDataHelper.getDaysUntilNextLaundry(hostelBlock, roomNumber);
            if (daysUntilNext > 0) {
                HostelDataHelper.LaundrySchedule countdownSchedule = new HostelDataHelper.LaundrySchedule();
                countdownSchedule
                        .setDate("Next laundry in " + daysUntilNext + " day" + (daysUntilNext == 1 ? "" : "s"));
                countdownSchedule.setRoomNumber("Countdown");
                schedules.add(countdownSchedule);
            }
        }

        updateLaundryAdapter(schedules);
    }

    private void updateLaundryAdapter(List<HostelDataHelper.LaundrySchedule> schedules) {
        if (!isAdded()) {
            return;
        }

        Context context = getContext();
        if (context == null || recyclerViewLaundry == null) {
            return;
        }

        LaundryScheduleAdapter adapter = new LaundryScheduleAdapter(schedules);
        recyclerViewLaundry.setLayoutManager(new LinearLayoutManager(context));
        recyclerViewLaundry.setAdapter(adapter);
    }

    private void showMessMenu() {
        // Debug logging
        android.util.Log.d("HostelInfoFragment",
                "Getting menu for: block=" + hostelBlock + ", gender=" + gender + ", messType=" + messType + ", offset="
                        + currentDayOffset);

        List<HostelDataHelper.MessMenu> menus = new ArrayList<>();

        // Try to get data from database first
        Disposable messDisposable = hostelDataHelper
                .getMessMenuForDayFromDB(hostelBlock, gender, messType, currentDayOffset)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        dbMenu -> {
                            if (!isAdded()) {
                                return;
                            }

                            // Convert database model to helper model for adapter compatibility
                            HostelDataHelper.MessMenu helperMenu = new HostelDataHelper.MessMenu();
                            helperMenu.setDay(dbMenu.getDay());
                            helperMenu.setBreakfast(dbMenu.getBreakfast());
                            helperMenu.setLunch(dbMenu.getLunch());
                            helperMenu.setSnacks(dbMenu.getSnacks());
                            helperMenu.setDinner(dbMenu.getDinner());

                            menus.clear();
                            menus.add(helperMenu);
                            updateMessMenuAdapter(menus);
                        },
                        throwable -> {
                            // Fallback to old method if database query fails
                            android.util.Log.d("HostelInfoFragment",
                                    "Database query failed, falling back to memory: " + throwable.getMessage());
                            if (currentDayOffset == 0) {
                                showMessMenuFallback();
                            } else {
                                // Clear adapter if navigation fails for other days
                                updateMessMenuAdapter(new ArrayList<>());
                            }
                        });

        disposables.add(messDisposable);
    }

    private void showMessMenuFallback() {
        HostelDataHelper.MessMenu todaysMenu = hostelDataHelper.getTodaysMessMenu(hostelBlock, gender, messType);
        List<HostelDataHelper.MessMenu> menus = new ArrayList<>();

        if (todaysMenu != null) {
            menus.add(todaysMenu);
        }

        updateMessMenuAdapter(menus);
    }

    private void updateMessMenuAdapter(List<HostelDataHelper.MessMenu> menus) {
        if (!isAdded()) {
            return;
        }

        Context context = getContext();
        if (context == null || recyclerViewMessMenu == null) {
            return;
        }

        MessMenuAdapter adapter = new MessMenuAdapter(menus);
        recyclerViewMessMenu.setLayoutManager(new LinearLayoutManager(context));
        recyclerViewMessMenu.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }
}
