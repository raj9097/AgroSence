package com.farmmonitor.agriai;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FarmDataFragment extends Fragment {

    private static final String TAG = "FarmDataFragment";
    private static final String ARG_FARM_ID = "farm_id";
    private static final long ONLINE_THRESHOLD = 90000; // 1.5 minutes

    // Weather API Configuration
    private static final String WEATHER_API_KEY = "5c5ae1f83894ec2c10ffc9738bde0cd0";
    private static final String CITY_NAME = "Vadodara";

    private String farmId;
    private DatabaseReference farmRef;
    private ValueEventListener farmListener;

    // Views
    private TextView tvTemperature, tvHumidity, tvSoilMoisture, tvLightLevel, tvLastUpdate;
    private TextView tvWeatherTemp, tvWeatherDesc, tvConnectionStatus, tvConnectionBadge;
    private ImageView ivConnectionStatus;
    private LinearLayout layoutNoData;

    // Track current state
    private boolean isRaspberryPiOnline = false;
    private WeatherResponse cachedWeatherData = null;

    public static FarmDataFragment newInstance(String farmId) {
        FarmDataFragment fragment = new FarmDataFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FARM_ID, farmId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            farmId = getArguments().getString(ARG_FARM_ID);
        }
        Log.d(TAG, "Fragment created for farm: " + farmId);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_farm_data, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupFirebaseConnection();
        fetchWeatherData(); // Always fetch weather as backup
    }

    private void initializeViews(View view) {
        ivConnectionStatus = view.findViewById(R.id.iv_connection_status);
        tvConnectionStatus = view.findViewById(R.id.tv_connection_status);
        tvConnectionBadge = view.findViewById(R.id.tv_connection_badge);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvHumidity = view.findViewById(R.id.tv_humidity);
        tvSoilMoisture = view.findViewById(R.id.tv_soil_moisture);
        tvLightLevel = view.findViewById(R.id.tv_light_level);
        tvLastUpdate = view.findViewById(R.id.tv_last_update);
        layoutNoData = view.findViewById(R.id.layout_no_data);
        tvWeatherTemp = view.findViewById(R.id.tv_weather_temp);
        tvWeatherDesc = view.findViewById(R.id.tv_weather_desc);
    }

    private void setupFirebaseConnection() {
        try {
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            farmRef = database.getReference("farms").child(farmId).child("sensors");

            Log.d(TAG, "Connecting to Firebase path: farms/" + farmId + "/sensors");

            setupDataListener();

        } catch (Exception e) {
            Log.e(TAG, "✗ Firebase setup exception: " + e.getMessage());
            showWeatherFallback();
        }
    }

    private void setupDataListener() {
        farmListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Data received from Firebase");

                if (snapshot.exists()) {
                    try {
                        SensorData data = snapshot.getValue(SensorData.class);

                        if (data != null && data.getLastUpdate() != null) {
                            // Check if Raspberry Pi is online
                            boolean isOnline = isDeviceOnline(data.getLastUpdate());
                            isRaspberryPiOnline = isOnline;

                            if (isOnline && data.getTemperature() != null) {
                                // Pi is online - use sensor data
                                Log.d(TAG, "✓ Raspberry Pi ONLINE - Using sensor data");
                                updateUIWithSensorData(data);
                                layoutNoData.setVisibility(View.GONE);
                            } else {
                                // Pi is offline - use weather data
                                Log.d(TAG, "✗ Raspberry Pi OFFLINE - Using weather data");
                                showWeatherFallback();
                            }
                        } else {
                            Log.w(TAG, "Sensor data is null or incomplete");
                            showWeatherFallback();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing sensor data: " + e.getMessage());
                        showWeatherFallback();
                    }
                } else {
                    Log.w(TAG, "No sensor data exists - Using weather fallback");
                    showWeatherFallback();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database error: " + error.getMessage());
                showWeatherFallback();
            }
        };

        farmRef.addValueEventListener(farmListener);
    }

    private void updateUIWithSensorData(SensorData data) {
        // Update connection status
        updateConnectionStatus(true);

        // Update sensor readings
        tvTemperature.setText(String.format(Locale.getDefault(),
                "%.1f°C", data.getTemperature()));
        tvHumidity.setText(String.format(Locale.getDefault(),
                "%.1f%%", data.getHumidity()));
        tvSoilMoisture.setText(String.format(Locale.getDefault(),
                "%.1f%%", data.getSoilMoisture()));
        tvLightLevel.setText(String.format(Locale.getDefault(),
                "%.0f lux", data.getLightLevel()));

        // Update timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",
                Locale.getDefault());
        tvLastUpdate.setText("Last updated: " +
                sdf.format(new Date(data.getLastUpdate())));

        // Show weather in separate section
        if (cachedWeatherData != null) {
            displayWeatherInfo(cachedWeatherData);
        }
    }

    private void showWeatherFallback() {
        if (cachedWeatherData != null) {
            Log.d(TAG, "Using cached weather data as fallback");
            updateUIWithWeatherData(cachedWeatherData);
        } else {
            Log.d(TAG, "Fetching fresh weather data...");
            fetchWeatherData();
        }
    }

    private void updateUIWithWeatherData(WeatherResponse weather) {
        // Update connection status to offline
        updateConnectionStatus(false);
        layoutNoData.setVisibility(View.GONE);

        // Use weather data for temperature and humidity
        tvTemperature.setText(String.format(Locale.getDefault(),
                "%.1f°C", weather.main.temp));
        tvHumidity.setText(String.format(Locale.getDefault(),
                "%d%%", weather.main.humidity));

        // Show N/A for soil and light (not available from weather API)
        tvSoilMoisture.setText("N/A");
        tvLightLevel.setText("N/A");

        // Update timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",
                Locale.getDefault());
        tvLastUpdate.setText("Weather data updated: " +
                sdf.format(new Date(System.currentTimeMillis())));

        // Display detailed weather info
        displayWeatherInfo(weather);

        Log.d(TAG, "✓ UI updated with weather data");
    }

    private void fetchWeatherData() {
        WeatherApi api = RetrofitClient.getInstance().create(WeatherApi.class);
        api.getWeather(CITY_NAME, WEATHER_API_KEY, "metric")
                .enqueue(new Callback<WeatherResponse>() {
                    @Override
                    public void onResponse(Call<WeatherResponse> call,
                                           Response<WeatherResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            WeatherResponse weather = response.body();
                            cachedWeatherData = weather;

                            Log.d(TAG, "✓ Weather data fetched successfully");

                            // If Pi is offline, use weather data immediately
                            if (!isRaspberryPiOnline) {
                                updateUIWithWeatherData(weather);
                            } else {
                                // Just display weather info in separate section
                                displayWeatherInfo(weather);
                            }
                        } else {
                            Log.e(TAG, "Weather API response unsuccessful");
                            showNoDataState();
                        }
                    }

                    @Override
                    public void onFailure(Call<WeatherResponse> call, Throwable t) {
                        Log.e(TAG, "Weather API failed: " + t.getMessage());
                        if (!isRaspberryPiOnline) {
                            showNoDataState();
                        }
                    }
                });
    }

    private void displayWeatherInfo(WeatherResponse weather) {
        if (getActivity() == null) return;

        tvWeatherTemp.setText(String.format(Locale.getDefault(),
                "%.1f°C", weather.main.temp));

        String weatherDesc = String.format(Locale.getDefault(),
                "Humidity: %d%%, Wind: %.1f m/s, Feels like: %.1f°C",
                weather.main.humidity,
                weather.wind.speed,
                weather.main.feels_like != null ? weather.main.feels_like : weather.main.temp);

        tvWeatherDesc.setText(weatherDesc);
    }

    private void updateConnectionStatus(boolean isOnline) {
        if (getActivity() == null) return;

        if (isOnline) {
            // Raspberry Pi is online
            ivConnectionStatus.setImageResource(android.R.drawable.presence_online);
            tvConnectionStatus.setText("Online");
            tvConnectionBadge.setText("ONLINE");
            tvConnectionBadge.setBackgroundColor(
                    getResources().getColor(android.R.color.holo_green_dark, null));
        } else {
            // Raspberry Pi is offline - using weather data
            ivConnectionStatus.setImageResource(android.R.drawable.presence_offline);
            tvConnectionStatus.setText("Offline (Weather Data)");
            tvConnectionBadge.setText("OFFLINE");
            tvConnectionBadge.setBackgroundColor(
                    getResources().getColor(android.R.color.holo_orange_dark, null));
        }
    }

    private boolean isDeviceOnline(Long lastUpdate) {
        if (lastUpdate == null) return false;
        long timeDifference = System.currentTimeMillis() - lastUpdate;
        boolean isOnline = timeDifference < ONLINE_THRESHOLD;

        Log.d(TAG, "Device status check: last update " + timeDifference + "ms ago, " +
                "threshold: " + ONLINE_THRESHOLD + "ms, online: " + isOnline);

        return isOnline;
    }

    private void showNoDataState() {
        if (getActivity() == null) return;

        layoutNoData.setVisibility(View.VISIBLE);
        updateConnectionStatus(false);
        tvTemperature.setText("--°C");
        tvHumidity.setText("--%");
        tvSoilMoisture.setText("--%");
        tvLightLevel.setText("-- lux");
        tvLastUpdate.setText("No data available");
        tvWeatherTemp.setText("--°C");
        tvWeatherDesc.setText("Weather data unavailable");
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh weather data when fragment resumes
        fetchWeatherData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (farmRef != null && farmListener != null) {
            farmRef.removeEventListener(farmListener);
            Log.d(TAG, "Firebase listener removed");
        }
    }
}