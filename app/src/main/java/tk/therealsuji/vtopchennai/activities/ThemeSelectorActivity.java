package tk.therealsuji.vtopchennai.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.ThemeAdapter;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.helpers.ThemeManager;
import tk.therealsuji.vtopchennai.models.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ThemeSelectorActivity extends AppCompatActivity implements ThemeAdapter.OnThemeClickListener {

    private RecyclerView themeRecyclerView;
    private ThemeAdapter themeAdapter;
    private List<Theme> themes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before setting content view
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_selector);

        setupToolbar();
        setupThemes();
        setupRecyclerView();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(""); // Title is in the layout now
        }
    }

    private void setupThemes() {
        // Populate available themes
        themes = Arrays.asList(
                new Theme(
                        ThemeManager.THEME_DEFAULT,
                        "Clean white interface with dark text",
                        R.style.Theme_VTOP,
                        R.color.colorPrimary,
                        R.color.colorPrimaryContainer),
                new Theme(
                        ThemeManager.THEME_SUNSET,
                        "Energetic and attention-grabbing",
                        R.style.Theme_VTOP_Red,
                        R.color.colorPrimaryRed,
                        R.color.colorPrimaryContainerRed),
                new Theme(
                        ThemeManager.THEME_OCEAN,
                        "Professional and trustworthy",
                        R.style.Theme_VTOP_Blue,
                        R.color.colorPrimaryBlue,
                        R.color.colorPrimaryContainerBlue),
                new Theme(
                        ThemeManager.THEME_PURPLE,
                        "Creative and modern",
                        R.style.Theme_VTOP_Purple,
                        R.color.colorPrimaryPurple,
                        R.color.colorPrimaryContainerPurple),
                new Theme(
                        ThemeManager.THEME_FOREST,
                        "Fresh and positive",
                        R.style.Theme_VTOP_Green,
                        R.color.colorPrimaryGreen,
                        R.color.colorPrimaryContainerGreen),
                new Theme(
                        ThemeManager.THEME_MIDNIGHT,
                        "Dark and elegant",
                        R.style.Theme_VTOP_Black,
                        R.color.colorPrimaryBlack,
                        R.color.colorSecondaryBlack));
    }

    private void setupRecyclerView() {
        themeRecyclerView = findViewById(R.id.theme_recycler_view);
        String currentTheme = ThemeManager.getCurrentTheme(this);
        themeAdapter = new ThemeAdapter(themes, currentTheme, this);
        themeRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        themeRecyclerView.setAdapter(themeAdapter);
    }

    @Override
    public void onThemeClick(Theme theme) {
        onThemeSelected(theme);
    }

    private void onThemeSelected(Theme theme) {
        // Persist selection using ThemeManager
        ThemeManager.saveTheme(this, theme.getName());

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
