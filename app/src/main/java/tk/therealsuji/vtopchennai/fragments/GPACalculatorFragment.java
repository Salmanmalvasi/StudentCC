package tk.therealsuji.vtopchennai.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.GPACourseAdapter;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.models.GPACourse;

public class GPACalculatorFragment extends Fragment implements GPACourseAdapter.OnCourseDeleteListener {
    private TextInputEditText creditHoursInput;
    private AutoCompleteTextView gradeDropdown;
    private MaterialButton addCourseButton;
    private MaterialButton calculateGPAButton;
    private MaterialButton clearAllButton;
    private MaterialButton calculateCGPAButton;
    private RecyclerView coursesRecyclerView;
    private LinearLayout noCoursesText;
    private TextView creditsBeingAddedText;
    private TextInputEditText currentCGPAInput;
    private TextInputEditText currentCreditsInput;
    private TextView cgpaResultText;

    // Current CGPA and Credits Display
    private TextView currentCGPADisplay;
    private TextView currentCreditsDisplay;

    // CGPA Estimator UI elements
    private TextInputEditText targetCGPAInput;
    private TextInputEditText semesterCreditsInput;
    private MaterialButton estimateCGPAButton;
    private TextView estimateResultText;

    // CGPA Predictor UI elements
    private TextInputEditText expectedGPAInput;
    private TextInputEditText semesterCreditsPredictorInput;
    private MaterialButton predictCGPAButton;
    private TextView predictionResultText;

    private GPACourseAdapter courseAdapter;
    private List<GPACourse> courses;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gpa_calculator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupRecyclerView();
        setupGradeDropdown();
        setupClickListeners();
        autoFillCurrentData();
        updateUI();
    }

    private void initializeViews(View view) {
        creditHoursInput = view.findViewById(R.id.credit_hours_input);
        gradeDropdown = view.findViewById(R.id.grade_dropdown);
        addCourseButton = view.findViewById(R.id.add_course_button);
        calculateGPAButton = view.findViewById(R.id.calculate_gpa_button);
        clearAllButton = view.findViewById(R.id.clear_all_button);
        calculateCGPAButton = view.findViewById(R.id.calculate_cgpa_button);
        coursesRecyclerView = view.findViewById(R.id.courses_recycler_view);
        noCoursesText = view.findViewById(R.id.no_courses_text);
        creditsBeingAddedText = view.findViewById(R.id.credits_being_added_text);
        currentCGPAInput = view.findViewById(R.id.current_cgpa_input);
        currentCreditsInput = view.findViewById(R.id.current_credits_input);
        cgpaResultText = view.findViewById(R.id.cgpa_result_text);

        // Initialize Current CGPA and Credits Display
        currentCGPADisplay = view.findViewById(R.id.current_cgpa_display);
        currentCreditsDisplay = view.findViewById(R.id.current_credits_display);

        // Initialize CGPA Estimator UI elements
        targetCGPAInput = view.findViewById(R.id.target_cgpa_input);
        semesterCreditsInput = view.findViewById(R.id.semester_credits_input);
        estimateCGPAButton = view.findViewById(R.id.estimate_cgpa_button);
        estimateResultText = view.findViewById(R.id.estimate_result_text);

        // Initialize CGPA Predictor UI elements
        expectedGPAInput = view.findViewById(R.id.expected_gpa_input);
        semesterCreditsPredictorInput = view.findViewById(R.id.semester_credits_predictor_input);
        predictCGPAButton = view.findViewById(R.id.predict_cgpa_button);
        predictionResultText = view.findViewById(R.id.prediction_result_text);

        courses = new ArrayList<>();
    }

    private void setupRecyclerView() {
        courseAdapter = new GPACourseAdapter();
        courseAdapter.setOnCourseDeleteListener(this);
        coursesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        coursesRecyclerView.setAdapter(courseAdapter);
    }

    private void setupGradeDropdown() {
        String[] grades = GPACourse.getAvailableGrades();
        String[] gradeDisplayTexts = new String[grades.length];

        for (int i = 0; i < grades.length; i++) {
            gradeDisplayTexts[i] = GPACourse.getGradeDisplayText(grades[i]);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                gradeDisplayTexts
        );
        gradeDropdown.setAdapter(adapter);
    }

    private void setupClickListeners() {
        addCourseButton.setOnClickListener(v -> addCourse());
        calculateGPAButton.setOnClickListener(v -> calculateGPA());
        clearAllButton.setOnClickListener(v -> clearAll());
        calculateCGPAButton.setOnClickListener(v -> calculateCGPA());
        estimateCGPAButton.setOnClickListener(v -> estimateRequiredGPA());
        predictCGPAButton.setOnClickListener(v -> predictNewCGPA());
    }

    private void autoFillCurrentData() {
        SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(requireContext());

        // Auto-fill current CGPA
        float currentCGPA = sharedPreferences.getFloat("cgpa", 0);
        if (currentCGPA > 0) {
            currentCGPAInput.setText(String.format("%.2f", currentCGPA));
            currentCGPADisplay.setText(getString(R.string.current_cgpa_display, currentCGPA));
        } else {
            currentCGPADisplay.setText("Current CGPA: --");
        }

        // Auto-fill current credits
        float totalCredits;
        try {
            // Support old integer based credits
            totalCredits = sharedPreferences.getInt("totalCredits", 0);
        } catch (Exception ignored) {
            totalCredits = sharedPreferences.getFloat("totalCredits", 0);
        }

        if (totalCredits > 0) {
            currentCreditsInput.setText(String.format("%.1f", totalCredits));
            currentCreditsDisplay.setText(getString(R.string.current_credits_display, totalCredits));
        } else {
            currentCreditsDisplay.setText("Total Credits: --");
        }
    }

    private void addCourse() {
        String creditHoursStr = creditHoursInput.getText() != null ? creditHoursInput.getText().toString().trim() : "";
        String grade = gradeDropdown.getText() != null ? gradeDropdown.getText().toString().trim() : "";

        // Validate inputs
        if (TextUtils.isEmpty(creditHoursStr)) {
            creditHoursInput.setError("Credit hours is required");
            return;
        }

        if (TextUtils.isEmpty(grade)) {
            gradeDropdown.setError("Grade is required");
            return;
        }

        double creditHours;
        try {
            creditHours = Double.parseDouble(creditHoursStr);
            if (creditHours <= 0) {
                creditHoursInput.setError("Credit hours must be greater than 0");
                return;
            }
        } catch (NumberFormatException e) {
            creditHoursInput.setError("Invalid credit hours");
            return;
        }

        // Extract grade from display text (e.g., "A (9.0)" -> "A")
        String actualGrade = grade.split(" ")[0];

        // Create and add course
        GPACourse course = new GPACourse(creditHours, actualGrade);
        courseAdapter.addCourse(course);
        courses.add(course);

        // Clear inputs
        creditHoursInput.setText("");
        gradeDropdown.setText("");

        updateUI();
    }

    private void calculateGPA() {
        if (courses.isEmpty()) {
            Toast.makeText(requireContext(), R.string.add_at_least_one_course, Toast.LENGTH_SHORT).show();
            return;
        }

        double totalGradePoints = 0;
        double totalCredits = 0;

        for (GPACourse course : courses) {
            totalGradePoints += course.getCourseGPA();
            totalCredits += course.getCreditHours();
        }

        double semesterGPA = totalCredits > 0 ? totalGradePoints / totalCredits : 0;

        // Show result in a toast
        DecimalFormat df = new DecimalFormat("#.##");
        String result = "Semester GPA: " + df.format(semesterGPA) + "\nTotal Credits: " + (int) totalCredits;
        Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show();
    }

    private void clearAll() {
        try {
            courses.clear();
            courseAdapter.clearCourses();
            updateUI();

            // Clear result texts
            if (cgpaResultText != null) {
                cgpaResultText.setVisibility(View.GONE);
            }
            if (estimateResultText != null) {
                estimateResultText.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void calculateCGPA() {
        String currentCGPAStr = currentCGPAInput.getText() != null ? currentCGPAInput.getText().toString().trim() : "";
        String currentCreditsStr = currentCreditsInput.getText() != null ? currentCreditsInput.getText().toString().trim() : "";

        if (TextUtils.isEmpty(currentCGPAStr) || TextUtils.isEmpty(currentCreditsStr)) {
            Toast.makeText(requireContext(), R.string.enter_valid_values, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double currentCGPA = Double.parseDouble(currentCGPAStr);
            double currentCredits = Double.parseDouble(currentCreditsStr);

            if (currentCGPA < 0 || currentCGPA > 10 || currentCredits <= 0) {
                Toast.makeText(requireContext(), R.string.enter_valid_values, Toast.LENGTH_SHORT).show();
                return;
            }

            // Calculate new semester GPA
            double semesterGPA = 0;
            double semesterCredits = 0;

            for (GPACourse course : courses) {
                semesterGPA += course.getCourseGPA();
                semesterCredits += course.getCreditHours();
            }

            if (semesterCredits > 0) {
                semesterGPA = semesterGPA / semesterCredits;
            }

            // Calculate new CGPA using the formula: (Current CGPA * Current Credits + Semester GPA * Semester Credits) / (Current Credits + Semester Credits)
            double newCGPA = ((currentCGPA * currentCredits) + (semesterGPA * semesterCredits)) / (currentCredits + semesterCredits);

            DecimalFormat df = new DecimalFormat("#.##");
            cgpaResultText.setText(getString(R.string.new_cgpa, df.format(newCGPA)));
            cgpaResultText.setVisibility(View.VISIBLE);

        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), R.string.enter_valid_values, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI() {
        if (courses.isEmpty()) {
            noCoursesText.setVisibility(View.VISIBLE);
            coursesRecyclerView.setVisibility(View.GONE);
        } else {
            noCoursesText.setVisibility(View.GONE);
            coursesRecyclerView.setVisibility(View.VISIBLE);
        }
        updateCreditsDisplay();
    }

    private void updateCreditsDisplay() {
        double totalCredits = 0;
        for (GPACourse course : courses) {
            totalCredits += course.getCreditHours();
        }

        if (totalCredits > 0) {
            creditsBeingAddedText.setText(getString(R.string.credits_being_added, totalCredits));
            creditsBeingAddedText.setVisibility(View.VISIBLE);
        } else {
            creditsBeingAddedText.setVisibility(View.GONE);
        }
    }

    /**
     * Estimate the required GPA to achieve target CGPA
     */
    private void estimateRequiredGPA() {
        String targetCGPAStr = targetCGPAInput.getText() != null ? targetCGPAInput.getText().toString().trim() : "";
        String semesterCreditsStr = semesterCreditsInput.getText() != null ? semesterCreditsInput.getText().toString().trim() : "";
        String currentCGPAStr = currentCGPAInput.getText() != null ? currentCGPAInput.getText().toString().trim() : "";
        String currentCreditsStr = currentCreditsInput.getText() != null ? currentCreditsInput.getText().toString().trim() : "";

        if (TextUtils.isEmpty(targetCGPAStr) || TextUtils.isEmpty(semesterCreditsStr) ||
            TextUtils.isEmpty(currentCGPAStr) || TextUtils.isEmpty(currentCreditsStr)) {
            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double targetCGPA = Double.parseDouble(targetCGPAStr);
            double semesterCredits = Double.parseDouble(semesterCreditsStr);
            double currentCGPA = Double.parseDouble(currentCGPAStr);
            double currentCredits = Double.parseDouble(currentCreditsStr);

            if (targetCGPA < 0 || targetCGPA > 10 || semesterCredits <= 0 ||
                currentCGPA < 0 || currentCGPA > 10 || currentCredits <= 0) {
                Toast.makeText(requireContext(), "Please enter valid values", Toast.LENGTH_SHORT).show();
                return;
            }

            // Calculate required GPA using the formula:
            // Required GPA = ((Target CGPA * (Current Credits + Semester Credits)) - (Current CGPA * Current Credits)) / Semester Credits
            double totalCredits = currentCredits + semesterCredits;
            double requiredGPA = ((targetCGPA * totalCredits) - (currentCGPA * currentCredits)) / semesterCredits;

            DecimalFormat df = new DecimalFormat("#.##");
            String result;

            if (requiredGPA < 0) {
                result = "Target CGPA is not achievable with current credits";
            } else if (requiredGPA > 10) {
                result = "Target CGPA requires GPA > 10 (not possible)";
            } else {
                result = "Required GPA: " + df.format(requiredGPA) + "\n" +
                        "Target CGPA: " + df.format(targetCGPA) + "\n" +
                        "Current CGPA: " + df.format(currentCGPA);
            }

            estimateResultText.setText(result);
            estimateResultText.setVisibility(View.VISIBLE);

            // Log to Firebase Analytics (commented out for now)
            // FirebaseHelper.getInstance().logEvent("cgpa_estimation", "target_cgpa", df.format(targetCGPA));
            // FirebaseHelper.getInstance().logEvent("cgpa_estimation", "required_gpa", df.format(requiredGPA));

        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Please enter valid numbers", Toast.LENGTH_SHORT).show();
        }
    }

    private void predictNewCGPA() {
        try {
            String currentCGPAStr = currentCGPAInput.getText().toString().trim();
            String currentCreditsStr = currentCreditsInput.getText().toString().trim();
            String expectedGPAStr = expectedGPAInput.getText().toString().trim();
            String semesterCreditsStr = semesterCreditsPredictorInput.getText().toString().trim();

            if (TextUtils.isEmpty(currentCGPAStr) || TextUtils.isEmpty(currentCreditsStr) ||
                TextUtils.isEmpty(expectedGPAStr) || TextUtils.isEmpty(semesterCreditsStr)) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            double currentCGPA = Double.parseDouble(currentCGPAStr);
            double currentCredits = Double.parseDouble(currentCreditsStr);
            double expectedGPA = Double.parseDouble(expectedGPAStr);
            double semesterCredits = Double.parseDouble(semesterCreditsStr);

            // Validate inputs
            if (currentCGPA < 0 || currentCGPA > 10) {
                Toast.makeText(requireContext(), "Current CGPA must be between 0 and 10", Toast.LENGTH_SHORT).show();
                return;
            }
            if (expectedGPA < 0 || expectedGPA > 10) {
                Toast.makeText(requireContext(), "Expected GPA must be between 0 and 10", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentCredits <= 0 || semesterCredits <= 0) {
                Toast.makeText(requireContext(), "Credits must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }

            // Calculate new CGPA
            // New CGPA = (Current CGPA × Current Credits + Expected GPA × Semester Credits) / (Current Credits + Semester Credits)
            double totalPoints = (currentCGPA * currentCredits) + (expectedGPA * semesterCredits);
            double totalCredits = currentCredits + semesterCredits;
            double newCGPA = totalPoints / totalCredits;

            DecimalFormat df = new DecimalFormat("#.##");
            String result = "New CGPA: " + df.format(newCGPA) + "\n" +
                          "Current CGPA: " + df.format(currentCGPA) + "\n" +
                          "Expected GPA: " + df.format(expectedGPA) + "\n" +
                          "New Total Credits: " + df.format(totalCredits);

            // Add color coding based on improvement
            String changeText;
            if (newCGPA > currentCGPA) {
                double improvement = newCGPA - currentCGPA;
                changeText = "\n✅ Improvement: +" + df.format(improvement);
            } else if (newCGPA < currentCGPA) {
                double decrease = currentCGPA - newCGPA;
                changeText = "\n⚠️ Decrease: -" + df.format(decrease);
            } else {
                changeText = "\n➡️ No change";
            }

            predictionResultText.setText(result + changeText);
            predictionResultText.setVisibility(View.VISIBLE);

        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Please enter valid numbers", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCourseDelete(int position) {
        if (position >= 0 && position < courses.size()) {
            courseAdapter.removeCourse(position);
            courses = courseAdapter.getCourses();
            updateUI();
        }
    }
}
