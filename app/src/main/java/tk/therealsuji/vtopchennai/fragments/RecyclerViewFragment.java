package tk.therealsuji.vtopchennai.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.analytics.FirebaseAnalytics;



import java.util.List;
import java.util.ArrayList;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.EmptyStateAdapter;
import tk.therealsuji.vtopchennai.adapters.ReceiptsItemAdapter;
import tk.therealsuji.vtopchennai.adapters.SpotlightGroupAdapter;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.interfaces.ReceiptsDao;
import tk.therealsuji.vtopchennai.interfaces.SpotlightDao;
import tk.therealsuji.vtopchennai.models.Receipt;
import tk.therealsuji.vtopchennai.models.Spotlight;

public class RecyclerViewFragment extends Fragment {
    public static final int TYPE_RECEIPTS = 1;
    public static final int TYPE_SPOTLIGHT = 2;

    int contentType;

    AppDatabase appDatabase;
    RecyclerView recyclerView;
    private FirebaseAnalytics mFirebaseAnalytics;

    public RecyclerViewFragment() {
        // Required empty public constructor
    }

    private void attachReceipts() {
        ReceiptsDao receiptsDao = this.appDatabase.receiptsDao();

        receiptsDao
                .getReceipts()
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<List<Receipt>>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onSuccess(@NonNull List<Receipt> receipts) {
                        if (receipts.size() == 0) {
                            displayEmptyState(EmptyStateAdapter.TYPE_NO_DATA, null);
                            return;
                        }

                        recyclerView.setAdapter(new ReceiptsItemAdapter(receipts));
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        displayEmptyState(EmptyStateAdapter.TYPE_ERROR, "Error: " + e.getLocalizedMessage());
                    }
                });
    }

    private void attachSpotlight() {
        // Create custom spotlight items for the coming soon feature
        List<Spotlight> comingSoonSpotlight = new java.util.ArrayList<>();

        // Main announcement about the new events feature
        Spotlight mainAnnouncement = new Spotlight();
        mainAnnouncement.id = 1;
        mainAnnouncement.announcement = "🎉 Events & Announcements Coming Soon!\n\nStudentCC is expanding beyond VTOP to become your central hub for ALL VIT campus events!";
        mainAnnouncement.category = "App Updates";
        mainAnnouncement.link = null;
        mainAnnouncement.isRead = true;

        // Club participation announcement
        Spotlight clubAnnouncement = new Spotlight();
        clubAnnouncement.id = 2;
        clubAnnouncement.announcement = "📢 Attention Club Leaders!\n\nWant your club events showcased here? Each club will get access to upload event details (date, time, poster, etc.) and reach all students instantly.\n\nNo more dependency on WhatsApp groups or Instagram posts - get FREE promotion with wider reach!";
        clubAnnouncement.category = "For Clubs";
        clubAnnouncement.link = null;
        clubAnnouncement.isRead = true;

        // Contact information
        Spotlight contactAnnouncement = new Spotlight();
        contactAnnouncement.id = 3;
        contactAnnouncement.announcement = "📱 Interested in joining this initiative?\n\nFill out this form to get your club onboarded for this exciting feature!";
        contactAnnouncement.category = "Contact";
        contactAnnouncement.link = "https://forms.gle/jvrj9iTEM6aXbn9EA";
        contactAnnouncement.isRead = true;

        comingSoonSpotlight.add(mainAnnouncement);
        comingSoonSpotlight.add(clubAnnouncement);
        comingSoonSpotlight.add(contactAnnouncement);

        // Display the custom announcements
        recyclerView.setAdapter(new SpotlightGroupAdapter(comingSoonSpotlight));

        // Track that user viewed the coming soon feature
        if (mFirebaseAnalytics != null) {
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "events_coming_soon");
            bundle.putString("feature_type", "club_events_preview");
            mFirebaseAnalytics.logEvent("view_coming_soon_events", bundle);
        }

        // Original VTOP spotlight code (commented out for future reference)
        /*
        SpotlightDao spotlightDao = this.appDatabase.spotlightDao();
        spotlightDao.get()
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<List<Spotlight>>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onSuccess(@NonNull List<Spotlight> spotlight) {
                        if (spotlight.size() == 0) {
                            displayEmptyState(EmptyStateAdapter.TYPE_NO_DATA, null);
                            return;
                        }

                        recyclerView.setAdapter(new SpotlightGroupAdapter(spotlight));
                        spotlightDao.setRead()
                                .subscribeOn(Schedulers.single())
                                .subscribe();
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        displayEmptyState(EmptyStateAdapter.TYPE_ERROR, "Error: " + e.getLocalizedMessage());
                    }
                });
        */
    }

    private void displayEmptyState(int type, String message) {
        this.recyclerView.setAdapter(new EmptyStateAdapter(type, message));
        this.recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bottomNavigationVisibility = new Bundle();
        bottomNavigationVisibility.putBoolean("isVisible", false);
        getParentFragmentManager().setFragmentResult("bottomNavigationVisibility", bottomNavigationVisibility);
    }

    @Override
    public void onResume() {
        super.onResume();

        String screenName = "RecyclerView Fragment";
        Bundle arguments = this.getArguments();

        if (arguments != null) {
            int contentType = arguments.getInt("content_type", 0);
            switch (contentType) {
                case TYPE_RECEIPTS:
                    screenName = "Receipts";
                    break;
                case TYPE_SPOTLIGHT:
                    screenName = "Spotlight";
                    break;
            }
        }


    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View recyclerViewFragment = inflater.inflate(R.layout.fragment_recycler_view, container, false);
        recyclerViewFragment.getRootView().setBackgroundColor(requireContext().getColor(R.color.secondary_container_95));
        recyclerViewFragment.getRootView().setOnTouchListener((view, motionEvent) -> true);

        View header = recyclerViewFragment.findViewById(R.id.linear_layout_header);
        this.recyclerView = recyclerViewFragment.findViewById(R.id.recycler_view);
        this.appDatabase = AppDatabase.getInstance(this.requireActivity().getApplicationContext());
        this.mFirebaseAnalytics = FirebaseAnalytics.getInstance(requireContext());

        getParentFragmentManager().setFragmentResultListener("customInsets2", this, (requestKey, result) -> {
            int systemWindowInsetLeft = result.getInt("systemWindowInsetLeft");
            int systemWindowInsetTop = result.getInt("systemWindowInsetTop");
            int systemWindowInsetRight = result.getInt("systemWindowInsetRight");
            int systemWindowInsetBottom = result.getInt("systemWindowInsetBottom");
            float pixelDensity = getResources().getDisplayMetrics().density;

            header.setPaddingRelative(
                    systemWindowInsetLeft,
                    systemWindowInsetTop,
                    systemWindowInsetRight,
                    0
            );

            this.recyclerView.setPaddingRelative(
                    systemWindowInsetLeft,
                    0,
                    systemWindowInsetRight,
                    (int) (systemWindowInsetBottom + 20 * pixelDensity)
            );
        });

        int titleId = 0;
        Bundle arguments = this.getArguments();

        if (arguments != null) {
            titleId = arguments.getInt("title_id", 0);
            this.contentType = arguments.getInt("content_type", 0);
        }

        recyclerViewFragment.findViewById(R.id.image_button_back).setOnClickListener(view -> requireActivity().getSupportFragmentManager().popBackStack());
        ((TextView) recyclerViewFragment.findViewById(R.id.text_view_title)).setText(getString(titleId));

        switch (this.contentType) {
            case TYPE_RECEIPTS:
                this.attachReceipts();
                break;
            case TYPE_SPOTLIGHT:
                this.attachSpotlight();
                break;
        }

        return recyclerViewFragment;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        Bundle bottomNavigationVisibility = new Bundle();
        bottomNavigationVisibility.putBoolean("isVisible", true);
        getParentFragmentManager().setFragmentResult("bottomNavigationVisibility", bottomNavigationVisibility);

        if (this.contentType == TYPE_SPOTLIGHT) {
            getParentFragmentManager().setFragmentResult("getUnreadCount", new Bundle());
        }
    }
}
