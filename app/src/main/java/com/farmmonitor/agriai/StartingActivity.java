package com.farmmonitor.agriai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class StartingActivity extends AppCompatActivity {

    private Button btnLogin;
    private TextView btnSignUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starting);

        initViews();
        setupClickListeners();
    }

    private void initViews() {
        btnLogin = findViewById(R.id.btn_login);
        btnSignUp = findViewById(R.id.btn_sign_up);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> {
            Intent intent = new Intent(StartingActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        btnSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(StartingActivity.this, SignUpActivity.class);
            startActivity(intent);
        });
    }
}
