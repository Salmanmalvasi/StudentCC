package tk.therealsuji.vtopchennai.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;



import tk.therealsuji.vtopchennai.R;

public class WebViewActivity extends AppCompatActivity {

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        findViewById(R.id.image_button_back).setOnClickListener(view -> finish());

        String title = getIntent().getStringExtra("title") + "";
        String url = getIntent().getStringExtra("url") + "";

        TextView titleView = findViewById(R.id.text_view_title);
        titleView.setText(title);

        WebView webView = findViewById(R.id.web_view);
        View progressBar = findViewById(R.id.progress_bar);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl(url);
    }

    @Override
    protected void onResume() {
        super.onResume();

        String title = getIntent().getStringExtra("title") + "";


    }
}
