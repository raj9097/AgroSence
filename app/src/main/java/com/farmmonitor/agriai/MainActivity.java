package com.farmmonitor.agriai;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize session manager
        sessionManager = new SessionManager(this);

        // Check if user is logged in
        if (sessionManager.isLoggedIn()) {
            // User is logged in, go directly to Dashboard
            startActivity(new Intent(this, MainDashboardActivity.class));
        } else {
            // User is not logged in, show Starting screen
            startActivity(new Intent(this, StartingActivity.class));
        }
        finish();
    }
}