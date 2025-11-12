package com.farmmonitor.agriai;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.farmmonitor.agriai.FarmDataFragment;
import com.farmmonitor.agriai.R;

public class HomeFragment extends Fragment {

    private Spinner spinnerFarms;
    private String[] farms = {"farm_1", "farm_2", "farm_3", "farm_4"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        spinnerFarms = view.findViewById(R.id.spinnerFarms);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, farms);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFarms.setAdapter(adapter);

        spinnerFarms.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadFarmFragment(farms[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Load initial fragment
        loadFarmFragment(farms[0]);

        return view;
    }

    private void loadFarmFragment(String farmId) {
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        FarmDataFragment fragment = FarmDataFragment.newInstance(farmId);
        transaction.replace(R.id.farm_fragment_container, fragment);
        transaction.commit();
    }
}
