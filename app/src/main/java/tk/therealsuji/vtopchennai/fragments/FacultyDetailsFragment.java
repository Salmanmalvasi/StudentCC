package tk.therealsuji.vtopchennai.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.FacultyAdapter;
import tk.therealsuji.vtopchennai.models.Faculty;

public class FacultyDetailsFragment extends Fragment {

    private RecyclerView recyclerView;
    private FacultyAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyStateText;
    private SearchView searchView;
    private List<Faculty> allFaculties;
    private List<Faculty> displayedFaculties;
    private CompositeDisposable compositeDisposable;
    
    private static final int ITEMS_PER_PAGE = 10;
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean isSearching = false;

    public FacultyDetailsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        allFaculties = new ArrayList<>();
        displayedFaculties = new ArrayList<>();
        compositeDisposable = new CompositeDisposable();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_faculty_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar_faculty);
        recyclerView = view.findViewById(R.id.recycler_view_faculty);
        progressBar = view.findViewById(R.id.progress_bar_faculty);
        emptyStateText = view.findViewById(R.id.text_view_empty_state);
        searchView = view.findViewById(R.id.search_view_faculty);

        // Setup toolbar
        toolbar.setNavigationOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FacultyAdapter(displayedFaculties);
        recyclerView.setAdapter(adapter);
        
        // Setup pagination scroll listener
        setupPaginationScrollListener();

        // Setup SearchView
        setupSearchView();

        // Load faculty data
        loadFacultyData();
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterFaculties(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterFaculties(newText);
                return true;
            }
        });

        // Set hint
        searchView.setQueryHint("Search by faculty name...");
    }

    private void setupPaginationScrollListener() {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (!isAdded() || isLoading || isSearching) return;

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;

                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                // Load more when user scrolls near the end (within 5 items)
                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5
                        && firstVisibleItemPosition >= 0) {
                    loadMoreFaculties();
                }
            }
        });
    }

    private void loadMoreFaculties() {
        if (isLoading || allFaculties.isEmpty()) return;

        int startIndex = currentPage * ITEMS_PER_PAGE;
        if (startIndex >= allFaculties.size()) return; // All items loaded

        isLoading = true;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allFaculties.size());

        // Prepare next batch of items
        final List<Faculty> newBatch = new ArrayList<>(allFaculties.subList(startIndex, endIndex));
        final int insertPosition = displayedFaculties.size();
        
        // Post to next frame to avoid "Cannot call this method in a scroll callback" error
        if (isAdded() && recyclerView != null) {
            recyclerView.post(() -> {
                if (isAdded()) {
                    // Add items and notify in the same frame
                    displayedFaculties.addAll(newBatch);
                    adapter.notifyItemRangeInserted(insertPosition, newBatch.size());
                    currentPage++;
                    isLoading = false;
                }
            });
        } else {
            isLoading = false;
        }
    }

    private void filterFaculties(String query) {
        if (!isAdded()) return;

        isSearching = query != null && !query.isEmpty();

        if (isSearching) {
            // Search mode: show filtered results (all at once since it's filtered)
            List<Faculty> filteredList = new ArrayList<>();
            
            for (Faculty faculty : allFaculties) {
                if (faculty.matchesSearch(query)) {
                    filteredList.add(faculty);
                }
            }

            displayedFaculties.clear();
            displayedFaculties.addAll(filteredList);
            adapter.notifyDataSetChanged();
            
            // Show/hide empty state
            if (filteredList.isEmpty()) {
                emptyStateText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                emptyStateText.setText(getString(R.string.no_search_results, query));
            } else {
                emptyStateText.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        } else {
            // Not searching: reset to paginated view
            displayedFaculties.clear();
            currentPage = 0;
            loadMoreFaculties(); // Load first page
            
            if (displayedFaculties.isEmpty() && allFaculties.isEmpty()) {
                emptyStateText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                emptyStateText.setText(R.string.no_faculty_found);
            } else {
                emptyStateText.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.notifyDataSetChanged();
            }
        }
    }

    private void loadFacultyData() {
        if (!isAdded()) return;
        
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyStateText.setVisibility(View.GONE);

        // Use RxJava to load data on background thread
        compositeDisposable.add(
            Single.fromCallable(() -> {
                String jsonString = loadJSONFromAsset();
                if (jsonString == null) {
                    throw new IOException("Failed to load faculty data file");
                }
                return parseFacultyDataSync(jsonString);
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                faculties -> {
                    if (isAdded() && getView() != null) {
                        allFaculties = faculties;
                        displayedFaculties.clear();
                        currentPage = 0;
                        
                        progressBar.setVisibility(View.GONE);
                        
                        if (faculties.isEmpty()) {
                            emptyStateText.setVisibility(View.VISIBLE);
                            emptyStateText.setText(R.string.no_faculty_found);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            emptyStateText.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                            
                            // Load first page only
                            loadMoreFaculties();
                        }
                    }
                },
                error -> {
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(requireContext(), 
                            "Error loading faculty data: " + error.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                        emptyStateText.setVisibility(View.VISIBLE);
                        emptyStateText.setText(R.string.error_loading_faculty);
                    }
                }
            )
        );
    }

    private String loadJSONFromAsset() {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            InputStream inputStream = requireContext().getAssets().open("fac_det.txt");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            bufferedReader.close();
            inputStream.close();
            return stringBuilder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private List<Faculty> parseFacultyDataSync(String jsonString) throws JSONException {
        JSONArray jsonArray = new JSONArray(jsonString);
        List<Faculty> faculties = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject facultyObj = jsonArray.getJSONObject(i);

            Faculty faculty = new Faculty(
                    facultyObj.optString("emp_sno", ""),
                    facultyObj.optString("emp_name", "Unknown"),
                    facultyObj.optString("emp_id", ""),
                    facultyObj.optString("emp_designation", ""),
                    facultyObj.optString("emp_school_abbr", ""),
                    facultyObj.optString("emp_school", ""),
                    facultyObj.optString("emp_location_building", "Not Available"),
                    facultyObj.optString("emp_location_cabin", "Not Available")
            );

            faculties.add(faculty);
        }

        return faculties;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (compositeDisposable != null && !compositeDisposable.isDisposed()) {
            compositeDisposable.clear();
        }
        recyclerView = null;
        adapter = null;
        progressBar = null;
        emptyStateText = null;
        searchView = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (compositeDisposable != null) {
            compositeDisposable.dispose();
        }
    }
}
