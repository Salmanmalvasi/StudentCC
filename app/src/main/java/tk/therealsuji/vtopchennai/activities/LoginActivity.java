package tk.therealsuji.vtopchennai.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.button.MaterialButton;



import org.json.JSONObject;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import tk.therealsuji.vtopchennai.BuildConfig;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.fragments.dialogs.UpdateDialogFragment;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.helpers.VTOPHelper;

public class LoginActivity extends AppCompatActivity {
    SharedPreferences encryptedSharedPreferences, sharedPreferences;
    CompositeDisposable compositeDisposable = new CompositeDisposable();
    VTOPHelper vtopHelper;
    
    // Student type and hostel selection
    private String studentType = ""; // "day_scholar" or "hosteller"
    private String gender = ""; // "male" or "female"
    private String selectedBlock = ""; // "A", "B", "CB", "CG", "D1", "D2", "E"
    private String messType = ""; // "V", "N", "S" (Vegetarian, Non-Veg, Special)
    private String roomNumber = ""; // User's room number
    
    // UI Elements
    private MaterialButton buttonDayScholar, buttonHosteller;
    private MaterialButton buttonMale, buttonFemale;
    private MaterialButton buttonVeg, buttonNonVeg, buttonSpecial;
    private MaterialButton buttonD1, buttonD2, buttonA, buttonC, buttonE, buttonCB, buttonCG;
    private LinearLayout layoutHostelSelection, layoutRoomNumber, layoutMessTypeSelection, layoutBlockSelection;
    private LinearLayout layoutMaleBlocks, layoutFemaleBlocks;
    private EditText editTextRoomNumber;

    public void signIn() {
        hideKeyboard(this.getCurrentFocus());

        EditText usernameView = findViewById(R.id.edit_text_username);
        EditText passwordView = findViewById(R.id.edit_text_password);

        String username = usernameView.getText().toString();
        String password = passwordView.getText().toString();

        // Save login credentials
        encryptedSharedPreferences.edit().putString("username", username).apply();
        encryptedSharedPreferences.edit().putString("password", password).apply();
        
        // Save hostel information if hosteller
        if ("hosteller".equals(studentType)) {
            roomNumber = editTextRoomNumber.getText().toString();
            encryptedSharedPreferences.edit().putString("student_type", studentType).apply();
            encryptedSharedPreferences.edit().putString("gender", gender).apply();
            encryptedSharedPreferences.edit().putString("hostel_block", selectedBlock).apply();
            encryptedSharedPreferences.edit().putString("mess_type", messType).apply();
            encryptedSharedPreferences.edit().putString("room_number", roomNumber).apply();
        } else {
            encryptedSharedPreferences.edit().putString("student_type", "day_scholar").apply();
        }

        this.vtopHelper.bind();
        this.vtopHelper.start();
    }
    
    private void initializeViews() {
        // Student type buttons
        buttonDayScholar = findViewById(R.id.button_day_scholar);
        buttonHosteller = findViewById(R.id.button_hosteller);
        
        // Gender buttons
        buttonMale = findViewById(R.id.button_male);
        buttonFemale = findViewById(R.id.button_female);
        
        // Mess type buttons
        buttonVeg = findViewById(R.id.button_veg);
        buttonNonVeg = findViewById(R.id.button_non_veg);
        buttonSpecial = findViewById(R.id.button_special);
        
        // Block buttons
        buttonD1 = findViewById(R.id.button_d1_block);
        buttonD2 = findViewById(R.id.button_d2_block);
        buttonA = findViewById(R.id.button_a_block);
        buttonC = findViewById(R.id.button_c_block);
        buttonE = findViewById(R.id.button_e_block);
        buttonCB = findViewById(R.id.button_cb_block);
        buttonCG = findViewById(R.id.button_cg_block);
        
        // Layouts
        layoutHostelSelection = findViewById(R.id.layout_hostel_selection);
        layoutRoomNumber = findViewById(R.id.layout_room_number);
        layoutMessTypeSelection = findViewById(R.id.layout_mess_type_selection);
        layoutBlockSelection = findViewById(R.id.layout_block_selection);
        layoutMaleBlocks = findViewById(R.id.layout_male_blocks);
        layoutFemaleBlocks = findViewById(R.id.layout_female_blocks);
        
        // EditText
        editTextRoomNumber = findViewById(R.id.edit_text_room_number);
    }
    
    private void setupClickListeners() {
        // Student type selection
        buttonDayScholar.setOnClickListener(v -> selectStudentType("day_scholar"));
        buttonHosteller.setOnClickListener(v -> selectStudentType("hosteller"));
        
        // Gender selection
        buttonMale.setOnClickListener(v -> selectGender("male"));
        buttonFemale.setOnClickListener(v -> selectGender("female"));
        
        // Mess type selection
        buttonVeg.setOnClickListener(v -> selectMessType("V"));
        buttonNonVeg.setOnClickListener(v -> selectMessType("N"));
        buttonSpecial.setOnClickListener(v -> selectMessType("S"));
        
        // Block selection
        buttonD1.setOnClickListener(v -> selectBlock("D1"));
        buttonD2.setOnClickListener(v -> selectBlock("D2"));
        buttonA.setOnClickListener(v -> selectBlock("A"));
        buttonC.setOnClickListener(v -> selectBlock("C"));
        buttonE.setOnClickListener(v -> selectBlock("E"));
        buttonCB.setOnClickListener(v -> selectBlock("CB"));
        buttonCG.setOnClickListener(v -> selectBlock("CG"));
    }
    
    private void selectStudentType(String type) {
        studentType = type;
        
        // Update button states with visual feedback
        if ("day_scholar".equals(type)) {
            buttonDayScholar.setSelected(true);
            buttonDayScholar.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            buttonHosteller.setSelected(false);
            buttonHosteller.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            layoutHostelSelection.setVisibility(View.GONE);
        } else {
            buttonDayScholar.setSelected(false);
            buttonDayScholar.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            buttonHosteller.setSelected(true);
            buttonHosteller.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            layoutHostelSelection.setVisibility(View.VISIBLE);
        }
    }
    
    private void selectGender(String selectedGender) {
        gender = selectedGender;
        
        // Update button states with visual feedback
        if ("male".equals(selectedGender)) {
            buttonMale.setSelected(true);
            buttonMale.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            buttonFemale.setSelected(false);
            buttonFemale.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            layoutMaleBlocks.setVisibility(View.VISIBLE);
            layoutFemaleBlocks.setVisibility(View.GONE);
        } else {
            buttonMale.setSelected(false);
            buttonMale.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            buttonFemale.setSelected(true);
            buttonFemale.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            layoutMaleBlocks.setVisibility(View.GONE);
            layoutFemaleBlocks.setVisibility(View.VISIBLE);
        }
        
        layoutMessTypeSelection.setVisibility(View.VISIBLE);
    }
    
    private void selectMessType(String selectedMessType) {
        messType = selectedMessType;
        
        // Update button states with visual feedback
        buttonVeg.setSelected("V".equals(selectedMessType));
        buttonVeg.setBackgroundColor("V".equals(selectedMessType) ? 
            getResources().getColor(android.R.color.holo_blue_light) : 
            getResources().getColor(android.R.color.transparent));
        
        buttonNonVeg.setSelected("N".equals(selectedMessType));
        buttonNonVeg.setBackgroundColor("N".equals(selectedMessType) ? 
            getResources().getColor(android.R.color.holo_blue_light) : 
            getResources().getColor(android.R.color.transparent));
        
        buttonSpecial.setSelected("S".equals(selectedMessType));
        buttonSpecial.setBackgroundColor("S".equals(selectedMessType) ? 
            getResources().getColor(android.R.color.holo_blue_light) : 
            getResources().getColor(android.R.color.transparent));
        
        layoutBlockSelection.setVisibility(View.VISIBLE);
    }
    
    private void selectBlock(String block) {
        selectedBlock = block;
        
        // Update all block button states with visual feedback
        updateBlockButton(buttonD1, "D1".equals(block));
        updateBlockButton(buttonD2, "D2".equals(block));
        updateBlockButton(buttonA, "A".equals(block));
        updateBlockButton(buttonC, "C".equals(block));
        updateBlockButton(buttonE, "E".equals(block));
        updateBlockButton(buttonCB, "CB".equals(block));
        updateBlockButton(buttonCG, "CG".equals(block));
        
        // Show room number input after block selection
        layoutRoomNumber.setVisibility(View.VISIBLE);
    }
    
    private void updateBlockButton(MaterialButton button, boolean isSelected) {
        button.setSelected(isSelected);
        button.setBackgroundColor(isSelected ? 
            getResources().getColor(android.R.color.holo_blue_light) : 
            getResources().getColor(android.R.color.transparent));
    }

    private void hideKeyboard(View view) {
        if (view != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        this.encryptedSharedPreferences = SettingsRepository.getEncryptedSharedPreferences(getApplicationContext());
        this.sharedPreferences = SettingsRepository.getSharedPreferences(getApplicationContext());

        initializeViews();
        setupClickListeners();

        findViewById(R.id.button_sign_in).setOnClickListener(view -> signIn());
        findViewById(R.id.button_privacy).setOnClickListener(view -> SettingsRepository.openWebViewActivity(
                this,
                getString(R.string.privacy),
                SettingsRepository.APP_PRIVACY_URL
        ));

        this.vtopHelper = new VTOPHelper(this, new VTOPHelper.Initiator() {
            @Override
            public void onLoading(boolean isLoading) {
                if (isLoading) {
                    findViewById(R.id.progress_bar_loading).setVisibility(View.VISIBLE);

                    findViewById(R.id.button_sign_in).setEnabled(false);
                    findViewById(R.id.edit_text_username).setEnabled(false);
                    findViewById(R.id.edit_text_password).setEnabled(false);
                } else {
                    findViewById(R.id.progress_bar_loading).setVisibility(View.INVISIBLE);

                    findViewById(R.id.button_sign_in).setEnabled(true);
                    findViewById(R.id.edit_text_username).setEnabled(true);
                    findViewById(R.id.edit_text_password).setEnabled(true);
                }
            }

            @Override
            public void onForceSignOut() {
                // User is already signed out so do nothing
            }

            @Override
            public void onComplete() {
                startMainActivity();
            }
        });

        /*
            Check for updates
         */
        SettingsRepository.fetchAboutJson(true)
                .subscribe(new Observer<JSONObject>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(@NonNull JSONObject about) {
                        try {
                            int versionCode = about.getInt("versionCode");
                            String versionName = about.getString("tagName");
                            String releaseNotes = about.getString("releaseNotes");

                            if (versionCode > BuildConfig.VERSION_CODE) {
                                FragmentManager fragmentManager = getSupportFragmentManager();
                                FragmentTransaction transaction = fragmentManager.beginTransaction();
                                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                                transaction.add(android.R.id.content, UpdateDialogFragment.newInstance(versionName, releaseNotes)).addToBackStack(null).commit();
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        this.vtopHelper.bind();

        if (!this.vtopHelper.isBound() && this.sharedPreferences.getBoolean("isSignedIn", false)) {
            this.startMainActivity();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();


    }

    @Override
    protected void onStop() {
        super.onStop();
        this.vtopHelper.unbind();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
    }
}
