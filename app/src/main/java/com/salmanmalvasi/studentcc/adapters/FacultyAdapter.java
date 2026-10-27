package com.salmanmalvasi.studentcc.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.color.MaterialColors;

import java.util.List;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.FacultyFavoritesStore;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import com.salmanmalvasi.studentcc.models.Faculty;

public class FacultyAdapter extends RecyclerView.Adapter<FacultyAdapter.FacultyViewHolder> {

    private List<Faculty> facultyList;
    private final OnFavoriteToggleListener favoriteToggleListener;

    public FacultyAdapter(List<Faculty> facultyList, OnFavoriteToggleListener favoriteToggleListener) {
        this.facultyList = facultyList;
        this.favoriteToggleListener = favoriteToggleListener;
    }

    @NonNull
    @Override
    public FacultyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_faculty, parent, false);
        return new FacultyViewHolder(view, favoriteToggleListener);
    }

    @Override
    public void onBindViewHolder(@NonNull FacultyViewHolder holder, int position) {
        Faculty faculty = facultyList.get(position);
        holder.bind(faculty);
    }

    @Override
    public int getItemCount() {
        return facultyList.size();
    }

    public interface OnFavoriteToggleListener {
        void onFavoriteToggled(@NonNull Faculty faculty, boolean isFavorite);
    }

    static class FacultyViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView nameTextView;
        private final TextView designationTextView;
        private final TextView schoolTextView;
        private final TextView locationTextView;
        private final ImageButton favoriteButton;
        private final OnFavoriteToggleListener favoriteToggleListener;
        private Faculty currentFaculty;

        public FacultyViewHolder(@NonNull View itemView, OnFavoriteToggleListener favoriteToggleListener) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            nameTextView = itemView.findViewById(R.id.text_view_faculty_name);
            designationTextView = itemView.findViewById(R.id.text_view_faculty_designation);
            schoolTextView = itemView.findViewById(R.id.text_view_faculty_school);
            locationTextView = itemView.findViewById(R.id.text_view_faculty_location);
            favoriteButton = itemView.findViewById(R.id.button_favorite);
            this.favoriteToggleListener = favoriteToggleListener;
        }

        public void bind(Faculty faculty) {
            this.currentFaculty = faculty;
            nameTextView.setText(faculty.getEmpName());
            designationTextView.setText(faculty.getEmpDesignation());
            
            // Show school abbreviation if available, otherwise full name
            String schoolDisplay = faculty.getEmpSchoolAbbr();
            if (schoolDisplay == null || schoolDisplay.isEmpty() || schoolDisplay.equals("SELECT")) {
                schoolDisplay = faculty.getEmpSchool();
            }
            schoolTextView.setText(schoolDisplay);

            // Show location info
            String building = faculty.getEmpLocationBuilding();
            String cabin = faculty.getEmpLocationCabin();
            
            if ((building == null || building.equals("Not Available")) && 
                (cabin == null || cabin.equals("Not Available"))) {
                locationTextView.setText("Location: Not Available");
                locationTextView.setVisibility(View.VISIBLE);
            } else if (building != null && !building.equals("Not Available")) {
                if (cabin != null && !cabin.equals("Not Available")) {
                    locationTextView.setText("📍 " + building + ", " + cabin);
                } else {
                    locationTextView.setText("📍 " + building);
                }
                locationTextView.setVisibility(View.VISIBLE);
            } else {
                locationTextView.setVisibility(View.GONE);
            }

            updateFavoriteUI(itemView.getContext(), faculty);

            favoriteButton.setOnClickListener(v -> toggleFavorite());

            // Click listener to show full details
            cardView.setOnClickListener(v -> showFullDetails(faculty));
        }

        private void toggleFavorite() {
            Context context = itemView.getContext();
            boolean isFavorite = FacultyFavoritesStore.toggleFavorite(context, currentFaculty);
            updateFavoriteUI(context, currentFaculty);
            if (favoriteToggleListener != null) {
                favoriteToggleListener.onFavoriteToggled(currentFaculty, isFavorite);
            }
        }

        private void updateFavoriteUI(Context context, Faculty faculty) {
            boolean isFavorite = FacultyFavoritesStore.isFavorite(context, faculty);
            favoriteButton.setImageResource(isFavorite ? R.drawable.ic_star_24 : R.drawable.ic_star_border_24);

            int activeColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimary);
            int inactiveColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant);
            favoriteButton.setImageTintList(ColorStateList.valueOf(isFavorite ? activeColor : inactiveColor));

            cardView.setStrokeWidth(isFavorite ? (int) (context.getResources().getDisplayMetrics().density * 2) : 0);
            cardView.setStrokeColor(isFavorite ? activeColor : 0);
        }

        private void showFullDetails(Faculty faculty) {
            String building = faculty.getEmpLocationBuilding();
            String cabin = faculty.getEmpLocationCabin();
            String location = "";
            
            if (building != null && !building.equals("Not Available")) {
                location += "Building: " + building + "\n";
            }
            if (cabin != null && !cabin.equals("Not Available")) {
                location += "Cabin: " + cabin + "\n";
            }
            if (location.isEmpty()) {
                location = "Location: Not Available\n";
            }

        String disclaimer = itemView.getContext().getString(R.string.message_faculty_details_disclaimer);

        String baseMessage = disclaimer + "\n\n" +
               "Name: " + faculty.getEmpName() + "\n\n" +
                           "Employee ID: " + (faculty.getEmpId().isEmpty() ? "N/A" : faculty.getEmpId()) + "\n\n" +
                           "Designation: " + faculty.getEmpDesignation() + "\n\n" +
                           "School: " + faculty.getEmpSchool() + "\n\n" +
                           location;

        Context context = itemView.getContext();
        boolean isFavorite = FacultyFavoritesStore.isFavorite(context, faculty);
        String favoriteAction = context.getString(isFavorite ? R.string.action_remove_favorite : R.string.action_add_favorite);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (24 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, 0);

        android.widget.TextView messageView = new android.widget.TextView(context);
        messageView.setText(baseMessage);
        messageView.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.BLACK));
        messageView.setTextSize(16);
        layout.addView(messageView);

        com.google.android.material.button.MaterialButton getReviewsBtn = new com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        getReviewsBtn.setText("Get Reviews");
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = (int) (16 * context.getResources().getDisplayMetrics().density);
        getReviewsBtn.setLayoutParams(params);
        layout.addView(getReviewsBtn);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(itemView.getContext())
                .setTitle("Faculty Details")
                .setView(layout)
                .setPositiveButton(R.string.action_close, null)
                .setNegativeButton(R.string.action_incorrect, (d, which) ->
                    SettingsRepository.openBrowser(context, "https://forms.office.com/r/srm3hzJN05"))
                .setNeutralButton(favoriteAction, (d, which) -> toggleFavorite())
                .show();

        final io.reactivex.rxjava3.disposables.CompositeDisposable disposables = new io.reactivex.rxjava3.disposables.CompositeDisposable();

        getReviewsBtn.setOnClickListener(v -> {
            getReviewsBtn.setEnabled(false);
            getReviewsBtn.setText("Loading reviews...");
            
            String base64EmpId = android.util.Base64.encodeToString(faculty.getEmpId().getBytes(), android.util.Base64.NO_WRAP);
            
            io.reactivex.rxjava3.disposables.Disposable disposable = io.reactivex.rxjava3.core.Single.fromCallable(() -> {
                String apiUrl = "https://vitfac-cache.vit-fac.workers.dev/profile/" + java.net.URLEncoder.encode(base64EmpId, "UTF-8");
                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    org.json.JSONObject json = new org.json.JSONObject(response.toString());
                    if (json.has("data") && !json.isNull("data")) {
                        org.json.JSONObject data = json.getJSONObject("data");
                        if (data.has("prof") && !data.isNull("prof")) {
                            org.json.JSONObject prof = data.getJSONObject("prof");
                            int reviews = prof.optInt("reviews", 0);
                            if (reviews == 0) {
                                return "No reviews found.";
                            }
                            
                            StringBuilder sb = new StringBuilder();
                            sb.append("Total Reviews: ").append(reviews).append("\n\n");
                            
                            if (prof.has("metrics") && !prof.isNull("metrics")) {
                                org.json.JSONObject metrics = prof.getJSONObject("metrics");
                                sb.append("Lecture: ").append(metrics.optString("lecture", "N/A")).append("/5\n");
                                sb.append("DA & Quiz: ").append(metrics.optString("da", "N/A")).append("/5\n");
                                sb.append("Assignment: ").append(metrics.optString("assign", "N/A")).append("/5\n");
                                sb.append("Vibe: ").append(metrics.optString("vibe", "N/A")).append("/5\n");
                            }
                            
                            return sb.toString().trim();
                        }
                    }
                }
                return "No reviews available.";
            })
            .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
            .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
            .subscribe(
                reviewText -> {
                    if (dialog.isShowing()) {
                        messageView.setText(baseMessage + "\n\n--- Reviews ---\n" + reviewText);
                        getReviewsBtn.setVisibility(View.GONE);
                    }
                },
                error -> {
                    if (dialog.isShowing()) {
                        getReviewsBtn.setEnabled(true);
                        getReviewsBtn.setText("Get Reviews");
                        messageView.setText(baseMessage + "\n\nFailed to load reviews. Try again.");
                    }
                }
            );
            
            disposables.add(disposable);
        });

        dialog.setOnDismissListener(d -> {
            if (!disposables.isDisposed()) {
                disposables.dispose();
            }
        });
    }
    }
}
