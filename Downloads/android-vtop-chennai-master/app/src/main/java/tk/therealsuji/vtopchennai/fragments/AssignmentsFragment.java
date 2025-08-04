package tk.therealsuji.vtopchennai.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.color.MaterialColors;



import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.AssignmentsGroupAdapter;
import tk.therealsuji.vtopchennai.adapters.EmptyStateAdapter;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.models.Assignment;
import tk.therealsuji.vtopchennai.models.Attachment;

public class AssignmentsFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
    CompositeDisposable compositeDisposable = new CompositeDisposable();

    RecyclerView assignmentGroups;
    SwipeRefreshLayout swipeRefreshLayout;

    public AssignmentsFragment() {
        // Required empty public constructor
    }

    /**
     * Function to get all the assignments for a list of course ids
     *
     * @param courseIds The list of course ids
     */
    private void getAssignments(List<Integer> courseIds) {
        // This method is no longer used as Moodle integration is removed.
        // The logic for fetching assignments from VTOP would go here.
        // For now, it will just show an empty state.
        if (courseIds.size() == 0) {
            assignmentGroups.setAdapter(new EmptyStateAdapter(EmptyStateAdapter.TYPE_NO_ASSIGNMENTS));
        } else {
            assignmentGroups.setAdapter(new EmptyStateAdapter(EmptyStateAdapter.TYPE_FETCHING_ASSIGNMENTS));
        }
    }

    /**
     * Function to remove activities if they have been marked as completed
     *
     * @param courseIds The list of course ids
     */
    private void filterAssignmentsByCompletion(List<Integer> courseIds, Map<Integer, Assignment> assignmentsMap) {
        List<Assignment> assignments = new ArrayList<>();
        List<Observable<ResponseBody>> singleSources = new ArrayList<>();

        for (Integer courseId : courseIds) {
            assignments.add(new Assignment()); // Placeholder for VTOP assignment
        }

        if (assignments.size() == 0) {
            assignmentGroups.setAdapter(new EmptyStateAdapter(EmptyStateAdapter.TYPE_NO_ASSIGNMENTS));
        } else {
            try {
                assignmentGroups.setAdapter(new AssignmentsGroupAdapter(assignments));
            } catch (java.text.ParseException e) {
                assignmentGroups.setAdapter(new EmptyStateAdapter(EmptyStateAdapter.TYPE_ERROR, e.getLocalizedMessage()));
            }
        }

        AppDatabase.getInstance(requireContext().getApplicationContext())
                .assignmentsDao()
                .insert(assignments)
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    private void displayAssignments(List<Assignment> assignments) {
        try {
            this.assignmentGroups.setAdapter(new AssignmentsGroupAdapter(assignments));
        } catch (java.text.ParseException e) {
            this.assignmentGroups.setAdapter(new EmptyStateAdapter(EmptyStateAdapter.TYPE_ERROR, e.getLocalizedMessage()));
        }
    }

    private void setLoading(boolean isLoading) {
        this.swipeRefreshLayout.setRefreshing(isLoading);
    }

    private void throwErrorIfExists(JSONObject jsonObject) throws Exception {
        if (jsonObject.has("error")) {
            throw new Exception(jsonObject.getString("error"));
        } else if (jsonObject.has("message")) {
            throw new Exception(jsonObject.getString("message"));
        }
    }

    private void displaySignInPage() {
        this.assignmentGroups.setAdapter(new EmptyStateAdapter(
                EmptyStateAdapter.TYPE_NOT_AUTHENTICATED,
                null,
                new EmptyStateAdapter.ButtonAttributes() {
                    @Override
                    public void onClick() {
                        // This method is no longer used as Moodle integration is removed.
                        // The logic for displaying the Moodle login dialog would go here.
                        Toast.makeText(getContext(), "Moodle sign-in functionality removed.", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public int getButtonTextId() {
                        return R.string.moodle_sign_in;
                    }
                }
        ));
        this.assignmentGroups.setOverScrollMode(View.OVER_SCROLL_NEVER);
        this.swipeRefreshLayout.setEnabled(false);
    }

    private void displayAssignmentsPage() {
        // This method is no longer used as Moodle integration is removed.
        // The logic for displaying the assignments page would go here.
        // For now, it will just show an empty state.
        this.assignmentGroups.setAdapter(new EmptyStateAdapter(EmptyStateAdapter.TYPE_FETCHING_ASSIGNMENTS));
        this.assignmentGroups.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        this.swipeRefreshLayout.setEnabled(true);
    }

    @Override
    public void onRefresh() {
        // This method is no longer used as Moodle integration is removed.
        // The logic for refreshing assignments would go here.
        Toast.makeText(getContext(), "Refresh functionality removed.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View assignmentsFragment = inflater.inflate(R.layout.fragment_assignments, container, false);

        this.assignmentGroups = assignmentsFragment.findViewById(R.id.recycler_view_assignment_groups);
        this.swipeRefreshLayout = assignmentsFragment.findViewById(R.id.swipe_refresh_layout);
        View appBarLayout = assignmentsFragment.findViewById(R.id.app_bar);

        getParentFragmentManager().setFragmentResultListener("customInsets", this, (requestKey, result) -> {
            int systemWindowInsetLeft = result.getInt("systemWindowInsetLeft");
            int systemWindowInsetTop = result.getInt("systemWindowInsetTop");
            int systemWindowInsetRight = result.getInt("systemWindowInsetRight");
            int bottomNavigationHeight = result.getInt("bottomNavigationHeight");
            float pixelDensity = getResources().getDisplayMetrics().density;

            appBarLayout.setPadding(
                    systemWindowInsetLeft,
                    systemWindowInsetTop,
                    systemWindowInsetRight,
                    0
            );

            this.assignmentGroups.setPaddingRelative(
                    systemWindowInsetLeft,
                    0,
                    systemWindowInsetRight,
                    (int) (bottomNavigationHeight + 20 * pixelDensity)
            );

            // Only one listener can be added per requestKey, so we create a duplicate
            getParentFragmentManager().setFragmentResult("customInsets2", result);
        });

        // Display Assignments if triggered from MoodleLoginDialogFragment
        getParentFragmentManager().setFragmentResultListener("displayAssignmentsPage", this, (requestKey, result) -> displayAssignmentsPage());

        this.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(this.swipeRefreshLayout, R.attr.colorSurface));
        this.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(MaterialColors.getColor(this.swipeRefreshLayout, R.attr.colorPrimary));
        this.swipeRefreshLayout.setOnRefreshListener(this);

        // Remove the if (SettingsRepository.isMoodleSignedIn(requireContext())) { ... } else { ... } block
        // Instead, always show the empty state or VTOP assignments (as previously refactored)
        this.displaySignInPage();

        return assignmentsFragment;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        compositeDisposable.dispose();
    }
}
