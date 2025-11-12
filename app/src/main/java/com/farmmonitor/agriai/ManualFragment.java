package com.farmmonitor.agriai;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class ManualFragment extends Fragment {

    private static final String TAG = "ManualFragment";
    private static final String FARM_ID = "farm_1"; // Change for multiple farms

    // Firebase
    private DatabaseReference controlsRef;
    private ValueEventListener controlsListener;

    // Views
    private Switch switchIrrigation, switchLighting, switchVentilation;
    private SeekBar seekbarLightIntensity, seekbarFanSpeed;
    private TextView tvIrrigationStatus, tvLightIntensity, tvFanSpeed;
    private Button btnIrrigationAuto, btnIrrigationSchedule;
    private Button btnLightSunrise, btnLightNoon, btnLightSunset;
    private Button btnSoundAlarm, btnTestSensors, btnEmergencyStop;

    // Prevent feedback loops when updating from Firebase
    private boolean isUpdatingFromFirebase = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manual, container, false);

        initializeViews(view);
        setupFirebase();
        setupListeners();

        return view;
    }

    private void initializeViews(View view) {
        // Switches
        switchIrrigation = view.findViewById(R.id.switch_irrigation);
        switchLighting = view.findViewById(R.id.switch_lighting);
        switchVentilation = view.findViewById(R.id.switch_ventilation);

        // SeekBars
        seekbarLightIntensity = view.findViewById(R.id.seekbar_light_intensity);
        seekbarFanSpeed = view.findViewById(R.id.seekbar_fan_speed);

        // TextViews
        tvIrrigationStatus = view.findViewById(R.id.tv_irrigation_status);
        tvLightIntensity = view.findViewById(R.id.tv_light_intensity);
        tvFanSpeed = view.findViewById(R.id.tv_fan_speed);

        // Buttons
        btnIrrigationAuto = view.findViewById(R.id.btn_irrigation_auto);
        btnIrrigationSchedule = view.findViewById(R.id.btn_irrigation_schedule);
        btnLightSunrise = view.findViewById(R.id.btn_light_sunrise);
        btnLightNoon = view.findViewById(R.id.btn_light_noon);
        btnLightSunset = view.findViewById(R.id.btn_light_sunset);
        btnSoundAlarm = view.findViewById(R.id.btn_sound_alarm);
        btnTestSensors = view.findViewById(R.id.btn_test_sensors);
        btnEmergencyStop = view.findViewById(R.id.btn_emergency_stop);
    }

    private void setupFirebase() {
        try {
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            controlsRef = database.getReference("farms").child(FARM_ID).child("controls");

            Log.d(TAG, "Connecting to Firebase: farms/" + FARM_ID + "/controls");

            // Listen for control changes from Firebase
            controlsListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        Log.d(TAG, "Controls data received from Firebase");
                        updateUIFromFirebase(snapshot);
                    } else {
                        Log.d(TAG, "No controls data found, initializing...");
                        initializeFirebaseControls();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Firebase error: " + error.getMessage());
                    showToast("Connection error: " + error.getMessage());
                }
            };

            controlsRef.addValueEventListener(controlsListener);

        } catch (Exception e) {
            Log.e(TAG, "Firebase setup error: " + e.getMessage());
            showToast("Failed to connect to Firebase");
        }
    }

    private void initializeFirebaseControls() {
        Map<String, Object> controls = new HashMap<>();

        // Irrigation control
        Map<String, Object> irrigation = new HashMap<>();
        irrigation.put("enabled", false);
        irrigation.put("autoMode", false);
        irrigation.put("lastUpdated", System.currentTimeMillis());
        controls.put("irrigation", irrigation);

        // Lighting control
        Map<String, Object> lighting = new HashMap<>();
        lighting.put("enabled", false);
        lighting.put("intensity", 50);
        lighting.put("preset", "manual");
        lighting.put("lastUpdated", System.currentTimeMillis());
        controls.put("lighting", lighting);

        // Ventilation control
        Map<String, Object> ventilation = new HashMap<>();
        ventilation.put("enabled", false);
        ventilation.put("fanSpeed", 50);
        ventilation.put("lastUpdated", System.currentTimeMillis());
        controls.put("ventilation", ventilation);

        // Alarm
        Map<String, Object> alarm = new HashMap<>();
        alarm.put("active", false);
        alarm.put("lastTriggered", 0L);
        controls.put("alarm", alarm);

        // Emergency stop
        Map<String, Object> emergency = new HashMap<>();
        emergency.put("stopped", false);
        emergency.put("lastActivated", 0L);
        controls.put("emergency", emergency);

        controlsRef.setValue(controls)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Controls initialized in Firebase");
                    showToast("Controls initialized");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗ Failed to initialize controls: " + e.getMessage());
                    showToast("Initialization failed");
                });
    }

    private void updateUIFromFirebase(DataSnapshot snapshot) {
        isUpdatingFromFirebase = true;

        try {
            // Update Irrigation
            DataSnapshot irrigation = snapshot.child("irrigation");
            if (irrigation.exists()) {
                Boolean enabled = irrigation.child("enabled").getValue(Boolean.class);
                if (enabled != null) {
                    switchIrrigation.setChecked(enabled);
                    tvIrrigationStatus.setText("Status: " + (enabled ? "ON" : "OFF"));
                }
            }

            // Update Lighting
            DataSnapshot lighting = snapshot.child("lighting");
            if (lighting.exists()) {
                Boolean enabled = lighting.child("enabled").getValue(Boolean.class);
                Long intensity = lighting.child("intensity").getValue(Long.class);

                if (enabled != null) {
                    switchLighting.setChecked(enabled);
                }
                if (intensity != null) {
                    seekbarLightIntensity.setProgress(intensity.intValue());
                    tvLightIntensity.setText("Intensity: " + intensity + "%");
                }
            }

            // Update Ventilation
            DataSnapshot ventilation = snapshot.child("ventilation");
            if (ventilation.exists()) {
                Boolean enabled = ventilation.child("enabled").getValue(Boolean.class);
                Long fanSpeed = ventilation.child("fanSpeed").getValue(Long.class);

                if (enabled != null) {
                    switchVentilation.setChecked(enabled);
                }
                if (fanSpeed != null) {
                    seekbarFanSpeed.setProgress(fanSpeed.intValue());
                    tvFanSpeed.setText("Fan Speed: " + fanSpeed + "%");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating UI from Firebase: " + e.getMessage());
        } finally {
            isUpdatingFromFirebase = false;
        }
    }

    private void setupListeners() {
        // === IRRIGATION SWITCH ===
        switchIrrigation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isUpdatingFromFirebase) {
                updateIrrigationControl(isChecked);
                tvIrrigationStatus.setText("Status: " + (isChecked ? "ON" : "OFF"));
                showToast("Irrigation " + (isChecked ? "ON" : "OFF"));
            }
        });

        // === LIGHTING SWITCH ===
        switchLighting.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isUpdatingFromFirebase) {
                updateLightingControl(isChecked, seekbarLightIntensity.getProgress());
                showToast("Lighting " + (isChecked ? "ON" : "OFF"));
            }
        });

        // === VENTILATION SWITCH ===
        switchVentilation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isUpdatingFromFirebase) {
                updateVentilationControl(isChecked, seekbarFanSpeed.getProgress());
                showToast("Ventilation " + (isChecked ? "ON" : "OFF"));
            }
        });

        // === LIGHT INTENSITY SEEKBAR ===
        seekbarLightIntensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLightIntensity.setText("Intensity: " + progress + "%");
                if (fromUser && !isUpdatingFromFirebase) {
                    updateLightingControl(switchLighting.isChecked(), progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // === FAN SPEED SEEKBAR ===
        seekbarFanSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvFanSpeed.setText("Fan Speed: " + progress + "%");
                if (fromUser && !isUpdatingFromFirebase) {
                    updateVentilationControl(switchVentilation.isChecked(), progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // === IRRIGATION AUTO MODE ===
        btnIrrigationAuto.setOnClickListener(v -> {
            Map<String, Object> updates = new HashMap<>();
            updates.put("autoMode", true);
            updates.put("lastUpdated", System.currentTimeMillis());

            controlsRef.child("irrigation").updateChildren(updates)
                    .addOnSuccessListener(aVoid -> showToast("Auto Mode Activated"))
                    .addOnFailureListener(e -> showToast("Failed: " + e.getMessage()));
        });

        // === IRRIGATION SCHEDULE ===
        btnIrrigationSchedule.setOnClickListener(v ->
                showToast("Irrigation Schedule - Coming Soon"));

        // === LIGHT PRESETS ===
        btnLightSunrise.setOnClickListener(v -> setLightPreset("sunrise", 30));
        btnLightNoon.setOnClickListener(v -> setLightPreset("noon", 100));
        btnLightSunset.setOnClickListener(v -> setLightPreset("sunset", 50));

        // === SOUND ALARM ===
        btnSoundAlarm.setOnClickListener(v -> {
            Map<String, Object> alarm = new HashMap<>();
            alarm.put("active", true);
            alarm.put("lastTriggered", System.currentTimeMillis());

            controlsRef.child("alarm").setValue(alarm)
                    .addOnSuccessListener(aVoid -> showToast("🚨 Alarm Activated"))
                    .addOnFailureListener(e -> showToast("Failed: " + e.getMessage()));
        });

        // === TEST SENSORS ===
        btnTestSensors.setOnClickListener(v -> {
            Map<String, Object> test = new HashMap<>();
            test.put("testRequested", System.currentTimeMillis());

            controlsRef.child("sensorTest").setValue(test)
                    .addOnSuccessListener(aVoid -> showToast("Sensor Test Requested"))
                    .addOnFailureListener(e -> showToast("Failed: " + e.getMessage()));
        });

        // === EMERGENCY STOP ===
        btnEmergencyStop.setOnClickListener(v -> activateEmergencyStop());
    }

    // === FIREBASE UPDATE METHODS ===

    private void updateIrrigationControl(boolean enabled) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("enabled", enabled);
        updates.put("lastUpdated", System.currentTimeMillis());

        controlsRef.child("irrigation").updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Irrigation updated: " + enabled))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update irrigation: " + e.getMessage());
                    showToast("Update failed");
                });
    }

    private void updateLightingControl(boolean enabled, int intensity) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("enabled", enabled);
        updates.put("intensity", intensity);
        updates.put("preset", "manual");
        updates.put("lastUpdated", System.currentTimeMillis());

        controlsRef.child("lighting").updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Lighting updated: " + enabled + ", " + intensity + "%"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update lighting: " + e.getMessage());
                    showToast("Update failed");
                });
    }

    private void updateVentilationControl(boolean enabled, int fanSpeed) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("enabled", enabled);
        updates.put("fanSpeed", fanSpeed);
        updates.put("lastUpdated", System.currentTimeMillis());

        controlsRef.child("ventilation").updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Ventilation updated: " + enabled + ", " + fanSpeed + "%"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update ventilation: " + e.getMessage());
                    showToast("Update failed");
                });
    }

    private void setLightPreset(String preset, int intensity) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("enabled", true);
        updates.put("intensity", intensity);
        updates.put("preset", preset);
        updates.put("lastUpdated", System.currentTimeMillis());

        controlsRef.child("lighting").updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    String presetName = preset.substring(0, 1).toUpperCase() + preset.substring(1);
                    showToast(presetName + " Preset Activated (" + intensity + "%)");
                    seekbarLightIntensity.setProgress(intensity);
                    switchLighting.setChecked(true);
                })
                .addOnFailureListener(e -> showToast("Failed: " + e.getMessage()));
    }

    private void activateEmergencyStop() {
        Map<String, Object> emergency = new HashMap<>();
        emergency.put("stopped", true);
        emergency.put("lastActivated", System.currentTimeMillis());

        // Turn off all controls immediately
        Map<String, Object> allOff = new HashMap<>();
        allOff.put("irrigation/enabled", false);
        allOff.put("lighting/enabled", false);
        allOff.put("ventilation/enabled", false);
        allOff.put("emergency", emergency);

        controlsRef.updateChildren(allOff)
                .addOnSuccessListener(aVoid -> {
                    showToast("⚠️ EMERGENCY STOP ACTIVATED!");
                    Log.d(TAG, "Emergency stop activated - all systems OFF");

                    // Update UI
                    switchIrrigation.setChecked(false);
                    switchLighting.setChecked(false);
                    switchVentilation.setChecked(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Emergency stop failed: " + e.getMessage());
                    showToast("Emergency stop failed!");
                });
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (controlsRef != null && controlsListener != null) {
            controlsRef.removeEventListener(controlsListener);
            Log.d(TAG, "Firebase listener removed");
        }
    }
}