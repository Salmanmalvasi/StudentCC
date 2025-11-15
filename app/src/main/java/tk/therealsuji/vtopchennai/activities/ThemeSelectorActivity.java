package tk.therealsuji.vtopchennai.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.ThemeAdapter;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.models.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ThemeSelectorActivity extends AppCompatActivity implements ThemeAdapter.OnThemeClickListener {

    private RecyclerView themeRecyclerView;
    private ThemeAdapter themeAdapter;
    private List<Theme> themes;
    private SwitchMaterial materialYouSwitch;
    private SwitchMaterial amoledSwitch;
    private MaterialCardView materialYouCard;
    private TextView materialYouDescription;
    private ImageView materialYouIcon;
    private ImageView amoledIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_selector);

        setupToolbar();
        setupMaterialYouSwitch();
        setupAmoledSwitch();
        setupThemes();
        setupRecyclerView();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.select_theme);
        }
    }

    private void setupMaterialYouSwitch() {
        materialYouCard = findViewById(R.id.material_you_card);
        materialYouSwitch = findViewById(R.id.material_you_switch);
        materialYouDescription = findViewById(R.id.material_you_description);
        materialYouIcon = findViewById(R.id.material_you_icon);

        // Check if Material You is available (Android 12+)
        boolean isAvailable = SettingsRepository.isMaterialYouAvailable();

        if (!isAvailable) {
            // Disable Material You on older Android versions
            materialYouSwitch.setEnabled(false);
            materialYouIcon.setEnabled(false);
            materialYouDescription.setText("Requires Android 12 or higher");
        }

        // Set initial state
        materialYouSwitch.setChecked(SettingsRepository.isMaterialYouEnabled(this));

        // Add listener
        materialYouSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsRepository.setMaterialYouEnabled(this, isChecked);

            // Enable/disable color themes when Material You is toggled
            updateThemeAvailability(isChecked);

            Toast.makeText(this, isChecked ? "Material You enabled" : "Material You disabled", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupAmoledSwitch() {
        amoledSwitch = findViewById(R.id.amoled_switch);
        amoledIcon = findViewById(R.id.amoled_icon);

        // Set initial state
        amoledSwitch.setChecked(SettingsRepository.isAmoledModeEnabled(this));

        // Add listener
        amoledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsRepository.setAmoledModeEnabled(this, isChecked);
            Toast.makeText(this, isChecked ? "AMOLED mode enabled" : "AMOLED mode disabled", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateThemeAvailability(boolean materialYouEnabled) {
        // When Material You is enabled, show a message that color themes are overridden
        // The themes can still be selected but won't fully apply due to dynamic colors
        if (themeAdapter != null) {
            themeAdapter.notifyDataSetChanged();
        }
    }

    private void setupThemes() {
        // Populate available themes
        themes = Arrays.asList(
            new Theme(
                "Default",
                "Clean white interface with dark text",
                R.style.Theme_VTOP,
                R.color.colorPrimary,
                R.color.colorPrimaryContainer
            ),
            new Theme(
                "Red",
                "Energetic and attention-grabbing",
                R.style.Theme_VTOP_Red,
                R.color.colorPrimaryRed,
                R.color.colorPrimaryContainerRed
            ),
            new Theme(
                "Blue",
                "Professional and trustworthy",
                R.style.Theme_VTOP_Blue,
                R.color.colorPrimaryBlue,
                R.color.colorPrimaryContainerBlue
            ),
            new Theme(
                "Purple",
                "Creative and modern",
                R.style.Theme_VTOP_Purple,
                R.color.colorPrimaryPurple,
                R.color.colorPrimaryContainerPurple
            ),
            new Theme(
                "Green",
                "Fresh and positive",
                R.style.Theme_VTOP_Green,
                R.color.colorPrimaryGreen,
                R.color.colorPrimaryContainerGreen
            )
        );
    }

    private void setupRecyclerView() {
        themeRecyclerView = findViewById(R.id.theme_recycler_view);
        themeAdapter = new ThemeAdapter(themes, this);
        themeRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        themeRecyclerView.setAdapter(themeAdapter);
    }

    @Override
    public void onThemeClick(Theme theme) {
        onThemeSelected(theme);
    }

    private void onThemeSelected(Theme theme) {
        SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Persist selection
        switch (theme.getName()) {
            case "Red":
                editor.putInt("selectedTheme", 1);
                break;
            case "Blue":
                editor.putInt("selectedTheme", 2);
                break;
            case "Purple":
                editor.putInt("selectedTheme", 3);
                break;
            case "Green":
                editor.putInt("selectedTheme", 4);
                break;
            case "Black":
                editor.putInt("selectedTheme", 5);
                break;
            default:
                editor.putInt("selectedTheme", 0);
                break;
        }
        editor.apply();

        // Restart to apply theme
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
