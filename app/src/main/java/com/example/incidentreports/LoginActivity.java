package com.example.incidentreports;

import android.content.Intent;
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

        Button btnLogin = findViewById(R.id.btnLogin);
        TextView txtRegister = findViewById(R.id.txtRegisterNow);
        progressBar = findViewById(R.id.progressLogin);

        btnLogin.setOnClickListener(v -> attemptLogin());
        txtRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();
        String serverUrl = edtServerUrl.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and password are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (serverUrl.isEmpty() || !serverUrl.startsWith("http")) {
            Toast.makeText(this, "Enter a valid server URL (http://... or https://...)", Toast.LENGTH_LONG).show();
            return;
        }

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

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void goToTaskList() {
        Intent intent = new Intent(this, TaskListActivity.class);
        startActivity(intent);
        finish();
    }
}
