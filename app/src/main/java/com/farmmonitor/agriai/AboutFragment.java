package com.farmmonitor.agriai;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AboutFragment extends Fragment {

    private TextView tvUserName, tvUserCity;
    private Button btnLogout;
    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate fragment layout
        View view = inflater.inflate(R.layout.fragment_about, container, false);

        // Initialize SessionManager
        sessionManager = new SessionManager(requireContext());

        initViews(view);
        loadUserData();
        setupLogoutButton();

        return view;
    }

    private void initViews(View view) {
        tvUserName = view.findViewById(R.id.tv_user_name);
        tvUserCity = view.findViewById(R.id.tv_user_city);
        btnLogout = view.findViewById(R.id.btn_logout);
    }

    private void loadUserData() {
        // Get user data from local session
        User user = sessionManager.getUser();

        if (user != null && user.getName() != null && !user.getName().isEmpty()) {
            tvUserName.setText(user.getName());
            tvUserCity.setText(user.getCity());
        } else {
            tvUserName.setText("Guest User");
            tvUserCity.setText("Unknown");
        }
    }

    private void setupLogoutButton() {
        btnLogout.setOnClickListener(v -> showLogoutConfirmation());
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void performLogout() {
        // Clear session
        sessionManager.logout();

        // Show toast message
        Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();

        // Navigate to StartingActivity and clear all previous activities
        Intent intent = new Intent(requireActivity(), StartingActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
