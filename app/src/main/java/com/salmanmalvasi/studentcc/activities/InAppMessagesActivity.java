package com.salmanmalvasi.studentcc.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.List;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.adapters.InAppMessageAdapter;
import com.salmanmalvasi.studentcc.helpers.InAppMessageManager;
import com.salmanmalvasi.studentcc.models.InAppMessage;

/**
 * Activity to display in-app messages
 */
public class InAppMessagesActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private InAppMessageAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView emptyView;
    private InAppMessageManager messageManager;

    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("NEW_IN_APP_MESSAGE".equals(intent.getAction())) {
                loadMessages();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_in_app_messages);

        setupToolbar();
        initializeViews();
        setupRecyclerView();
        loadMessages();

        // Register broadcast receiver
    IntentFilter filter = new IntentFilter("NEW_IN_APP_MESSAGE");
    registerReceiver(messageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(messageReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered
        }
    }

    private void setupToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Messages");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recycler_view_messages);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        emptyView = findViewById(R.id.text_empty_messages);
        messageManager = InAppMessageManager.getInstance();

        swipeRefresh.setOnRefreshListener(this::loadMessages);
    }

    private void setupRecyclerView() {
        adapter = new InAppMessageAdapter(this::onMessageClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadMessages() {
        swipeRefresh.setRefreshing(true);

        // Simulate network delay
        recyclerView.postDelayed(() -> {
            List<InAppMessage> messages = messageManager.getAllMessages();

            if (messages.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                adapter.submitList(messages);
            }

            swipeRefresh.setRefreshing(false);
        }, 500);
    }

    private void onMessageClick(InAppMessage message) {
        // Mark as read
        messageManager.markAsRead(message.id);

        // Handle action if present
        if (message.hasAction()) {
            // You can implement URL handling or specific actions here
            // For example, open a web view or navigate to a specific screen
            Intent intent = new Intent(this, WebViewActivity.class);
            intent.putExtra("url", message.actionUrl);
            intent.putExtra("title", message.title);
            startActivity(intent);
        }

        // Refresh the list to update read status
        loadMessages();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
