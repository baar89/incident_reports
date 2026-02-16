package com.example.incidentreports;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

        RecyclerView recyclerView = findViewById(R.id.recyclerIncidents);
        progressBar = findViewById(R.id.progressTasks);
        txtEmpty = findViewById(R.id.txtEmpty);

        adapter = new IncidentAdapter(incidentReport -> {
            Intent intent = new Intent(TaskListActivity.this, IncidentDetailActivity.class);
            intent.putExtra("incident_id", incidentReport.getId());
            startActivity(intent);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchAssignedTasks();
    }

    private void fetchAssignedTasks() {
        setLoading(true);
        apiHelper.fetchAssignedIncidents(sessionManager.getToken(), sessionManager.getUserId(), new PocketBaseApiHelper.IncidentListCallback() {
            @Override
            public void onSuccess(List<IncidentReport> incidents) {
                setLoading(false);
                adapter.submitList(incidents);
                txtEmpty.setVisibility(incidents.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(TaskListActivity.this, "Failed to load tasks: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void logoutAndGoToLogin() {
        sessionManager.clearSession();
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 101, 0, "Logout");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 101) {
            logoutAndGoToLogin();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
