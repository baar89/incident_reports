package com.example.incidentreports;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int RESPONDER_ID_LENGTH = 15;

    private EditText edtFirstName;
    private EditText edtLastName;
    private EditText edtPosition;
    private EditText edtEmail;
    private EditText edtPassword;
    private EditText edtResponderId;
    private EditText edtServerUrl;
    private TextView txtPasswordIndicator;
    private ProgressBar progressBar;

    private PocketBaseApiHelper apiHelper;
    private ApiConfigManager apiConfigManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        apiConfigManager = new ApiConfigManager(this);
        apiHelper = new PocketBaseApiHelper(this);

        edtFirstName = findViewById(R.id.edtFirstName);
        edtLastName = findViewById(R.id.edtLastName);
        edtPosition = findViewById(R.id.edtPosition);
        edtEmail = findViewById(R.id.edtRegisterEmail);
        edtPassword = findViewById(R.id.edtRegisterPassword);
        edtResponderId = findViewById(R.id.edtResponderId);
        edtServerUrl = findViewById(R.id.edtServerUrl);
        edtServerUrl.setText(apiConfigManager.getBaseUrl());

        txtPasswordIndicator = findViewById(R.id.txtPasswordIndicator);
        Button btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressRegister);

        bindPasswordIndicator();
        btnRegister.setOnClickListener(v -> register());
    }

    private void bindPasswordIndicator() {
        edtPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // no-op
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int currentLength = s == null ? 0 : s.length();
                if (currentLength < MIN_PASSWORD_LENGTH) {
                    txtPasswordIndicator.setText("Password must be at least 8 characters (" + currentLength + "/8)");
                    txtPasswordIndicator.setTextColor(getColor(android.R.color.holo_red_dark));
                } else {
                    txtPasswordIndicator.setText("Password length is valid (" + currentLength + "/8+) ");
                    txtPasswordIndicator.setTextColor(getColor(android.R.color.holo_green_dark));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // no-op
            }
        });
    }

    private void register() {
        String firstName = edtFirstName.getText().toString().trim();
        String lastName = edtLastName.getText().toString().trim();
        String position = edtPosition.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();
        String responderId = edtResponderId.getText().toString().trim();
        String serverUrl = edtServerUrl.getText().toString().trim();

        if (!validateInputs(firstName, lastName, position, email, password, responderId, serverUrl)) {
            return;
        }

        apiConfigManager.setBaseUrl(serverUrl);
        apiHelper = new PocketBaseApiHelper(this, serverUrl);

        setLoading(true);
        apiHelper.registerAdmin(firstName, lastName, position, email, password, responderId, new PocketBaseApiHelper.SimpleCallback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, "Registration successful. Please login.", Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, "Registration failed: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean validateInputs(String firstName,
                                   String lastName,
                                   String position,
                                   String email,
                                   String password,
                                   String responderId,
                                   String serverUrl) {
        clearErrors();

        if (TextUtils.isEmpty(firstName)) {
            edtFirstName.setError("First name is required");
            edtFirstName.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(lastName)) {
            edtLastName.setError("Last name is required");
            edtLastName.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(position)) {
            edtPosition.setError("Position is required");
            edtPosition.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("Email is required");
            edtEmail.requestFocus();
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Enter a valid email address");
            edtEmail.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            edtPassword.setError("Password is required");
            edtPassword.requestFocus();
            return false;
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            edtPassword.setError("Password must be at least 8 characters");
            edtPassword.requestFocus();
            Toast.makeText(this, "Password should be 8 characters minimum.", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (TextUtils.isEmpty(responderId)) {
            edtResponderId.setError("Responder ID is required");
            edtResponderId.requestFocus();
            return false;
        }

        if (responderId.length() != RESPONDER_ID_LENGTH || !responderId.matches("^[a-z0-9]{15}$")) {
            edtResponderId.setError("Responder ID must be exactly 15 lowercase letters/numbers");
            edtResponderId.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(serverUrl) || !serverUrl.startsWith("http")) {
            edtServerUrl.setError("Server URL must start with http:// or https://");
            edtServerUrl.requestFocus();
            return false;
        }

        return true;
    }

    private void clearErrors() {
        edtFirstName.setError(null);
        edtLastName.setError(null);
        edtPosition.setError(null);
        edtEmail.setError(null);
        edtPassword.setError(null);
        edtResponderId.setError(null);
        edtServerUrl.setError(null);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
