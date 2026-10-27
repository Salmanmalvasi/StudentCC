package com.salmanmalvasi.studentcc.fragments;

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
import com.google.android.material.chip.Chip;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.adapters.FacultyAdapter;
import com.salmanmalvasi.studentcc.helpers.FacultyFavoritesStore;
import com.salmanmalvasi.studentcc.models.Faculty;

public class FacultyDetailsFragment extends Fragment {

    private RecyclerView recyclerView;
    private FacultyAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyStateText;
    private SearchView searchView;
    private Chip favoritesChip;
    private List<Faculty> allFaculties;
    private List<Faculty> displayedFaculties;
    private CompositeDisposable compositeDisposable;
    private boolean showFavoritesOnly = false;
    private String currentQuery = "";
    private boolean hasLoadedData = false;
    
    private static final int ITEMS_PER_PAGE = 5;  // Reduced for smoother scrolling
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
        favoritesChip = view.findViewById(R.id.chip_show_favorites);

        // Setup toolbar
        toolbar.setNavigationOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        if (favoritesChip != null) {
            favoritesChip.setOnCheckedChangeListener((button, isChecked) -> {
                showFavoritesOnly = isChecked;
                refreshDisplayedFaculties();
            });
        }

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FacultyAdapter(displayedFaculties, (faculty, isFavorite) -> handleFavoriteToggle(faculty, isFavorite));
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

                if (!isAdded() || isLoading || isSearching || showFavoritesOnly) return;

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;

                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                // Load more when user scrolls near the end (within 3 items)
                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 3
                        && firstVisibleItemPosition >= 0) {
                    loadMoreFaculties();
                }
            }
        });
    }

    private void handleFavoriteToggle(@NonNull Faculty faculty, boolean isFavorite) {
        refreshDisplayedFaculties();
    }

    private void loadMoreFaculties() {
        if (isLoading || allFaculties.isEmpty() || showFavoritesOnly || isSearching) return;

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
                    updateEmptyState(displayedFaculties.isEmpty(), null);
                }
            });
        } else {
            isLoading = false;
        }
    }

    private void filterFaculties(String query) {
        if (!isAdded()) return;

        currentQuery = query != null ? query.trim() : "";
        isSearching = !currentQuery.isEmpty();

        refreshDisplayedFaculties();
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
                        hasLoadedData = true;
                        displayedFaculties.clear();
                        currentPage = 0;
                        
                        progressBar.setVisibility(View.GONE);
                        refreshDisplayedFaculties();
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

    private void refreshDisplayedFaculties() {
        if (!isAdded()) return;

        applyFavoriteOrdering();

        if (hasLoadedData && progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        if (showFavoritesOnly || isSearching) {
            List<Faculty> filtered = getFilteredFaculties();
            displayedFaculties.clear();
            displayedFaculties.addAll(filtered);
            adapter.notifyDataSetChanged();
            updateEmptyState(filtered.isEmpty(), isSearching ? currentQuery : null);
        } else {
            displayedFaculties.clear();
            adapter.notifyDataSetChanged();
            currentPage = 0;
            isLoading = false;

            if (allFaculties.isEmpty()) {
                updateEmptyState(true, null);
            } else {
                updateEmptyState(false, null);
                loadMoreFaculties();
            }
        }
    }

    private void applyFavoriteOrdering() {
        if (!isAdded() || allFaculties == null || allFaculties.size() <= 1) return;
        Collections.sort(allFaculties, FacultyFavoritesStore.favoriteFirstComparator(requireContext()));
    }

    private List<Faculty> getFilteredFaculties() {
        List<Faculty> filtered = new ArrayList<>();
        if (!isAdded()) {
            return filtered;
        }

        Set<String> favorites = FacultyFavoritesStore.getFavorites(requireContext());
        for (Faculty faculty : allFaculties) {
            if (showFavoritesOnly && !FacultyFavoritesStore.isFavorite(favorites, faculty)) {
                continue;
            }
            if (isSearching && !faculty.matchesSearch(currentQuery)) {
                continue;
            }
            filtered.add(faculty);
        }
        return filtered;
    }

    private void updateEmptyState(boolean isEmpty, @Nullable String query) {
        if (emptyStateText == null || recyclerView == null) return;

        if (isEmpty) {
            recyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
            if (isSearching && query != null && !query.isEmpty()) {
                emptyStateText.setText(getString(R.string.no_search_results, query));
            } else if (showFavoritesOnly) {
                emptyStateText.setText(R.string.no_favorites_saved);
            } else {
                emptyStateText.setText(R.string.no_faculty_found);
            }
        } else {
            emptyStateText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
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
        favoritesChip = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (compositeDisposable != null) {
            compositeDisposable.dispose();
        }
    }
}
