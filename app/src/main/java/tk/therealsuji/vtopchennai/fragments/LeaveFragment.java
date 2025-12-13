package tk.therealsuji.vtopchennai.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.LeaveAdapter;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.interfaces.LeaveDao;
import tk.therealsuji.vtopchennai.models.Leave;
import tk.therealsuji.vtopchennai.models.LeaveApplication;

/**
 * Fragment displaying leave/OD history from VTOP.
 * Shows active leave prominently and past leaves in a collapsible list.
 * Uses Room database for data persistence and offline access.
 */
public class LeaveFragment extends Fragment {

    // Views
    private SwipeRefreshLayout swipeRefresh;
    private MaterialCardView cardActiveLeave;
    private MaterialCardView cardNoActiveLeave;
    private MaterialButton btnToggleHistory;
    private LinearLayout layoutHistory;
    private LinearLayout layoutEmpty;
    private LinearProgressIndicator loadingIndicator;
    private RecyclerView recyclerLeaveHistory;

    // Active leave views
    private TextView textActiveLeaveId;
    private TextView textActiveType;
    private TextView textActiveDuration;
    private TextView textActiveReason;
    private TextView textActivePlace;
    private Chip chipActiveStatus;

    // Adapter
    private LeaveAdapter leaveAdapter;

    // Database
    private AppDatabase appDatabase;
    private LeaveDao leaveDao;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    // State
    private boolean isHistoryVisible = false;
    private List<LeaveApplication> allLeaves = new ArrayList<>();
    private boolean isHosteller = false;

    private static final String TAG = "LeaveFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_leave, container, false);

        // Initialize database
        appDatabase = AppDatabase.getInstance(requireContext());
        leaveDao = appDatabase.leaveDao();

        initViews(view);
        setupToolbar(view);
        setupRecyclerView();
        setupListeners();

        // Load leave data - the Leave feature is accessed from Hostel tab
        // which is already hidden for day scholars, so no need for additional check
        loadLeaveData();

        return view;
    }

    /**
     * Check if the current user is a hosteller.
     * @return true if hosteller, false if day scholar
     */
    private boolean checkIsHosteller() {
        if (getContext() == null) {
            return false;
        }

        try {
            MasterKey masterKey = new MasterKey.Builder(getContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences encryptedSharedPreferences = EncryptedSharedPreferences.create(
                    getContext(),
                    "vtop_preferences",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String studentType = encryptedSharedPreferences.getString("student_type", "").toLowerCase().trim();
            Log.d(TAG, "Student type: '" + studentType + "'");

            // Check if user is a hosteller (not a day scholar)
            boolean isHostelStudent = !studentType.isEmpty() && 
                    !studentType.equals("day_scholar") && 
                    !studentType.contains("day_scholar");

            Log.d(TAG, "Is hosteller: " + isHostelStudent);
            return isHostelStudent;

        } catch (Exception e) {
            Log.e(TAG, "Error checking hosteller status", e);
            return false;
        }
    }

    /**
     * Show a message indicating this feature is only for hostellers.
     * @param rootView the root view of the fragment
     */
    private void showDayScholarMessage(View rootView) {
        loadingIndicator.setVisibility(View.GONE);
        cardActiveLeave.setVisibility(View.GONE);
        cardNoActiveLeave.setVisibility(View.GONE);
        btnToggleHistory.setVisibility(View.GONE);
        layoutHistory.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);

        // Update the empty state icon
        TextView textEmptyIcon = rootView.findViewById(R.id.text_empty_icon);
        if (textEmptyIcon != null) {
            textEmptyIcon.setText("🏠");
        }

        // Update the empty state message for day scholars
        TextView textEmptyMessage = rootView.findViewById(R.id.text_empty_message);
        if (textEmptyMessage != null) {
            textEmptyMessage.setText("Leave management is only available for hostellers.\n\nIf you are a hosteller, please update your student type in Settings.");
        }

        // Hide refresh button as it's not useful for day scholars
        MaterialButton btnRefresh = rootView.findViewById(R.id.btn_refresh);
        if (btnRefresh != null) {
            btnRefresh.setVisibility(View.GONE);
        }

        Toast.makeText(getContext(), "Leave feature is for hostellers only", Toast.LENGTH_SHORT).show();
    }

    private void initViews(View view) {
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        cardActiveLeave = view.findViewById(R.id.card_active_leave);
        cardNoActiveLeave = view.findViewById(R.id.card_no_active_leave);
        btnToggleHistory = view.findViewById(R.id.btn_toggle_history);
        layoutHistory = view.findViewById(R.id.layout_history);
        layoutEmpty = view.findViewById(R.id.layout_empty);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        recyclerLeaveHistory = view.findViewById(R.id.recycler_leave_history);

        // Active leave views
        textActiveLeaveId = view.findViewById(R.id.text_active_leave_id);
        textActiveType = view.findViewById(R.id.text_active_type);
        textActiveDuration = view.findViewById(R.id.text_active_duration);
        textActiveReason = view.findViewById(R.id.text_active_reason);
        textActivePlace = view.findViewById(R.id.text_active_place);
        chipActiveStatus = view.findViewById(R.id.chip_active_status);

        // Setup SwipeRefreshLayout
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(
                    R.color.colorPrimary,
                    R.color.colorSecondary
            );
            swipeRefresh.setOnRefreshListener(() -> {
                if (isHosteller) {
                    loadLeaveData();
                } else {
                    swipeRefresh.setRefreshing(false);
                }
            });
        }

        // Refresh button
        MaterialButton btnRefresh = view.findViewById(R.id.btn_refresh);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> loadLeaveData());
        }
    }

    private void setupToolbar(View view) {
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            });
        }
    }

    private void setupRecyclerView() {
        leaveAdapter = new LeaveAdapter();
        recyclerLeaveHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerLeaveHistory.setAdapter(leaveAdapter);
    }

    private void setupListeners() {
        btnToggleHistory.setOnClickListener(v -> {
            isHistoryVisible = !isHistoryVisible;
            updateHistoryVisibility();
        });
    }

    private void updateHistoryVisibility() {
        if (isHistoryVisible) {
            layoutHistory.setVisibility(View.VISIBLE);
            btnToggleHistory.setText("Hide Leave History");
        } else {
            layoutHistory.setVisibility(View.GONE);
            btnToggleHistory.setText("Show Leave History");
        }
    }

    /**
     * Load leave data from Room database.
     * Data is synced from VTOP during the main app sync.
     */
    private void loadLeaveData() {
        loadingIndicator.setVisibility(View.VISIBLE);
        cardActiveLeave.setVisibility(View.GONE);
        cardNoActiveLeave.setVisibility(View.GONE);
        btnToggleHistory.setVisibility(View.GONE);
        layoutHistory.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);

        // Load data from database - data is synced from VTOP during app sync
        loadFromDatabase();
    }

    /**
     * Load leave data from Room database.
     */
    private void loadFromDatabase() {
        compositeDisposable.add(
            leaveDao.getAll()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(leaves -> {
                    loadingIndicator.setVisibility(View.GONE);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);

                    if (leaves.isEmpty()) {
                        layoutEmpty.setVisibility(View.VISIBLE);
                        return;
                    }

                    // Convert Leave entities to LeaveApplication objects for display
                    allLeaves = convertToLeaveApplications(leaves);

                    // Find active leave
                    LeaveApplication activeLeave = findActiveLeave(allLeaves);
                    List<LeaveApplication> pastLeaves = getPastLeaves(allLeaves, activeLeave);

                    // Display active leave
                    if (activeLeave != null) {
                        displayActiveLeave(activeLeave);
                        cardActiveLeave.setVisibility(View.VISIBLE);
                    } else {
                        cardNoActiveLeave.setVisibility(View.VISIBLE);
                    }

                    // Display past leaves
                    if (!pastLeaves.isEmpty()) {
                        leaveAdapter.setItems(pastLeaves);
                        btnToggleHistory.setVisibility(View.VISIBLE);
                    }

                }, error -> {
                    Log.e(TAG, "Error loading from database", error);
                    loadingIndicator.setVisibility(View.GONE);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    layoutEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(getContext(), "Error loading leave data", Toast.LENGTH_SHORT).show();
                })
        );
    }

    /**
     * Convert Room Leave entities to LeaveApplication display objects.
     */
    private List<LeaveApplication> convertToLeaveApplications(List<Leave> leaves) {
        List<LeaveApplication> applications = new ArrayList<>();
        for (Leave leave : leaves) {
            LeaveApplication.LeaveType leaveType = mapLeaveType(leave.getLeaveType());
            LeaveApplication.Status status = mapStatus(leave.getStatus());
            
            applications.add(new LeaveApplication(
                    leave.getLeaveId(),
                    leave.getFromDate(),
                    leave.getToDate(),
                    leaveType,
                    leave.getLeaveType(), // Pass raw VTOP leave type
                    leave.getReason(),
                    leave.getVisitPlace(),
                    status,
                    leave.getRemarks()
            ));
        }
        return applications;
    }

    private LeaveApplication.LeaveType mapLeaveType(String type) {
        if (type == null) return LeaveApplication.LeaveType.OTHER;
        String normalized = type.toUpperCase().trim();
        if (normalized.contains("HOME")) return LeaveApplication.LeaveType.HOME_LEAVE;
        if (normalized.contains("OD") || normalized.contains("ON DUTY")) return LeaveApplication.LeaveType.ON_DUTY;
        if (normalized.contains("MEDICAL")) return LeaveApplication.LeaveType.MEDICAL;
        if (normalized.contains("EMERGENCY")) return LeaveApplication.LeaveType.EMERGENCY;
        return LeaveApplication.LeaveType.OTHER;
    }

    private LeaveApplication.Status mapStatus(String status) {
        if (status == null) return LeaveApplication.Status.REQUEST_PENDING;
        switch (status) {
            case Leave.STATUS_APPROVED:
                return LeaveApplication.Status.REQUEST_APPROVED;
            case Leave.STATUS_CLOSED:
                return LeaveApplication.Status.LEAVE_CLOSED;
            case Leave.STATUS_CANCELLED:
                return LeaveApplication.Status.REQUEST_CANCELLED;
            case Leave.STATUS_REJECTED:
                return LeaveApplication.Status.REJECTED;
            default:
                return LeaveApplication.Status.REQUEST_PENDING;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        compositeDisposable.clear();
    }

    private LeaveApplication findActiveLeave(List<LeaveApplication> leaves) {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);

        for (LeaveApplication leave : leaves) {
            try {
                Date fromDate = sdf.parse(leave.getFromDate());
                Date toDate = sdf.parse(leave.getToDate());

                if (fromDate == null || toDate == null) continue;

                // Check if currently active or upcoming
                if ((fromDate.before(now) || fromDate.equals(now)) && 
                    (toDate.after(now) || toDate.equals(now))) {
                    return leave;
                }

                // Check if upcoming (within 3 days)
                long daysUntilStart = (fromDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24);
                if (daysUntilStart >= 0 && daysUntilStart <= 3) {
                    return leave;
                }

                // Check if recently ended (within 3 days)
                long daysSinceEnd = (now.getTime() - toDate.getTime()) / (1000 * 60 * 60 * 24);
                if (daysSinceEnd >= 0 && daysSinceEnd <= 3 && 
                    leave.getStatus() != LeaveApplication.Status.LEAVE_CLOSED) {
                    return leave;
                }

            } catch (ParseException e) {
                // Try alternate format
            }
        }
        return null;
    }

    private List<LeaveApplication> getPastLeaves(List<LeaveApplication> all, LeaveApplication active) {
        List<LeaveApplication> past = new ArrayList<>();
        for (LeaveApplication leave : all) {
            if (active == null || !leave.getLeaveId().equals(active.getLeaveId())) {
                past.add(leave);
            }
        }
        return past;
    }

    private void displayActiveLeave(LeaveApplication leave) {
        textActiveLeaveId.setText(leave.getLeaveId());
        textActiveType.setText(leave.getLeaveTypeDisplay());
        textActiveDuration.setText(leave.getFromDate() + " to " + leave.getToDate());
        textActiveReason.setText(leave.getReason() != null ? leave.getReason() : "--");
        textActivePlace.setText(leave.getVisitPlace() != null ? leave.getVisitPlace() : "--");

        chipActiveStatus.setText(leave.getStatusDisplay());
        int statusColor = getStatusColor(leave.getStatus());
        chipActiveStatus.setChipBackgroundColorResource(statusColor);
    }

    private int getStatusColor(LeaveApplication.Status status) {
        switch (status) {
            case REQUEST_APPROVED:
                return R.color.green_500;
            case LEAVE_CLOSED:
                return R.color.green_700;
            case REQUEST_PENDING:
                return R.color.yellow_500;
            case REQUEST_CANCELLED:
                return R.color.gray_400;
            case REJECTED:
                return R.color.red_500;
            default:
                return R.color.gray_500;
        }
    }
}
