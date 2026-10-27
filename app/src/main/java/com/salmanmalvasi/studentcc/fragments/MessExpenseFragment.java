package com.salmanmalvasi.studentcc.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.adapters.MessExpenseAdapter;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.models.MessExpense;

public class MessExpenseFragment extends Fragment {

    private static final String PREF_NAME = "MessTrackerPrefs";
    private static final String KEY_MONTHLY_LIMIT = "monthly_limit";
    private static final String KEY_LAST_RESET_MONTH = "last_reset_month";
    private static final float DEFAULT_LIMIT = 7800f; // Default points

    private TextView balanceTextView;
    private TextView dailyLimitTextView;
    private RecyclerView recyclerView;
    private MessExpenseAdapter adapter;
    private AppDatabase database;
    private ExecutorService executorService;
    private SharedPreferences prefs;

    // Image Capture
    private ActivityResultLauncher<Void> takePictureLauncher;
    private ActivityResultLauncher<String> getContentLauncher; // Optional: Pick from gallery

    // Temporary URI logic would be needed for High Quality photo, but bitmap is
    // easier for POC
    // However, TakePicture returns boolean and saves to provided URI.
    // TakePicturePreview returns Bitmap (thumbnail).
    // I'll use TakePicturePreview for simplicity now, or better:
    // actually, let's use the simple thumbnail for parsing, usually enough for text
    // close up?
    // No, ML kit needs good quality.
    // I will use `ActivityResultContracts.TakePicturePreview` first to see if it
    // works,
    // but usually `TakePicture` with a file URI is safer.
    // Let's stick to `TakePicturePreview` for least friction implementation right
    // now (no file provider setup needed).
    private ActivityResultLauncher<Void> takePicturePreviewLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = AppDatabase.getInstance(requireContext());
        executorService = Executors.newSingleThreadExecutor();
        prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        takePicturePreviewLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null) {
                        processBillImage(bitmap);
                    }
                });

        getContentLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        try {
                            InputImage image = InputImage.fromFilePath(requireContext(), uri);
                            processInputImage(image);
                        } catch (IOException e) {
                            Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mess_tracker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        balanceTextView = view.findViewById(R.id.text_view_balance);
        dailyLimitTextView = view.findViewById(R.id.text_view_daily_limit);
        recyclerView = view.findViewById(R.id.recycler_view_expenses);
        MaterialButton scanButton = view.findViewById(R.id.button_scan_bill);
        MaterialButton uploadButton = view.findViewById(R.id.button_upload_bill);

        scanButton.setOnClickListener(v -> takePicturePreviewLauncher.launch(null));
        uploadButton.setOnClickListener(v -> getContentLauncher.launch("image/*"));

        MaterialButton overrideButton = view.findViewById(R.id.button_manual_override);
        overrideButton.setOnClickListener(v -> showManualOverrideDialog());

        FloatingActionButton fabAddManual = view.findViewById(R.id.fab_add_manual);
        fabAddManual.setOnClickListener(v -> showConfirmDialog(0, 0, ""));

        setupRecyclerView();
        checkMonthlyReset();
        loadData();

        view.findViewById(R.id.card_balance).setOnClickListener(v -> showSetLimitDialog());
    }

    private void showManualOverrideDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Manual Balance Override");
        builder.setMessage("Enter your current balance to override the tracker.");

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_mess_expense, null);
        EditText amountInput = view.findViewById(R.id.edit_text_amount);
        EditText balanceInput = view.findViewById(R.id.edit_text_balance);
        EditText itemsInput = view.findViewById(R.id.edit_text_items);

        amountInput.setVisibility(View.GONE);
        itemsInput.setVisibility(View.GONE);
        balanceInput.setHint("Current Balance");

        builder.setView(view);
        builder.setPositiveButton("Override", (dialog, which) -> {
            try {
                float newBalance = Float.parseFloat(balanceInput.getText().toString());
                saveExpense(0f, newBalance, "Manual Balance Override");
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Invalid number", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showSetLimitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Set Monthly Points Limit");

        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        float currentLimit = prefs.getFloat(KEY_MONTHLY_LIMIT, DEFAULT_LIMIT);
        input.setText(String.valueOf(currentLimit));

        builder.setView(input);
        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                float newLimit = Float.parseFloat(input.getText().toString());
                prefs.edit().putFloat(KEY_MONTHLY_LIMIT, newLimit).apply();
                loadData();
                Toast.makeText(requireContext(), "Monthly limit updated", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Invalid number", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void setupRecyclerView() {
        adapter = new MessExpenseAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void checkMonthlyReset() {
        // Logic: specific day of month (e.g. 1st)
        // Or just if current month != stored month
        Calendar calendar = Calendar.getInstance();
        int currentMonth = calendar.get(Calendar.MONTH);
        int lastMonth = prefs.getInt(KEY_LAST_RESET_MONTH, -1);

        if (lastMonth != -1 && lastMonth != currentMonth) {
            // New month!
            Toast.makeText(requireContext(), "New Month! Balance Allowance Reset.", Toast.LENGTH_LONG).show();
            // In a real app, we might archive old expenses or just keep them in DB.
            // Since we calculate balance dynamically from stored expenses + limit,
            // we really just need to know which expenses belong to *this* month.
            // Our query does that.
        }

        prefs.edit().putInt(KEY_LAST_RESET_MONTH, currentMonth).apply();
    }

    private void loadData() {
        executorService.execute(() -> {
            // Get start of month
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            long startOfMonth = calendar.getTimeInMillis();

            // Fetch expenses since start of month
            List<MessExpense> expenses = database.messExpenseDao()
                    .getExpensesInRange(startOfMonth, System.currentTimeMillis())
                    .blockingGet();

            // Calculate totals
            float limit = prefs.getFloat(KEY_MONTHLY_LIMIT, DEFAULT_LIMIT);
            float balance;

            MessExpense latestWithBalance = null;
            // Expenses are ordered DESC (newest first)
            for (MessExpense e : expenses) {
                if (e.balanceRemaining > 0) {
                    latestWithBalance = e;
                    break;
                }
            }

            if (latestWithBalance != null) {
                // Use the latest known balance from a bill
                balance = (float) latestWithBalance.balanceRemaining;

                // Subtract any expenses recorded *after* that bill (newer timestamps)
                for (MessExpense e : expenses) {
                    if (e.date > latestWithBalance.date) {
                        balance -= e.amount;
                    }
                }
            } else {
                // Fallback: Default limit minus all expenses
                float totalSpent = 0;
                for (MessExpense e : expenses) {
                    totalSpent += e.amount;
                }
                balance = limit - totalSpent;
            }

            // Calculate daily limit
            int totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
            int currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
            int daysRemaining = totalDays - currentDay + 1; // including today
            float dailyLimit = daysRemaining > 0 ? balance / daysRemaining : 0;

            float finalBalance = balance;
            float finalDailyLimit = dailyLimit;

            getActivity().runOnUiThread(() -> {
                adapter.setExpenses(expenses);
                balanceTextView.setText(String.format("₹%.2f", finalBalance));
                dailyLimitTextView
                        .setText(String.format("Daily Allowance: ₹%.2f (for %d days)", finalDailyLimit, daysRemaining));
            });
        });
    }

    private void processBillImage(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        processInputImage(image);
    }

    private void processInputImage(InputImage image) {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(text -> parseBillText(text))
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to read bill", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                });
    }

    private void parseBillText(Text text) {
        String fullText = text.getText();
        Log.d("MessTracker", "OCR Result: " + fullText);

        double recognizedAmount = 0.0;
        double recognizedBalance = 0.0;
        StringBuilder itemsBuilder = new StringBuilder();

        // Revised Regex to capture flexible amounts:
        // 1. Matches standard 1,234.56
        // 2. Matches unformatted 1234.56
        // 3. Matches OCR artifacts like 1 234.56
        Pattern moneyPattern = Pattern.compile("(\\d[\\d,\\s]*\\.\\d{2})");

        // Keywords
        String[] headerKeywords = { "Product", "Qty", "Rate", "Amt", "SN", "Description" };
        String[] totalKeywords = { "Total", "NET TOTAL", "Amount" };
        String[] balanceKeywords = { "YOUR BALANCE", "Wallet", "Balance" };

        // Flatten and Sort lines by Y coordinate to handle columns/fragments
        List<Text.Line> rawLines = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            rawLines.addAll(block.getLines());
        }

        // Sort by Top Y coordinate
        Collections.sort(rawLines, (l1, l2) -> Float.compare(l1.getBoundingBox().top, l2.getBoundingBox().top));

        // Merge lines that are on the same vertical level (row)
        List<String> lines = new ArrayList<>();
        if (!rawLines.isEmpty()) {
            StringBuilder currentRow = new StringBuilder(rawLines.get(0).getText());
            int currentTop = rawLines.get(0).getBoundingBox().top;
            int currentBottom = rawLines.get(0).getBoundingBox().bottom;
            int rowHeight = currentBottom - currentTop;

            for (int i = 1; i < rawLines.size(); i++) {
                Text.Line line = rawLines.get(i);
                int lineTop = line.getBoundingBox().top;

                // If top is within 50% of the previous row's height, consider it same row
                if (Math.abs(lineTop - currentTop) < (rowHeight * 0.5)) {
                    currentRow.append(" ").append(line.getText());
                } else {
                    lines.add(currentRow.toString());
                    currentRow = new StringBuilder(line.getText());
                    currentTop = lineTop;
                    rowHeight = line.getBoundingBox().bottom - lineTop;
                }
            }
            lines.add(currentRow.toString());
        }

        boolean inItemsSection = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String lowerLine = line.toLowerCase();

            // 1. Detect Header to start tracking items
            if (!inItemsSection) {
                for (String header : headerKeywords) {
                    if (line.contains(header)) {
                        inItemsSection = true;
                        continue; // Skip the header line itself
                    }
                }
            }

            // 2. Detect Total or End of Items
            boolean isTotalLine = false;
            for (String tot : totalKeywords) {
                if (line.contains(tot)) {
                    inItemsSection = false; // End items
                    isTotalLine = true;

                    // Try extract amount from this line
                    recognizedAmount = parseHighestAmount(line, moneyPattern, recognizedAmount);
                }
            }

            // 3. Capture Items
            if (inItemsSection && !isTotalLine) {
                // Filter noise (e.g. lines with just dashes or invalid chars)
                if (line.length() > 5 && !line.matches("^[\\d\\s\\W]+$")) {
                    if (itemsBuilder.length() > 0)
                        itemsBuilder.append("\n");

                    // Try to clean the line to just the Product Name
                    // Typical format: "1 ITEM NAME 2 50.00 100.00" OR "38.00 2 PHULKA"

                    String clean = line;

                    // 1. Remove leading index numbers (like "1 ")
                    clean = clean.replaceFirst("^\\d+\\s+", "");

                    // 2. Remove leading prices/quantities (like "38.00 2 ")
                    // Matches: Start of string, (digits with decimals or spaces), followed by space
                    clean = clean.replaceFirst("^[\\d.,\\s]+\\s+", "");

                    // 3. Remove trailing numbers (prices/qty)
                    // (like " 100.00")
                    clean = clean.replaceAll("[\\d.,\\s]+$", "");

                    if (!clean.trim().isEmpty()) {
                        itemsBuilder.append(clean.trim());
                    } else {
                        // Fallback to original if aggressive clean killed it
                        itemsBuilder.append(line);
                    }
                }
            }

            // 4. Detect Balance
            for (String bal : balanceKeywords) {
                if (line.contains(bal)) {
                    // Check this line for money
                    double val = parseHighestAmount(line, moneyPattern, 0);
                    if (val > 0) {
                        recognizedBalance = val;
                    } else if (i + 1 < lines.size()) {
                        // Check next line if not found here (e.g. "YOUR BALANCE" \n "2202.00")
                        val = parseHighestAmount(lines.get(i + 1), moneyPattern, 0);
                        if (val > 0) {
                            recognizedBalance = val;
                            // Skip next line in main loop to avoid double counting? Not strictly necessary
                            // here.
                        }
                    }
                }
            }
        }

        // Fallback: If amount is 0, scan whole text for largest value < 10000
        if (recognizedAmount == 0) {
            recognizedAmount = parseHighestAmount(fullText, moneyPattern, 0);
        }

        showConfirmDialog((float) recognizedAmount, (float) recognizedBalance, itemsBuilder.toString());
    }

    private double parseHighestAmount(String text, Pattern pattern, double currentMax) {
        Matcher m = pattern.matcher(text);
        double max = currentMax;
        while (m.find()) {
            try {
                // Remove commas and spaces for parsing
                String valStr = m.group(1).replace(",", "").replace(" ", "");
                double val = Double.parseDouble(valStr);
                // Sanity check: bills usually aren't massive, ignore crazy OCR errors
                if (val > max && val < 50000) {
                    max = val;
                }
            } catch (Exception e) {
            }
        }
        return max;
    }

    private void showConfirmDialog(float detectedAmount, float detectedBalance, String detectedItems) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Confirm Expense");

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_mess_expense, null);
        EditText amountInput = view.findViewById(R.id.edit_text_amount);
        EditText balanceInput = view.findViewById(R.id.edit_text_balance);
        EditText itemsInput = view.findViewById(R.id.edit_text_items);

        amountInput.setText(String.valueOf(detectedAmount));
        if (detectedBalance > 0)
            balanceInput.setText(String.valueOf(detectedBalance));
        itemsInput.setText(detectedItems);

        builder.setView(view);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String amtStr = amountInput.getText().toString();
            String balStr = balanceInput.getText().toString();
            String itemsStr = itemsInput.getText().toString();

            float finalAmount = amtStr.isEmpty() ? 0 : Float.parseFloat(amtStr);
            float finalBalance = balStr.isEmpty() ? 0 : Float.parseFloat(balStr);

            if (finalAmount > 0) {
                saveExpense(finalAmount, finalBalance, itemsStr);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveExpense(float amount, float balanceRemaining, String items) {
        MessExpense expense = new MessExpense();
        expense.amount = amount;
        expense.balanceRemaining = balanceRemaining;
        expense.itemsSummary = items;
        expense.date = System.currentTimeMillis();
        expense.location = "Mess";

        executorService.execute(() -> {
            database.messExpenseDao().insert(expense).blockingAwait();
            getActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "Expense Added", Toast.LENGTH_SHORT).show();
                loadData();
            });
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null)
            executorService.shutdown();
    }
}
