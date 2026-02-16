package com.example.incidentreports;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    private EditText edtEmail;
    private EditText edtPassword;
    private EditText edtServerUrl;
    private TextView txtConnectionStatus;
    private ProgressBar progressBar;

    private PocketBaseApiHelper apiHelper;
    private SessionManager sessionManager;
    private ApiConfigManager apiConfigManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        apiConfigManager = new ApiConfigManager(this);
        apiHelper = new PocketBaseApiHelper(this);
        sessionManager = new SessionManager(this);

        if (sessionManager.isLoggedIn()) {
            goToTaskList();
            return;
        }

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        edtServerUrl = findViewById(R.id.edtServerUrl);
        edtServerUrl.setText(apiConfigManager.getBaseUrl());
        txtConnectionStatus = findViewById(R.id.txtConnectionStatus);

        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnTestConnection = findViewById(R.id.btnTestConnection);
        TextView txtRegister = findViewById(R.id.txtRegisterNow);
        progressBar = findViewById(R.id.progressLogin);

        btnLogin.setOnClickListener(v -> attemptLogin());
        btnTestConnection.setOnClickListener(v -> testConnection());
        txtRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void testConnection() {
        String serverUrl = normalizeServerUrl(edtServerUrl.getText().toString().trim());
        String serverError = validateServerUrl(serverUrl);

        if (serverError != null) {
            txtConnectionStatus.setText(serverError);
            txtConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark));
            return;
        }

        edtServerUrl.setText(serverUrl);
        apiConfigManager.setBaseUrl(serverUrl);
        apiHelper = new PocketBaseApiHelper(this, serverUrl);
        txtConnectionStatus.setText("Testing connection...");

        apiHelper.checkServerConnection(new PocketBaseApiHelper.ConnectivityCallback() {
            @Override
            public void onSuccess(String message) {
                txtConnectionStatus.setText(message);
                txtConnectionStatus.setTextColor(getColor(android.R.color.holo_green_dark));
            }

            @Override
            public void onError(String message) {
                txtConnectionStatus.setText(message);
                txtConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark));
            }
        });
    }

    private void attemptLogin() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();
        String serverUrl = normalizeServerUrl(edtServerUrl.getText().toString().trim());

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and password are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        String serverError = validateServerUrl(serverUrl);
        if (serverError != null) {
            Toast.makeText(this, serverError, Toast.LENGTH_LONG).show();
            return;
        }

        edtServerUrl.setText(serverUrl);
        apiConfigManager.setBaseUrl(serverUrl);
        apiHelper = new PocketBaseApiHelper(this, serverUrl);

        setLoading(true);
        apiHelper.loginAdmin(email, password, new PocketBaseApiHelper.AuthCallback() {
            @Override
            public void onSuccess(String token, String userId, String fullName, String responderId) {
                setLoading(false);
                sessionManager.saveSession(token, userId, fullName, responderId);
                goToTaskList();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Login failed: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String normalizeServerUrl(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String validateServerUrl(String serverUrl) {
        if (serverUrl.isEmpty() || !serverUrl.startsWith("http")) {
            return "Enter a valid server URL (http://... or https://...)";
        }

        Uri uri = Uri.parse(serverUrl);
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return "Server URL host is invalid.";
        }

        if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "0.0.0.0".equals(host)) {
            return "This URL is only local to your PC. Use your PC LAN IP (e.g. http://192.168.x.x:8090).";
        }

        return null;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void goToTaskList() {
        Intent intent = new Intent(this, TaskListActivity.class);
        startActivity(intent);
        finish();
    }
}
