package com.jayraj.churnpredictor;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;
import java.util.Locale;

/** One screen: describe a customer, see their predicted churn risk and what drives it. */
public class MainActivity extends AppCompatActivity {

    private static final int MIN_CHARGES = 18;

    private SeekBar tenureSeek, chargesSeek;
    private TextView tenureLabel, chargesLabel;
    private Spinner contractSpinner, internetSpinner, paymentSpinner;
    private TextView probabilityText, riskText, recommendationText, driversText, exposureText;
    private ProgressBar meter;

    private final String[] flagKeys = {
        "techSupport", "onlineSecurity", "onlineBackup", "deviceProtection", "streamingTV",
        "streamingMovies", "paperlessBilling", "seniorCitizen", "partner", "dependents",
        "phoneService", "multipleLines"};
    private final int[] flagLabels = {
        R.string.tech_support, R.string.online_security, R.string.online_backup,
        R.string.device_protection, R.string.streaming_tv, R.string.streaming_movies,
        R.string.paperless_billing, R.string.senior, R.string.partner, R.string.dependents,
        R.string.phone_service, R.string.multiple_lines};
    private final CheckBox[] flags = new CheckBox[flagKeys.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Draw behind the system bars on every Android version (Android 15+ enforces this for apps that
        // target it), and pad the content so nothing hides beneath them. The top inset AppCompat
        // hands down already includes the app bar, so it is used as is.
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootScroll), (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        tenureSeek = findViewById(R.id.tenureSeek);
        chargesSeek = findViewById(R.id.chargesSeek);
        tenureLabel = findViewById(R.id.tenureLabel);
        chargesLabel = findViewById(R.id.chargesLabel);
        contractSpinner = findViewById(R.id.contractSpinner);
        internetSpinner = findViewById(R.id.internetSpinner);
        paymentSpinner = findViewById(R.id.paymentSpinner);
        probabilityText = findViewById(R.id.probabilityText);
        riskText = findViewById(R.id.riskText);
        recommendationText = findViewById(R.id.recommendationText);
        driversText = findViewById(R.id.driversText);
        exposureText = findViewById(R.id.exposureText);
        meter = findViewById(R.id.meter);
        LinearLayout flagsContainer = findViewById(R.id.flagsContainer);

        ((TextView) findViewById(R.id.modelNote)).setText(getString(R.string.model_note,
                ModelData.TRAINED_ON, ModelData.N_TRAIN + ModelData.N_TEST,
                ModelData.ROC_AUC, ModelData.ROC_AUC_LOW, ModelData.ROC_AUC_HIGH));
        ((TextView) findViewById(R.id.modelProvenance)).setText(getString(R.string.model_provenance,
                ModelData.MODEL_VERSION, ModelData.TRAINED_DATE, shortHash(ModelData.TRAINING_COMMIT, 7),
                shortHash(ModelData.DATA_SHA256, 12), shortHash(ModelData.COEFFICIENTS_SHA256, 8),
                ModelData.SKLEARN_VERSION));

        bindSpinner(contractSpinner, ChurnModel.CONTRACTS, 0);
        bindSpinner(internetSpinner, ChurnModel.INTERNET_LABELS, 1);
        bindSpinner(paymentSpinner, ChurnModel.PAYMENTS, 0);

        tenureSeek.setMax(72);
        chargesSeek.setMax(112); // $18 to $130
        SeekBar.OnSeekBarChangeListener onSeek = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { refresh(); }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        };
        tenureSeek.setOnSeekBarChangeListener(onSeek);
        chargesSeek.setOnSeekBarChangeListener(onSeek);
        tenureSeek.setProgress(3);
        chargesSeek.setProgress(96 - MIN_CHARGES);

        for (int i = 0; i < flags.length; i++) {
            CheckBox box = new CheckBox(this);
            box.setText(flagLabels[i]);
            box.setOnCheckedChangeListener((button, checked) -> refresh());
            flagsContainer.addView(box);
            flags[i] = box;
        }
        flags[indexOfFlag("phoneService")].setChecked(true);
        flags[indexOfFlag("paperlessBilling")].setChecked(true);
        flags[indexOfFlag("streamingTV")].setChecked(true);

        refresh();
    }

    private void bindSpinner(Spinner spinner, String[] items, int selected) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { refresh(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private int indexOfFlag(String key) {
        for (int i = 0; i < flagKeys.length; i++) {
            if (flagKeys[i].equals(key)) return i;
        }
        throw new IllegalArgumentException(key);
    }

    private boolean flag(String key) {
        CheckBox box = flags[indexOfFlag(key)];
        return box != null && box.isChecked();
    }

    private ChurnModel.Customer readCustomer() {
        ChurnModel.Customer c = new ChurnModel.Customer();
        c.tenure = tenureSeek.getProgress();
        c.monthlyCharges = MIN_CHARGES + chargesSeek.getProgress();
        c.contract = contractSpinner.getSelectedItemPosition();
        c.internet = internetSpinner.getSelectedItemPosition();
        c.payment = paymentSpinner.getSelectedItemPosition();
        c.techSupport = flag("techSupport");
        c.onlineSecurity = flag("onlineSecurity");
        c.onlineBackup = flag("onlineBackup");
        c.deviceProtection = flag("deviceProtection");
        c.streamingTV = flag("streamingTV");
        c.streamingMovies = flag("streamingMovies");
        c.paperlessBilling = flag("paperlessBilling");
        c.seniorCitizen = flag("seniorCitizen");
        c.partner = flag("partner");
        c.dependents = flag("dependents");
        c.phoneService = flag("phoneService");
        c.multipleLines = flag("multipleLines");
        return c;
    }

    /** Re-score and redraw. Guards against the first listener callbacks firing before the views exist. */
    private void refresh() {
        if (flags[flags.length - 1] == null || probabilityText == null) return;

        ChurnModel.Customer c = readCustomer();
        tenureLabel.setText(getString(R.string.tenure_label, c.tenure));
        chargesLabel.setText(String.format(Locale.US, getString(R.string.charges_label), c.monthlyCharges));

        ChurnModel.Result r = ChurnModel.predict(c);
        probabilityText.setText(String.format(Locale.US, "%.1f%%", r.probability * 100));
        meter.setProgress((int) Math.round(r.probability * 100));
        riskText.setText(getString(R.string.risk_band, r.band));
        int color = r.band.equals("High") ? R.color.risk_high
                : r.band.equals("Moderate") ? R.color.risk_moderate : R.color.risk_low;
        riskText.setTextColor(ContextCompat.getColor(this, color));
        recommendationText.setText(r.recommendation);
        exposureText.setText(getString(R.string.exposure_line, r.annualBilling, r.expectedAnnualExposure));
        driversText.setText(drivers(r));
    }

    private String drivers(ChurnModel.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.raising)).append('\n');
        append(sb, r.raising);
        sb.append('\n').append(getString(R.string.lowering)).append('\n');
        append(sb, r.lowering);
        return sb.toString().trim();
    }

    private void append(StringBuilder sb, List<ChurnModel.Driver> drivers) {
        if (drivers.isEmpty()) {
            sb.append("  (none)\n");
            return;
        }
        for (ChurnModel.Driver d : drivers) {
            sb.append(String.format(Locale.US, "  %+.1f pts  %s\n", d.points, d.label));
        }
    }

    /** The first {@code n} characters of a hash or commit id, for display. */
    static String shortHash(String value, int n) {
        return value.length() <= n ? value : value.substring(0, n);
    }
}
