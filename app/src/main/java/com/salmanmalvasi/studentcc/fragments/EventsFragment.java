package com.salmanmalvasi.studentcc.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.adapters.EventCategoryAdapter;
import com.salmanmalvasi.studentcc.adapters.EventHubAdapter;
import com.salmanmalvasi.studentcc.adapters.FeaturedEventAdapter;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.models.EventClub;
import com.salmanmalvasi.studentcc.models.EventHubEvent;

/**
 * Fragment displaying VIT EventHub campus events
 * Fetches events from https://eventhubcc.vit.ac.in/EventHub/
 */
public class EventsFragment extends Fragment {

    private static final String TAG = "EventsFragment";
    private static final String EVENTHUB_URL = "https://eventhubcc.vit.ac.in/EventHub/";
    private static final long CACHE_DURATION_MS = TimeUnit.HOURS.toMillis(1); // Refresh every hour

    private RecyclerView eventsRecyclerView;
    // private RecyclerView featuredRecyclerView; // Removed for now
    private RecyclerView clubsRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout emptyState;
    private ChipGroup filterChips;
    private TextView lastUpdatedText;
    private ImageButton backButton;
    private TextInputEditText searchEditText;

    private EventHubAdapter adapter;
    // private FeaturedEventAdapter featuredAdapter; // Removed for now
    private EventCategoryAdapter categoryAdapter;
    private CompositeDisposable disposables = new CompositeDisposable();
    private List<EventHubEvent> allEvents = new ArrayList<>();
    private List<EventClub> availableClubs = new ArrayList<>();

    private String currentClubFilter = "0"; // ID "0" = All
    private String currentFilter = "all"; // all, free, upcoming, vitian
    private String searchQuery = ""; // current search query

    private static class ScrapeResult {
        List<EventHubEvent> events;
        List<EventClub> clubs;

        ScrapeResult(List<EventHubEvent> events, List<EventClub> clubs) {
            this.events = events;
            this.clubs = clubs;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_events_hub, container, false);

        eventsRecyclerView = view.findViewById(R.id.events_recycler_view);
        // featuredRecyclerView = view.findViewById(R.id.rv_featured_events);
        clubsRecyclerView = view.findViewById(R.id.rv_clubs);

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        emptyState = view.findViewById(R.id.empty_state);
        filterChips = view.findViewById(R.id.filter_chips);
        lastUpdatedText = view.findViewById(R.id.last_updated_text);
        backButton = view.findViewById(R.id.back_button);
        searchEditText = view.findViewById(R.id.search_edit_text);

        setupRecyclerView();
        setupSwipeRefresh();
        setupFilterChips();
        setupBackButton();
        setupSearchBar();

        // Load cached events first, then refresh if needed
        loadCachedEvents();

        return view;
    }

    private void setupRecyclerView() {
        // Main events list
        adapter = new EventHubAdapter();
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        eventsRecyclerView.setAdapter(adapter);

        // Featured events - Removed for now
        // featuredAdapter = new FeaturedEventAdapter();
        // featuredRecyclerView.setLayoutManager(new
        // LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL,
        // false));
        // featuredRecyclerView.setAdapter(featuredAdapter);

        // Club filters
        categoryAdapter = new EventCategoryAdapter();
        clubsRecyclerView
                .setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        clubsRecyclerView.setAdapter(categoryAdapter);

        // Initialize with empty list and listener
        categoryAdapter.setCategories(new ArrayList<>(), category -> {
            if (category.id.equals("0")) {
                currentClubFilter = "0";
                // If "All" is selected, we might want to refetch the main page "all" events
                // OR just filter if we still have them?
                // Since this is a network app, let's treat "All" as "Fetch Home".
                fetchEventsFromWeb();
            } else {
                currentClubFilter = category.id;
                fetchEventsForClub(category);
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::fetchEventsFromWeb);
        swipeRefreshLayout.setColorSchemeResources(
                R.color.colorPrimary,
                R.color.instructional_indicator,
                R.color.event_indicator);
    }

    private void setupFilterChips() {
        filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentFilter = "all";
            } else {
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chip_all) {
                    currentFilter = "all";
                } else if (checkedId == R.id.chip_free) {
                    currentFilter = "free";
                } else if (checkedId == R.id.chip_upcoming) {
                    currentFilter = "upcoming";
                } else if (checkedId == R.id.chip_vitian) {
                    currentFilter = "vitian";
                }
            }
            applyFilter();
        });
    }

    private void setupBackButton() {
        backButton.setOnClickListener(v -> {
            // Go back to previous fragment
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });
    }

    private void setupSearchBar() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim().toLowerCase();
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void loadCachedEvents() {
        Log.d(TAG, "loadCachedEvents() called - will fetch fresh data");
        // Always fetch fresh data from web first, then fall back to cache if needed
        fetchEventsFromWeb();
    }

    private void checkCacheAndRefresh() {
        Log.d(TAG, "checkCacheAndRefresh() called");
        // Not used anymore - we always fetch fresh
        fetchEventsFromWeb();
    }

    private void fetchEventsFromWeb() {
        Log.d(TAG, "fetchEventsFromWeb() - Starting fetch process...");
        if (!isAdded() || getContext() == null)
            return;
        swipeRefreshLayout.setRefreshing(true);

        // Clear old cached data first
        Log.d(TAG, "Clearing old cached data...");
        disposables.add(
                AppDatabase.getInstance(getContext())
                        .eventHubEventDao()
                        .deleteAll()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                            Log.d(TAG, "Old cache cleared successfully, fetching fresh data...");
                            // Old cache cleared, now fetch fresh data
                            fetchFreshEvents();
                        }, error -> {
                            Log.e(TAG, "Failed to clear cache: " + error.getMessage());
                            // Even if delete fails, try to fetch
                            fetchFreshEvents();
                        }));
    }

    private void fetchFreshEvents() {
        Log.d(TAG, "fetchFreshEvents() - Starting web scrape from: " + EVENTHUB_URL);
        disposables.add(
                Single.fromCallable(() -> scrapeEventHub())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> {
                            if (!isAdded() || getContext() == null)
                                return;
                            Log.d(TAG, "Scrape completed! Found " + result.events.size() + " events");
                            swipeRefreshLayout.setRefreshing(false);

                            if (result.clubs != null && !result.clubs.isEmpty()) {
                                availableClubs = result.clubs;
                                categoryAdapter.setCategories(availableClubs, category -> {
                                    if (category.id.equals("0")) {
                                        currentClubFilter = "0";
                                        fetchEventsFromWeb();
                                    } else {
                                        currentClubFilter = category.id;
                                        fetchEventsForClub(category);
                                    }
                                });
                            }

                            if (!result.events.isEmpty()) {
                                // Save to database
                                saveEventsToDatabase(result.events);
                                allEvents = result.events;
                                applyFilter();
                                updateLastUpdatedText(System.currentTimeMillis());
                                Toast.makeText(getContext(),
                                        "Found " + result.events.size() + " events", Toast.LENGTH_SHORT).show();
                            } else {
                                Log.w(TAG, "No events found - showing empty state");
                                allEvents = new ArrayList<>();
                                applyFilter();
                                showEmptyState(true);
                                Toast.makeText(getContext(),
                                        "No events found on EventHub.", Toast.LENGTH_SHORT).show();
                            }
                        }, error -> {
                            if (!isAdded() || getContext() == null)
                                return;
                            Log.e(TAG, "Error during scrape: " + error.getClass().getSimpleName() + " - "
                                    + error.getMessage());
                            swipeRefreshLayout.setRefreshing(false);
                            // Don't clear list on error, maybe keep old?
                            // showEmptyState(true);
                            String errorMsg = error.getMessage();
                            Toast.makeText(getContext(), "Error: " + errorMsg, Toast.LENGTH_SHORT).show();
                            error.printStackTrace();
                        }));
    }

    private void fetchEventsForClub(EventClub club) {
        if (!isAdded() || getContext() == null)
            return;
        swipeRefreshLayout.setRefreshing(true);
        disposables.add(
                Single.fromCallable(() -> {
                    Log.d(TAG, "Fetching events for club: " + club.name + " (" + club.id + ")");
                    SSLSocketFactory sslSocketFactory = createTrustAllSSLSocketFactory();
                    Document doc = Jsoup.connect(EVENTHUB_URL + "getByClubId")
                            .timeout(15000)
                            .userAgent("Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36")
                            .sslSocketFactory(sslSocketFactory)
                            .ignoreHttpErrors(true)
                            .data("id", club.id)
                            .post();

                    return parseEventsFromDoc(doc);
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(events -> {
                            if (!isAdded() || getContext() == null)
                                return;
                            swipeRefreshLayout.setRefreshing(false);
                            Log.d(TAG, "Found " + events.size() + " events for club " + club.name);
                            allEvents = events;
                            // Since these are club specific, we do not filter by "clubName" locally
                            // But we still apply Search/Type filters
                            applyFilter();

                            if (events.isEmpty()) {
                                Toast.makeText(getContext(), "No events for " + club.name, Toast.LENGTH_SHORT).show();
                            }
                        }, error -> {
                            if (!isAdded() || getContext() == null)
                                return;
                            swipeRefreshLayout.setRefreshing(false);
                            Toast.makeText(getContext(), "Error fetching club events", Toast.LENGTH_SHORT).show();
                            error.printStackTrace();
                        }));
    }

    private List<EventHubEvent> parseEventsFromDoc(Document doc) {
        List<EventHubEvent> events = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        // Parse event cards
        Elements eventCards = doc.select("section#events .card, #events .card");
        if (eventCards.isEmpty())
            eventCards = doc.select(".col-lg-4 .card");
        if (eventCards.isEmpty())
            eventCards = doc.select(".card");

        for (Element card : eventCards) {
            try {
                // ... same parsing logic ...
                String title = card.select(".card-title span").text().trim();
                if (title.isEmpty())
                    title = card.select("h3.card-title").text().trim();
                if (title.isEmpty())
                    continue;

                String audienceType = "";
                if (card.select(".fa-user-check").size() > 0)
                    audienceType = "VITian";
                else if (card.select(".fa-people-carry-box").size() > 0)
                    audienceType = "VITian/Non-VITian";
                else if (card.select(".fa-user-large").size() > 0)
                    audienceType = "Non-VITian";

                String subCategory = "";
                for (Element div : card.select("div")) {
                    String text = div.text().trim();
                    if (text.startsWith("(") && text.endsWith(")")) {
                        subCategory = text;
                        break;
                    }
                }
                String category = audienceType + (subCategory.isEmpty() ? "" : " " + subCategory);

                String date = "";
                Element dateIcon = card.selectFirst(".fa-calendar-days");
                if (dateIcon != null && dateIcon.parent() != null)
                    date = dateIcon.parent().text().trim(); // Parent likely has text directly too or in span

                String venue = "";
                Element venueIcon = card.selectFirst(".fa-map-location-dot");
                if (venueIcon != null && venueIcon.parent() != null)
                    venue = venueIcon.parent().text().trim();

                String fee = "";
                Element feeIcon = card.selectFirst(".fa-indian-rupee-sign");
                if (feeIcon != null && feeIcon.parent() != null)
                    fee = feeIcon.parent().text().trim();

                String teamSize = "1";
                // Minimal re-implementation for brevity as logic is complex
                // Assuming standard icons

                String eventId = "";
                Element viewButton = card.selectFirst("button[name=eid]");
                if (viewButton != null)
                    eventId = viewButton.attr("value");
                String eventUrl = eventId.isEmpty() ? EVENTHUB_URL : EVENTHUB_URL + "eventPreview?eid=" + eventId;

                String clubName = ""; // We don't know it from the card usually

                String uniqueKey = title + "|" + category + "|" + date;
                if (seenKeys.contains(uniqueKey))
                    continue;
                seenKeys.add(uniqueKey);

                EventHubEvent event = new EventHubEvent(title, category, date, venue, fee, teamSize, eventUrl,
                        clubName);
                if (event.isUpcoming()) {
                    events.add(event);
                }
            } catch (Exception e) {
                // ignore
            }
        }

        java.util.Collections.sort(events, (e1, e2) -> {
            if (e1.date == null)
                return 1;
            if (e2.date == null)
                return -1;
            return e1.date.compareTo(e2.date);
        });

        return events;
    }

    private ScrapeResult scrapeEventHub() throws Exception {
        Log.d(TAG, "scrapeEventHub() - Starting to parse HTML...");

        SSLSocketFactory sslSocketFactory = createTrustAllSSLSocketFactory();
        Log.d(TAG, "SSL socket factory created, connecting to: " + EVENTHUB_URL);

        Document doc = Jsoup.connect(EVENTHUB_URL)
                .timeout(15000)
                .userAgent("Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36")
                .sslSocketFactory(sslSocketFactory)
                .ignoreHttpErrors(true)
                .get();

        Log.d(TAG, "HTML fetched! Document title: " + doc.title());

        // Parse Events
        List<EventHubEvent> events = parseEventsFromDoc(doc);

        // Parse Clubs
        List<EventClub> clubs = new ArrayList<>();
        Elements options = doc.select("select#id option");
        for (Element opt : options) {
            String val = opt.attr("value");
            String txt = opt.text();
            if (val != null && !val.equals("0") && !txt.equals("Select")) {
                clubs.add(new EventClub(val, txt));
            }
        }
        Log.d(TAG, "Parsed " + clubs.size() + " clubs.");

        return new ScrapeResult(events, clubs);
    }

    private void saveEventsToDatabase(List<EventHubEvent> events) {
        if (!isAdded() || getContext() == null)
            return;
        disposables.add(
                AppDatabase.getInstance(getContext())
                        .eventHubEventDao()
                        .deleteAll()
                        .andThen(AppDatabase.getInstance(getContext())
                                .eventHubEventDao()
                                .insertAll(events))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                        }, error -> {
                        }));
    }

    private void applyFilter() {
        // Update Featured (Top 5 Upcoming) - Disabled
        /*
         * if (allEvents.size() > 0) {
         * List<EventHubEvent> featured = new ArrayList<>();
         * int count = 0;
         * for (EventHubEvent e : allEvents) {
         * if (e.isUpcoming()) {
         * featured.add(e);
         * count++;
         * if (count >= 5) break;
         * }
         * }
         * featuredAdapter.setEvents(featured);
         * }
         */

        List<EventHubEvent> filtered = new ArrayList<>();

        // First apply Main Filter (Free/Upcoming/VITian)
        List<EventHubEvent> step1Filtered;
        switch (currentFilter) {
            case "free":
                step1Filtered = new ArrayList<>();
                for (EventHubEvent event : allEvents) {
                    if (event.isFree)
                        step1Filtered.add(event);
                }
                break;
            case "upcoming":
                step1Filtered = new ArrayList<>();
                for (EventHubEvent event : allEvents) {
                    if (event.isUpcoming())
                        step1Filtered.add(event);
                }
                break;
            case "vitian":
                step1Filtered = new ArrayList<>();
                for (EventHubEvent event : allEvents) {
                    if (event.category != null &&
                            event.category.contains("VITian") &&
                            !event.category.contains("Non-VITian")) {
                        step1Filtered.add(event);
                    }
                }
                break;
            default:
                step1Filtered = new ArrayList<>(allEvents);
        }

        // Skip separate Club Filter step because currentClubFilter is handled by
        // fetching logic.
        // We assume 'allEvents' already reflects the selected club context if any.
        List<EventHubEvent> step2Filtered = step1Filtered;

        // Then apply search filter
        if (searchQuery.isEmpty()) {
            filtered = step2Filtered;
        } else {
            for (EventHubEvent event : step2Filtered) {
                boolean matches = false;
                if (event.title != null && event.title.toLowerCase().contains(searchQuery)) {
                    matches = true;
                } else if (event.category != null && event.category.toLowerCase().contains(searchQuery)) {
                    matches = true;
                } else if (event.venue != null && event.venue.toLowerCase().contains(searchQuery)) {
                    matches = true;
                } else if (event.clubName != null && event.clubName.toLowerCase().contains(searchQuery)) {
                    matches = true;
                }
                if (matches) {
                    filtered.add(event);
                }
            }
        }

        adapter.setEvents(filtered);
        // Show empty state if list is empty
        showEmptyState(filtered.isEmpty());
    }

    private void showEmptyState(boolean show) {
        emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        eventsRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateLastUpdatedText(long timestamp) {
        if (lastUpdatedText != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, hh:mm a",
                    java.util.Locale.ENGLISH);
            String formatted = sdf.format(new java.util.Date(timestamp));
            lastUpdatedText.setText("Last updated: " + formatted);
            lastUpdatedText.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Creates an SSLSocketFactory that trusts all certificates.
     * This is needed for VIT's EventHub which may use a certificate
     * not trusted by default on all Android devices.
     */
    private SSLSocketFactory createTrustAllSSLSocketFactory() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[] {};
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL socket factory", e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }
}
