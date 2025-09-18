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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_hostel_info, container, false);

        recyclerViewLaundry = view.findViewById(R.id.recycler_view_laundry);
        recyclerViewMessMenu = view.findViewById(R.id.recycler_view_mess_menu);

        setupContent();

        return view;
    }

    private void setupContent() {
        if ("hosteller".equals(studentType)) {
            // Show both laundry schedule and mess menu directly
            showLaundrySchedule();
            if (!"O".equals(messType)) {
                showMessMenu();
            } else {
                recyclerViewMessMenu.setVisibility(View.GONE);
            }
        } else {
            // Hide content for day scholars
            recyclerViewLaundry.setVisibility(View.GONE);
            recyclerViewMessMenu.setVisibility(View.GONE);
        }
    }


    private void showLaundrySchedule() {
        List<HostelDataHelper.LaundrySchedule> schedules = new ArrayList<>();

        // Try to get data from database first
        hostelDataHelper.getTodaysLaundryScheduleFromDB(hostelBlock, roomNumber)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        dbSchedule -> {
                            // Convert database model to helper model for adapter compatibility
                            HostelDataHelper.LaundrySchedule helperSchedule = new HostelDataHelper.LaundrySchedule();
                            helperSchedule.setDate(dbSchedule.getDate());
                            helperSchedule.setRoomNumber(dbSchedule.getRoomNumber());

                            schedules.clear();
                            schedules.add(helperSchedule);
                            updateLaundryAdapter(schedules);
                        },
                        throwable -> {
                            // Fallback to old method if database query fails
                            android.util.Log.d("HostelInfoFragment", "Database query failed, falling back to memory: " + throwable.getMessage());
                            showLaundryScheduleFallback();
                        }
                );
    }

    private void showLaundryScheduleFallback() {
        HostelDataHelper.LaundrySchedule todaysSchedule = hostelDataHelper.getTodaysLaundrySchedule(hostelBlock, roomNumber);
        List<HostelDataHelper.LaundrySchedule> schedules = new ArrayList<>();

        if (todaysSchedule != null) {
            schedules.add(todaysSchedule);
        } else {
            // Show countdown to next laundry
            int daysUntilNext = hostelDataHelper.getDaysUntilNextLaundry(hostelBlock, roomNumber);
            if (daysUntilNext > 0) {
                HostelDataHelper.LaundrySchedule countdownSchedule = new HostelDataHelper.LaundrySchedule();
                countdownSchedule.setDate("Next laundry in " + daysUntilNext + " day" + (daysUntilNext == 1 ? "" : "s"));
                countdownSchedule.setRoomNumber("Countdown");
                schedules.add(countdownSchedule);
            }
        }

        updateLaundryAdapter(schedules);
    }

    private void updateLaundryAdapter(List<HostelDataHelper.LaundrySchedule> schedules) {
        LaundryScheduleAdapter adapter = new LaundryScheduleAdapter(schedules);
        recyclerViewLaundry.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewLaundry.setAdapter(adapter);
    }

    private void showMessMenu() {
        // Debug logging
        android.util.Log.d("HostelInfoFragment", "Getting menu for: block=" + hostelBlock + ", gender=" + gender + ", messType=" + messType);

        List<HostelDataHelper.MessMenu> menus = new ArrayList<>();

        // Try to get data from database first
        hostelDataHelper.getTodaysMessMenuFromDB(hostelBlock, gender, messType)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        dbMenu -> {
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
                            android.util.Log.d("HostelInfoFragment", "Database query failed, falling back to memory: " + throwable.getMessage());
                            showMessMenuFallback();
                        }
                );
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
        MessMenuAdapter adapter = new MessMenuAdapter(menus);
        recyclerViewMessMenu.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewMessMenu.setAdapter(adapter);
    }
}
