package com.salmanmalvasi.studentcc.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.content.res.Configuration;
import androidx.core.app.NotificationCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.button.MaterialButton;



import org.json.JSONObject;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import com.salmanmalvasi.studentcc.BuildConfig;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.fragments.dialogs.UpdateDialogFragment;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import com.salmanmalvasi.studentcc.helpers.VTOPHelper;
import com.salmanmalvasi.studentcc.helpers.HostelDataHelper;

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
    private MaterialButton buttonVeg, buttonNonVeg, buttonSpecial, buttonOthers;
    private MaterialButton buttonD1, buttonD2, buttonA, buttonC, buttonE, buttonCB, buttonCG;
    private LinearLayout layoutHostelSelection, layoutRoomNumber, layoutMessTypeSelection, layoutBlockSelection;
    private LinearLayout layoutMaleBlocks, layoutFemaleBlocks;
    private EditText editTextRoomNumber;

    public void signIn() {
        hideKeyboard(this.getCurrentFocus());

        EditText usernameView = findViewById(R.id.edit_text_username);
        EditText passwordView = findViewById(R.id.edit_text_password);

        String username = usernameView != null && usernameView.getText() != null ? usernameView.getText().toString().trim() : "";
        String password = passwordView != null && passwordView.getText() != null ? passwordView.getText().toString().trim() : "";

        if (username.isEmpty()) {
            if (usernameView != null) usernameView.setError("Registration number is required");
            return;
        }
        if (password.isEmpty()) {
            if (passwordView != null) passwordView.setError("Password is required");
            return;
        }

        // Save login credentials
        encryptedSharedPreferences.edit().putString("username", username).apply();
        encryptedSharedPreferences.edit().putString("password", password).apply();

        // Save hostel information if hosteller
        if ("hosteller".equals(studentType)) {
            if (editTextRoomNumber != null && editTextRoomNumber.getText() != null) {
                roomNumber = editTextRoomNumber.getText().toString().trim();
            }
            encryptedSharedPreferences.edit().putString("student_type", studentType).apply();
            encryptedSharedPreferences.edit().putString("gender", gender).apply();
            encryptedSharedPreferences.edit().putString("hostel_block", selectedBlock).apply();
            encryptedSharedPreferences.edit().putString("mess_type", messType).apply();
            encryptedSharedPreferences.edit().putString("room_number", roomNumber).apply();
        } else {
            encryptedSharedPreferences.edit().putString("student_type", "day_scholar").apply();
        }

        // Mandate legacy WebView sign-in pipeline directly (no OTP)
        android.util.Log.i("LoginActivity", "Mandating legacy WebView sign-in pipeline (OTP disabled).");
        startWebViewSignIn();
    }

    private void startWebViewSignIn() {
        android.util.Log.i("LoginActivity", "Starting standard WebView sign-in fallback...");
        setLoadingState(true);
        scheduleLaundryAlarms();
        this.vtopHelper.bind();
        this.vtopHelper.start();
    }

    private void scheduleLaundryAlarms() {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent laundryIntent = new Intent(this, com.salmanmalvasi.studentcc.receivers.LaundryNotificationReceiver.class);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(this, 2011, laundryIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 8);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            long trigger = cal.getTimeInMillis();
            if (trigger < System.currentTimeMillis()) trigger += android.app.AlarmManager.INTERVAL_DAY;
            am.setInexactRepeating(android.app.AlarmManager.RTC_WAKEUP, trigger, android.app.AlarmManager.INTERVAL_DAY, pi);
        } catch (Exception ignored) {
        }
    }

    private void setLoadingState(boolean isLoading) {
        View progressBar = findViewById(R.id.progress_bar_loading);
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);
        }
        View btnSignIn = findViewById(R.id.button_sign_in);
        if (btnSignIn != null) {
            btnSignIn.setEnabled(!isLoading);
        }
        View edtUser = findViewById(R.id.edit_text_username);
        if (edtUser != null) {
            edtUser.setEnabled(!isLoading);
        }
        View edtPass = findViewById(R.id.edit_text_password);
        if (edtPass != null) {
            edtPass.setEnabled(!isLoading);
        }
    }

    private void onNativeAuthSuccess(com.salmanmalvasi.studentcc.sync.VTOPAuthClient.DiagnosticProofResult result, String username) {
        onNativeAuthSuccess(result, username, null);
    }

    private void onNativeAuthSuccess(
            com.salmanmalvasi.studentcc.sync.VTOPAuthClient.DiagnosticProofResult result,
            String username,
            com.salmanmalvasi.studentcc.sync.VTOPAuthClient.DiagnosticProofResult initialProof
    ) {
        setLoadingState(false);
        try {
            SharedPreferences.Editor encEditor = encryptedSharedPreferences.edit();
            encEditor.putString("username", username);

            String applNo = (result != null && result.applNo != null && !result.applNo.isEmpty())
                    ? result.applNo
                    : (initialProof != null ? initialProof.applNo : "");
            if (applNo != null && !applNo.isEmpty()) {
                encEditor.putString("applNo", applNo);
            }

            String apiKey = (result != null && result.apiKey != null && !result.apiKey.isEmpty())
                    ? result.apiKey
                    : (initialProof != null ? initialProof.apiKey : "");
            if (apiKey != null && !apiKey.isEmpty()) {
                encEditor.putString("vtop_api_key", apiKey);
            }

            String urlPath = (result != null && result.urlPath != null && !result.urlPath.isEmpty())
                    ? result.urlPath
                    : (initialProof != null ? initialProof.urlPath : "");
            if (urlPath != null && !urlPath.isEmpty() && !"null".equalsIgnoreCase(urlPath.trim())) {
                encEditor.putString("vtop_url_path", urlPath.trim());
            } else {
                encEditor.putString("vtop_url_path", "students");
            }
            encEditor.apply();

            sharedPreferences.edit()
                    .putBoolean("isVTOPSignedIn", true)
                    .putLong("lastRefreshed", System.currentTimeMillis())
                    .apply();

            scheduleLaundryAlarms();

            try {
                String student = encryptedSharedPreferences.getString("student_type", "");
                if ("hosteller".equals(student)) {
                    HostelDataHelper.getInstance(LoginActivity.this).syncHostelData();

                    String block = encryptedSharedPreferences.getString("hostel_block", "");
                    String room = encryptedSharedPreferences.getString("room_number", "");
                    int days = HostelDataHelper.getInstance(LoginActivity.this).getDaysUntilNextLaundry(block, room);
                    sendLaundryCountdownNotification(days, room);
                }
            } catch (Exception ignored) {
            }

            android.widget.Toast.makeText(this, "Signed in successfully!", android.widget.Toast.LENGTH_SHORT).show();
            startMainActivity();
        } catch (Exception e) {
            android.util.Log.e("LoginActivity", "Error finalizing native sign-in, falling back to WebView", e);
            startWebViewSignIn();
        }
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
        buttonOthers = findViewById(R.id.button_others);

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
        buttonOthers.setOnClickListener(v -> selectMessType("O"));

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

        // Theme-aware selection styling
        boolean isDayScholar = "day_scholar".equals(type);
        buttonDayScholar.setSelected(isDayScholar);
        buttonHosteller.setSelected(!isDayScholar);
        styleOutlinedSelection(buttonDayScholar, isDayScholar);
        styleOutlinedSelection(buttonHosteller, !isDayScholar);

        layoutHostelSelection.setVisibility(isDayScholar ? View.GONE : View.VISIBLE);
    }

    private void selectGender(String selectedGender) {
        gender = selectedGender;

        // Theme-aware selection styling
        boolean isMale = "male".equals(selectedGender);
        buttonMale.setSelected(isMale);
        buttonFemale.setSelected(!isMale);
        styleOutlinedSelection(buttonMale, isMale);
        styleOutlinedSelection(buttonFemale, !isMale);

        layoutMaleBlocks.setVisibility(isMale ? View.VISIBLE : View.GONE);
        layoutFemaleBlocks.setVisibility(isMale ? View.GONE : View.VISIBLE);

        layoutMessTypeSelection.setVisibility(View.VISIBLE);
    }

    private void selectMessType(String selectedMessType) {
        messType = selectedMessType;

        // Update button states with visual feedback
        buttonVeg.setSelected("V".equals(selectedMessType));
        boolean vegSel = "V".equals(selectedMessType);
        styleOutlinedSelection(buttonVeg, vegSel);

        buttonNonVeg.setSelected("N".equals(selectedMessType));
        boolean nonSel = "N".equals(selectedMessType);
        styleOutlinedSelection(buttonNonVeg, nonSel);

        buttonSpecial.setSelected("S".equals(selectedMessType));
        boolean spSel = "S".equals(selectedMessType);
        styleOutlinedSelection(buttonSpecial, spSel);

        if (buttonOthers != null) {
            boolean oSel = "O".equals(selectedMessType);
            buttonOthers.setSelected(oSel);
            styleOutlinedSelection(buttonOthers, oSel);
        }

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
        styleOutlinedSelection(button, isSelected);
    }

    private void styleOutlinedSelection(MaterialButton button, boolean selected) {
        // Theme-aware selection styling for good contrast in light/dark
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        int colorOnSurface = getAttrColor(com.google.android.material.R.attr.colorOnSurface);
        int colorSurface = getAttrColor(com.google.android.material.R.attr.colorSurface);

        int stroke = adjustAlpha(colorOnSurface, 200); // visible outline
        int unselectedText = adjustAlpha(colorOnSurface, 230);

        if (selected) {
            // Fill with contrasting surface/onSurface pair
            int fill = night ? colorOnSurface : colorOnSurface; // white in dark, black in light (our palette)
            int text = night ? colorSurface : colorSurface; // dark text when background is light
            button.setBackgroundTintList(ColorStateList.valueOf(fill));
            button.setStrokeColor(ColorStateList.valueOf(fill));
            button.setStrokeWidth(0);
            button.setTextColor(text);
            button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            button.setStrokeColor(ColorStateList.valueOf(stroke));
            button.setStrokeWidth(3);
            button.setTextColor(unselectedText);
            button.setTypeface(button.getTypeface(), android.graphics.Typeface.NORMAL);
        }
    }

    private int getAttrColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private int adjustAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void sendLaundryCountdownNotification(int days, String room) {
        final String CHANNEL_ID = "laundry_notifications";
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Laundry Notifications", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(channel);
        }
        String text;
        if (days == 0) {
            text = "Your laundry is scheduled today for room " + room;
        } else if (days > 0) {
            text = "Your laundry is in " + days + " day" + (days == 1 ? "" : "s") + " (Room " + room + ")";
        } else {
            // Unknown/NA
            text = "Laundry schedule info unavailable";
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.salmanmalvasi.studentcc.R.drawable.ic_update_available)
                .setContentTitle("Laundry Reminder")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        nm.notify(2012, builder.build());
    }

    private void hideKeyboard(View view) {
        if (view != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void startMainActivity() {
        com.salmanmalvasi.studentcc.workers.NightlySyncScheduler.schedule(this);
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

        try {
            String savedU = encryptedSharedPreferences.getString("username", "");
            String savedP = encryptedSharedPreferences.getString("password", "");
            if (!savedU.isEmpty()) {
                EditText uView = findViewById(R.id.edit_text_username);
                if (uView != null && (uView.getText() == null || uView.getText().toString().isEmpty())) {
                    uView.setText(savedU);
                }
            }
            if (!savedP.isEmpty()) {
                EditText pView = findViewById(R.id.edit_text_password);
                if (pView != null && (pView.getText() == null || pView.getText().toString().isEmpty())) {
                    pView.setText(savedP);
                }
            }
        } catch (Exception ignored) {}

        findViewById(R.id.button_sign_in).setOnClickListener(view -> signIn());
        findViewById(R.id.button_privacy_policy).setOnClickListener(view -> {
            SettingsRepository.openBrowser(this, SettingsRepository.APP_PRIVACY_URL);
        });

        if (BuildConfig.DEBUG) {
            View titleView = findViewById(R.id.text_view_title);
            if (titleView != null) {
                titleView.setOnLongClickListener(v -> {
                    EditText usernameView = findViewById(R.id.edit_text_username);
                    EditText passwordView = findViewById(R.id.edit_text_password);
                    String u = usernameView != null && usernameView.getText() != null ? usernameView.getText().toString().trim() : "";
                    String p = passwordView != null && passwordView.getText() != null ? passwordView.getText().toString().trim() : "";
                    if (u.isEmpty() || p.isEmpty()) {
                        android.widget.Toast.makeText(this, "Enter username and password first to test native auth", android.widget.Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    android.widget.Toast.makeText(this, "Testing isolated native REST auth...", android.widget.Toast.LENGTH_SHORT).show();
                    com.salmanmalvasi.studentcc.sync.VTOPAuthClient.executeControlledAuthProofAsync(getApplicationContext(), u, p, result -> {
                        runOnUiThread(() -> {
                            if ("OTP_REQUIRED".equals(result.failureStage)) {
                                showOtpVerificationDialog(u, result.diagnosticMessage, result);
                            } else {
                                String msg = result.authSucceeded
                                        ? "Auth OK (HTTP " + result.authHttpCode + "). Academic: " + (result.academicValidResponse ? "HTTP 200 SUCCESS" : "HTTP " + result.academicHttpCode + " " + result.failureStage)
                                        : "Auth FAILED (HTTP " + result.authHttpCode + ": " + result.failureStage + ")";
                                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                    return true;
                });
            }
        }

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
                // After full sync completes, sync hostel data and send laundry countdown notification as well
                try {
                    String student = encryptedSharedPreferences.getString("student_type", "");
                    if ("hosteller".equals(student)) {
                        // Sync hostel data (laundry and mess menu) after login
                        HostelDataHelper.getInstance(LoginActivity.this).syncHostelData();

                        String block = encryptedSharedPreferences.getString("hostel_block", "");
                        String room = encryptedSharedPreferences.getString("room_number", "");
                        int days = com.salmanmalvasi.studentcc.helpers.HostelDataHelper.getInstance(LoginActivity.this).getDaysUntilNextLaundry(block, room);
                        sendLaundryCountdownNotification(days, room);
                    }
                } catch (Exception ignored) {
                }

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

    private void showOtpVerificationDialog(String username, String otpMessage) {
        showOtpVerificationDialog(username, otpMessage, null);
    }

    private void showOtpVerificationDialog(
            String username,
            String otpMessage,
            com.salmanmalvasi.studentcc.sync.VTOPAuthClient.DiagnosticProofResult initialProof
    ) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("VTOP OTP Verification");
        builder.setMessage(otpMessage != null && !otpMessage.isEmpty() ? otpMessage : "Enter the 6-digit OTP sent to your registered mobile number.");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("6-digit OTP");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        builder.setView(input);

        builder.setPositiveButton("Verify & Sign In", (dialog, which) -> {
            String otp = input.getText() != null ? input.getText().toString().trim() : "";
            if (otp.isEmpty()) {
                android.widget.Toast.makeText(this, "OTP cannot be empty", android.widget.Toast.LENGTH_SHORT).show();
                showOtpVerificationDialog(username, "Please enter the 6-digit OTP to proceed.", initialProof);
                return;
            }
            setLoadingState(true);
            final String regNoUpper = username.trim().toUpperCase();
            com.salmanmalvasi.studentcc.sync.VTOPAuthClient.submitOtpAndVerifyAcademicAsync(
                    getApplicationContext(),
                    regNoUpper,
                    otp,
                    initialProof != null ? initialProof.authResult : null,
                    proofResult -> {
                        runOnUiThread(() -> {
                            boolean isOtpVerified = proofResult.academicValidResponse ||
                                    "SUCCESS".equals(proofResult.authStatus) ||
                                    (proofResult.authHttpCode == 200 && !"OTP_REJECTED".equals(proofResult.failureStage) && !"OTP_ERROR".equals(proofResult.failureStage));

                            if (isOtpVerified) {
                                onNativeAuthSuccess(proofResult, regNoUpper, initialProof);
                            } else {
                                setLoadingState(false);
                                android.widget.Toast.makeText(this, "OTP verification failed. Falling back to standard sign-in...", android.widget.Toast.LENGTH_LONG).show();
                                startWebViewSignIn();
                            }
                        });
                    });
        });
        builder.setNegativeButton("Cancel (Use Standard Login)", (dialog, which) -> {
            dialog.dismiss();
            setLoadingState(false);
            startWebViewSignIn();
        });
        builder.setCancelable(false);
        builder.show();
    }

}
