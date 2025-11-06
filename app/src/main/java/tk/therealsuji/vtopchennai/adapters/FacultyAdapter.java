package tk.therealsuji.vtopchennai.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.models.Faculty;

public class FacultyAdapter extends RecyclerView.Adapter<FacultyAdapter.FacultyViewHolder> {

    private List<Faculty> facultyList;

    public FacultyAdapter(List<Faculty> facultyList) {
        this.facultyList = facultyList;
    }

    @NonNull
    @Override
    public FacultyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_faculty, parent, false);
        return new FacultyViewHolder(view);
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

    static class FacultyViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView nameTextView;
        private final TextView designationTextView;
        private final TextView schoolTextView;
        private final TextView locationTextView;

        public FacultyViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            nameTextView = itemView.findViewById(R.id.text_view_faculty_name);
            designationTextView = itemView.findViewById(R.id.text_view_faculty_designation);
            schoolTextView = itemView.findViewById(R.id.text_view_faculty_school);
            locationTextView = itemView.findViewById(R.id.text_view_faculty_location);
        }

        public void bind(Faculty faculty) {
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

            // Click listener to show full details
            cardView.setOnClickListener(v -> showFullDetails(faculty));
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

            String message = "Name: " + faculty.getEmpName() + "\n\n" +
                           "Employee ID: " + (faculty.getEmpId().isEmpty() ? "N/A" : faculty.getEmpId()) + "\n\n" +
                           "Designation: " + faculty.getEmpDesignation() + "\n\n" +
                           "School: " + faculty.getEmpSchool() + "\n\n" +
                           location;

            new MaterialAlertDialogBuilder(itemView.getContext())
                    .setTitle("Faculty Details")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }
}
