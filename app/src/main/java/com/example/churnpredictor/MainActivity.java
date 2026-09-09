package com.example.churnpredictor;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText tenureInput, chargesInput;
    private Spinner contractSpinner;
    private CheckBox internetCheckBox, techSupportCheckBox;
    private SeekBar tenureSeekBar, chargesSeekBar;
    private TextView probabilityOutput, riskLevelOutput, recommendationOutput;
    private TextView displayTenure, displayCharges;

    private int selectedContractType = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        tenureInput = findViewById(R.id.tenureInput);
        chargesInput = findViewById(R.id.chargesInput);
        contractSpinner = findViewById(R.id.contractSpinner);
        internetCheckBox = findViewById(R.id.internetCheckBox);
        techSupportCheckBox = findViewById(R.id.techSupportCheckBox);
        tenureSeekBar = findViewById(R.id.tenureSeekBar);
        chargesSeekBar = findViewById(R.id.chargesSeekBar);
        probabilityOutput = findViewById(R.id.probabilityOutput);
        riskLevelOutput = findViewById(R.id.riskLevelOutput);
        recommendationOutput = findViewById(R.id.recommendationOutput);
        displayTenure = findViewById(R.id.displayTenure);
        displayCharges = findViewById(R.id.displayCharges);

        // Setup contract spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.contract_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        contractSpinner.setAdapter(adapter);
        contractSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedContractType = position;
                predictChurn();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Setup seekbars
        tenureSeekBar.setMax(72); // 0-72 months (6 years)
        tenureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                displayTenure.setText(progress + " months");
                tenureInput.setText(String.valueOf(progress));
                predictChurn();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        chargesSeekBar.setMax(150); // 0-150 dollars
        chargesSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                displayCharges.setText("$" + progress);
                chargesInput.setText(String.valueOf(progress));
                predictChurn();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Setup checkboxes
        internetCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> predictChurn());
        techSupportCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> predictChurn());

        // Initial prediction
        predictChurn();
    }

    private void predictChurn() {
        try {
            int tenure = tenureSeekBar.getProgress();
            int charges = chargesSeekBar.getProgress();
            boolean hasInternet = internetCheckBox.isChecked();
            boolean hasTechSupport = techSupportCheckBox.isChecked();

            ChurnModel.PredictionResult result = ChurnModel.predict(
                    tenure,
                    (double) charges,
                    selectedContractType,
                    hasInternet,
                    hasTechSupport
            );

            // Update probability display
            String probText = String.format("%.1f%%", result.churnProbability * 100);
            probabilityOutput.setText(probText);

            // Update risk level with color
            riskLevelOutput.setText(result.riskLevel);
            if (result.riskLevel.equals("LOW RISK")) {
                riskLevelOutput.setTextColor(Color.GREEN);
            } else if (result.riskLevel.equals("MEDIUM RISK")) {
                riskLevelOutput.setTextColor(Color.parseColor("#FFA500")); // Orange
            } else {
                riskLevelOutput.setTextColor(Color.RED);
            }

            // Update recommendation
            recommendationOutput.setText(result.recommendation);

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show();
        }
    }
}
