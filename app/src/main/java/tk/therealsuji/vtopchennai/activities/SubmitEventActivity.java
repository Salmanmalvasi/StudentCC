package tk.therealsuji.vtopchennai.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Placeholder activity retained while the events submission feature is disabled.
 */
public class SubmitEventActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Feature removed – close immediately.
        finish();
    }
}
