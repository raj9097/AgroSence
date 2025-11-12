package com.farmmonitor.agriai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SignUpActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword, etCity;
    private Button btnSignUp;
    private TextView tvLogin;
    private ProgressBar progressBar;
    private Toolbar toolbar;

    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize database helper
        databaseHelper = new DatabaseHelper(this);

        initViews();
        setupToolbar();
        setupClickListeners();
    }

    private void initViews() {
        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etCity = findViewById(R.id.et_city);
        btnSignUp = findViewById(R.id.btn_sign_up);
        tvLogin = findViewById(R.id.tv_login);
        progressBar = findViewById(R.id.progress_bar);
        toolbar = findViewById(R.id.toolbar);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Sign Up");
        }
    }

    private void setupClickListeners() {
        btnSignUp.setOnClickListener(v -> signUpUser());

        tvLogin.setOnClickListener(v -> {
            // Go to login activity
            Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });

        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void signUpUser() {
        String name = etName.getText().toString().trim();
        String city = etCity.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Input validation
        if (TextUtils.isEmpty(name)) {
            etName.setError("Please enter your name");
            etName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(city)) {
            etCity.setError("Please enter your city");
            etCity.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Please enter your email");
            etEmail.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Please enter your password");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        // Check for existing user
        if (databaseHelper.isEmailExists(email)) {
            etEmail.setError("This email is already registered");
            etEmail.requestFocus();
            Toast.makeText(this, "Email already exists. Please login instead.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        showLoading(true);

        // Simulate registration process
        new android.os.Handler().postDelayed(() -> {
            long result = databaseHelper.registerUser(name, email, city, password);

            showLoading(false);

            if (result != -1) {
                Toast.makeText(this, "✓ Account created successfully!",
                        Toast.LENGTH_SHORT).show();

                // ✅ IMPORTANT: Don't create session here, just go to LoginActivity
                // Navigate to LoginActivity with success message
                Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
                intent.putExtra("REGISTRATION_SUCCESS", true);
                intent.putExtra("USER_EMAIL", email);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();

            } else {
                Toast.makeText(this, "✗ Registration failed. Please try again.",
                        Toast.LENGTH_SHORT).show();
            }

        }, 800);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSignUp.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Go back to login activity
        Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}