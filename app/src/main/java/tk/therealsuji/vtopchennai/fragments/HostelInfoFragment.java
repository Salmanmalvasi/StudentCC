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
            showMessMenu();
        } else {
            // Hide content for day scholars
            recyclerViewLaundry.setVisibility(View.GONE);
            recyclerViewMessMenu.setVisibility(View.GONE);
        }
    }
    
    
    private void showLaundrySchedule() {
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
        
        LaundryScheduleAdapter adapter = new LaundryScheduleAdapter(schedules);
        recyclerViewLaundry.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewLaundry.setAdapter(adapter);
    }
    
    private void showMessMenu() {
        // Debug logging
        android.util.Log.d("HostelInfoFragment", "Getting menu for: block=" + hostelBlock + ", gender=" + gender + ", messType=" + messType);
        
        HostelDataHelper.MessMenu todaysMenu = hostelDataHelper.getTodaysMessMenu(hostelBlock, gender, messType);
        List<HostelDataHelper.MessMenu> menus = new ArrayList<>();
        
        if (todaysMenu != null) {
            menus.add(todaysMenu);
        }
        
        MessMenuAdapter adapter = new MessMenuAdapter(menus);
        recyclerViewMessMenu.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewMessMenu.setAdapter(adapter);
    }
}
