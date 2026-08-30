package com.seuapp;

import android.app.Activity;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private AppPrefs prefs;
    private TextView txtStatus;
    private TextView txtSummary;
    private Button btnAccessibility;
    private Button btnOverlay;
    private Button btnRefresh;
    private EditText excellentMinimumInput;
    private EditText goodMinimumInput;
    private boolean testClickLocked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = new AppPrefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        txtStatus = new TextView(this);
        txtStatus.setText("Verificando permissões...");
        txtStatus.setTextSize(16f);
        txtStatus.setGravity(Gravity.CENTER);
        txtStatus.setPadding(0, 0, 0, 16);

        TextView lblTitle = new TextView(this);
        lblTitle.setText("Configurar Valores por KM (R$/km)");
        lblTitle.setTextSize(18f);
        lblTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        lblTitle.setPadding(0, 16, 0, 12);

        TextView lblGood = new TextView(this);
        lblGood.setText("1. Mínimo para corrida BOA (R$/km):");
        lblGood.setTextSize(14f);

        goodMinimumInput = createMinimumInput(
            "Ex: 2.00", prefs.goodMinimum());

        TextView lblExcellent = new TextView(this);
        lblExcellent.setText("2. Mínimo para corrida EXCELENTE (R$/km):");
        lblExcellent.setTextSize(14f);
        lblExcellent.setPadding(0, 12, 0, 0);

        excellentMinimumInput = createMinimumInput(
            "Ex: 3.00", prefs.excellentMinimum());

        txtSummary = new TextView(this);
        txtSummary.setTextSize(14f);
        txtSummary.setPadding(16, 16, 16, 16);
        txtSummary.setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0"));
        txtSummary.setLineSpacing(6f, 1f);

        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateClassificationsSummary();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        };
        goodMinimumInput.addTextChangedListener(watcher);
        excellentMinimumInput.addTextChangedListener(watcher);

        Button btnSaveConfiguration = new Button(this);
        btnSaveConfiguration.setText("Salvar Configuração");

        Button btnTestClick = new Button(this);
        btnTestClick.setText("Testar Clique na Mira");

        btnAccessibility = new Button(this);
        btnAccessibility.setText("Abrir Acessibilidade");

        btnOverlay = new Button(this);
        btnOverlay.setText("Conceder Sobreposição");

        btnRefresh = new Button(this);
        btnRefresh.setText("Atualizar Status");

        Button btnToggleRobot = new Button(this);
        btnToggleRobot.setText(prefs.enabled() ? "PAUSAR ROBÔ (⏸)" : "ATIVAR ROBÔ (▶)");
        btnToggleRobot.setTextSize(16f);
        btnToggleRobot.setTypeface(null, android.graphics.Typeface.BOLD);
        btnToggleRobot.setPadding(0, 20, 0, 20);
        btnToggleRobot.setOnClickListener(v -> {
            boolean newState = !prefs.enabled();
            prefs.setEnabled(newState);
            btnToggleRobot.setText(newState ? "PAUSAR ROBÔ (⏸)" : "ATIVAR ROBÔ (▶)");
            Toast.makeText(this, newState ? "▶️ Robô Ativado!" : "⏸️ Robô Pausado!", Toast.LENGTH_SHORT).show();
            sendBroadcast(new Intent(RideVoiceService.ACTION_TEST_CLICK).setPackage(getPackageName()));
        });

        root.addView(txtStatus);
        root.addView(btnToggleRobot);
        root.addView(lblTitle);
        root.addView(lblGood);
        root.addView(goodMinimumInput);
        root.addView(lblExcellent);
        root.addView(excellentMinimumInput);
        root.addView(txtSummary);
        root.addView(btnSaveConfiguration);
        root.addView(btnTestClick);
        root.addView(btnAccessibility);
        root.addView(btnOverlay);
        root.addView(btnRefresh);

        setContentView(root);

        updateClassificationsSummary();

        btnAccessibility.setOnClickListener(v -> {
            prefs.setAccessibilityPermissionRequested(true);
            openAccessibilitySettings();
        });

        btnOverlay.setOnClickListener(v -> {
            prefs.setOverlayPermissionRequested(true);
            requestOverlayPermission();
        });

        btnRefresh.setOnClickListener(v -> checkPermissions());
        btnSaveConfiguration.setOnClickListener(v -> saveConfiguration());
        btnTestClick.setOnClickListener(v -> testClick());

        checkPermissions();
    }

    private void updateClassificationsSummary() {
        try {
            double good = parseMinimum(goodMinimumInput);
            double excellent = parseMinimum(excellentMinimumInput);

            if (good > excellent) {
                txtSummary.setText("⚠️ O valor de Excelente deve ser maior ou igual ao de Boa.");
                txtSummary.setTextColor(android.graphics.Color.RED);
                return;
            }

            txtSummary.setTextColor(android.graphics.Color.BLACK);
            txtSummary.setText(String.format(java.util.Locale.US,
                "Regra dos 3 Tipos de Corrida:\n" +
                "🔴 RUIM: Menos que R$ %.2f/km (IGNORA / Não Aceita)\n" +
                "🟡 BOA: De R$ %.2f a R$ %.2f/km (ACEITA AUTO)\n" +
                "🟢 EXCELENTE: A partir de R$ %.2f/km (ACEITA AUTO)",
                good, good, excellent, excellent
            ));
        } catch (Exception e) {
            txtSummary.setText("Digite valores válidos nos campos acima.");
        }
    }

    private EditText createMinimumInput(String hint, double value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(String.format(java.util.Locale.US, "%.2f", value));
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSelectAllOnFocus(true);
        input.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private void saveConfiguration() {
        try {
            double excellentMinimum = parseMinimum(excellentMinimumInput);
            double goodMinimum = parseMinimum(goodMinimumInput);

            if (goodMinimum > excellentMinimum) {
                throw new IllegalArgumentException();
            }

            prefs.setExcellentMinimum(excellentMinimum);
            prefs.setGoodMinimum(goodMinimum);
            updateClassificationsSummary();
            Toast.makeText(this, "Configurações salvas com sucesso!", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this,
                    "Informe valores válidos; o mínimo Excelente deve ser maior ou igual ao Boa.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private double parseMinimum(EditText input) {
        String value = input.getText().toString().trim().replace(',', '.');
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed) || parsed < 0) {
            throw new IllegalArgumentException();
        }
        return parsed;
    }

    private void testClick() {
        if (testClickLocked) {
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this,
                    "Ative o RideVoiceService em Acessibilidade primeiro.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        sendBroadcast(new Intent(RideVoiceService.ACTION_TEST_CLICK)
                .setPackage(getPackageName()));
        testClickLocked = true;
        new android.os.Handler().postDelayed(() -> testClickLocked = false, 1000);
        Toast.makeText(this, "Comando de teste enviado para a mira.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    private void checkPermissions() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        boolean overlayEnabled = hasOverlayPermission();

        if (!accessibilityEnabled) {
            prefs.setAccessibilityPermissionRequested(true);
            txtStatus.setText("Acessibilidade pendente: ative o RideVoiceService.");
            btnAccessibility.setVisibility(View.VISIBLE);
            btnAccessibility.setText("Abrir acessibilidade");
            btnOverlay.setVisibility(View.GONE);
            btnRefresh.setVisibility(View.VISIBLE);
            return;
        }

        prefs.setAccessibilityPermissionRequested(false);

        if (!overlayEnabled) {
            prefs.setOverlayPermissionRequested(true);
            txtStatus.setText("Sobreposição pendente: conceda a permissão de sobreposição.");
            btnAccessibility.setVisibility(View.VISIBLE);
            btnOverlay.setVisibility(View.VISIBLE);
            btnOverlay.setText("Conceder sobreposição");
            btnRefresh.setVisibility(View.VISIBLE);
            return;
        }

        prefs.setOverlayPermissionRequested(false);
        txtStatus.setText("Tudo pronto. O serviço pode funcionar corretamente.");
        btnAccessibility.setVisibility(View.GONE);
        btnOverlay.setVisibility(View.GONE);
        btnRefresh.setVisibility(View.VISIBLE);

        Toast.makeText(this, "Permissões OK.", Toast.LENGTH_SHORT).show();
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName expectedService = new ComponentName(this, RideVoiceService.class);
        String expectedId = expectedService.flattenToString();
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (enabledServices != null) {
            for (String serviceId : enabledServices.split(":")) {
                if (expectedId.equalsIgnoreCase(serviceId)) {
                    return true;
                }
            }
        }

        AccessibilityManager accessibilityManager =
                (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);

        if (accessibilityManager == null) {
            return false;
        }

        for (AccessibilityServiceInfo info :
                accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {

            String serviceId = info.getId();
            if (serviceId != null && serviceId.equals(expectedService.flattenToString())) {
                return true;
            }
        }

        return false;
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return Settings.canDrawOverlays(this);
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else {
            Toast.makeText(this, "Não é necessário em versões antigas do Android.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (hasOverlayPermission()) {
                prefs.setOverlayPermissionRequested(false);
                Toast.makeText(this, "Permissão de sobreposição concedida.", Toast.LENGTH_SHORT).show();
            } else {
                prefs.setOverlayPermissionRequested(true);
                Toast.makeText(this, "Permissão de sobreposição ainda pendente.", Toast.LENGTH_SHORT).show();
            }

            checkPermissions();
        }
    }
}
