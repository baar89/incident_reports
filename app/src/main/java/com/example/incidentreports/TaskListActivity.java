package com.example.incidentreports;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TaskListActivity extends AppCompatActivity {
    private SessionManager sessionManager;
    private PocketBaseApiHelper apiHelper;
    private IncidentAdapter adapter;
    private ProgressBar progressBar;
    private TextView txtEmpty;
    private int lastKnownIncidentCount = 0;
    private MediaPlayer mediaPlayer; // Keep a reference to the MediaPlayer

    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private static final int REFRESH_INTERVAL = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_list);

        sessionManager = new SessionManager(this);
        apiHelper = new PocketBaseApiHelper(this);

        if (!sessionManager.isLoggedIn()) {
            logoutAndGoToLogin();
            return;
        }

        TextView txtWelcome = findViewById(R.id.txtWelcome);
        txtWelcome.setText("Welcome, " + sessionManager.getFullName());

        ImageButton btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> logoutAndGoToLogin());

        RecyclerView recyclerView = findViewById(R.id.recyclerIncidents);
        progressBar = findViewById(R.id.progressTasks);
        txtEmpty = findViewById(R.id.txtEmpty);

        adapter = new IncidentAdapter(incidentReport -> {
            stopNotificationSound(); // Stop the sound when an incident is clicked
            Intent intent = new Intent(TaskListActivity.this, IncidentDetailActivity.class);
            intent.putExtra("incident_id", incidentReport.getId());
            startActivity(intent);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchAssignedTasks(false);
            autoRefreshHandler.postDelayed(this, REFRESH_INTERVAL);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        fetchAssignedTasks(true);
        startAutoRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
        // Optionally stop sound if user leaves the activity
        // stopNotificationSound(); 
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopNotificationSound(); // Ensure cleanup
    }

    private void startAutoRefresh() {
        autoRefreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    private void stopAutoRefresh() {
        autoRefreshHandler.removeCallbacks(refreshRunnable);
    }

    private void fetchAssignedTasks(boolean showLoader) {
        if (showLoader) setLoading(true);
        apiHelper.fetchAssignedIncidents(sessionManager.getToken(), sessionManager.getUserId(), new PocketBaseApiHelper.IncidentListCallback() {
            @Override
            public void onSuccess(List<IncidentReport> incidents) {
                if (showLoader) setLoading(false);

                if (incidents.size() > lastKnownIncidentCount && !showLoader) {
                    playNotificationSound();
                }
                lastKnownIncidentCount = incidents.size();

                adapter.submitList(incidents);
                txtEmpty.setVisibility(incidents.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                if (showLoader) setLoading(false);
                if (showLoader) {
                    Toast.makeText(TaskListActivity.this, "Failed to load tasks: " + message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void playNotificationSound() {
        try {
            stopNotificationSound(); // Stop any existing sound before playing new
            mediaPlayer = MediaPlayer.create(this, R.raw.notification_sound);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true); // Loop until stopped manually
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopNotificationSound() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void logoutAndGoToLogin() {
        stopAutoRefresh();
        stopNotificationSound();
        sessionManager.clearSession();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
