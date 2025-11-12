package com.farmmonitor.agriai;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

public class HomeActivity extends AppCompatActivity {

    private Spinner spinnerFarms;
    private String[] farms = {"farm_1", "farm_2", "farm_3", "farm_4"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        spinnerFarms = findViewById(R.id.spinnerFarms);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, farms);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFarms.setAdapter(adapter);

        spinnerFarms.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedFarm = farms[position];
                loadFarmFragment(selectedFarm);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Load initial fragment
        loadFarmFragment(farms[0]);
    }

    private void loadFarmFragment(String farmId) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        FarmDataFragment fragment = FarmDataFragment.newInstance(farmId);
        transaction.replace(R.id.container, fragment);
        transaction.commit();
    }
}