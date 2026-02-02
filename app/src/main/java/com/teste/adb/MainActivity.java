package com.teste.adb;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "QuestControllerPrefs";
    private static final String KEY_EMAIL = "email";

    private EditText edtEmail;
    private TextView txtStatus, txtBattery, txtIp, txtLastUpdate, txtCurrentApp, txtLog;
    private Button btnSalvar, btnTrocar;

    private DatabaseReference rootRef, deviceRef;
    private String deviceId;

    private final Map<String, String> appPackages = new HashMap<>();
    private final Handler heartbeat = new Handler(Looper.getMainLooper());

    /* ===================== ON CREATE ===================== */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        edtEmail = findViewById(R.id.editEmail);
        txtStatus = findViewById(R.id.txtStatus);
        txtBattery = findViewById(R.id.txtBattery);
        txtIp = findViewById(R.id.txtIp);
        txtLastUpdate = findViewById(R.id.txtLastUpdate);
        txtCurrentApp = findViewById(R.id.txtCurrentApp);
        txtLog = findViewById(R.id.txtLog);

        btnSalvar = findViewById(R.id.btnSaveEmail);
        btnTrocar = findViewById(R.id.btnTrocarEmail);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(KEY_EMAIL, null);

        btnSalvar.setOnClickListener(v -> salvarEmail());
        btnTrocar.setOnClickListener(v -> trocarEmail());

        if (saved != null) {
            edtEmail.setText(saved);
            edtEmail.setEnabled(false);
            conectar(saved);
        }
    }

    /* ===================== EMAIL ===================== */

    private void salvarEmail() {
        String input = edtEmail.getText().toString().trim();

        if (TextUtils.isEmpty(input)) {
            Toast.makeText(this, "Digite o email", Toast.LENGTH_SHORT).show();
            return;
        }

        String email;

        // 👉 Se NÃO tiver "@", completa com @gmail.com
        if (!input.contains("@")) {
            email = input + "@gmail.com";
        } else {
            email = input;
        }

        // Validação final simples
        if (!email.contains("@") || !email.contains(".")) {
            Toast.makeText(this, "Email inválido", Toast.LENGTH_SHORT).show();
            return;
        }

        // Salva no SharedPreferences
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_EMAIL, email)
                .apply();

        // Atualiza o campo visualmente já com o email completo
        edtEmail.setText(email);
        edtEmail.setEnabled(false);

        conectar(email);
    }

    private void trocarEmail() {
        if (deviceRef != null) deviceRef.child("status").setValue("offline");
        edtEmail.setEnabled(true);
        edtEmail.setText("");
    }

    /* ===================== FIREBASE ===================== */

    private void conectar(String email) {

        // 1️⃣ Define o deviceId PRIMEIRO
        deviceId = email.split("@")[0];
        DeviceSession.deviceId = deviceId;

        // 2️⃣ Inicializa Firebase
        rootRef = FirebaseDatabase.getInstance().getReference();
        deviceRef = rootRef.child("devices").child(deviceId);

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("ip", getIp());
        data.put("battery", getBattery());
        data.put("status", "online");
        data.put("lastUpdate", System.currentTimeMillis());

        deviceRef.setValue(data);

        // 3️⃣ SÓ AGORA inicia o Service
        Intent service = new Intent(this, QuestMonitorService.class);
        startForegroundService(service);

        // 4️⃣ Restante da lógica
        carregarApps();
        ouvirComandos();
        ouvirStatus();

        log("Conectado ao servidor");
    }

    private void carregarApps() {
        rootRef.child("availableApps").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                appPackages.clear();
                for (DataSnapshot s : snap.getChildren()) {
                    String id = s.child("id").getValue(String.class);
                    String pkg = s.child("packageName").getValue(String.class);
                    if (id != null && pkg != null) {
                        appPackages.put(id, pkg);
                    }
                }
                log("Apps sincronizados: " + appPackages.size());
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    /* ===================== COMANDOS ===================== */

    private void ouvirComandos() {
        deviceRef.child("command").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) return;

                String commandId = snap.child("commandId").getValue(String.class);
                String action = snap.child("action").getValue(String.class);

                if (commandId == null || action == null) return;

                executar(commandId, action);
                snap.getRef().removeValue();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    /* ===================== EXECUTAR ===================== */

    private void executar(String commandId, String action) {
        try {
            Intent intent;

            // ===== HOME =====
            if ("home".equals(action)) {
                voltarAoMenu();
                responder(commandId, "success", "Menu principal");
                return;
            }

            // ===== BEAT SABER (MANUAL - BLOQUEADO PELO SISTEMA) =====
            if ("beatsaber".equals(action)) {
                responder(
                        commandId,
                        "info",
                        "Abra o Beat Saber manualmente no menu do Quest"
                );
                log("🎵 Beat Saber é protegido pelo sistema");
                return;
            }

            // ===== APPS VR FIXOS =====
            switch (action) {

                case "hyperdash":
                    intent = new Intent();
                    intent.setClassName(
                            "com.TriangleFactory.HyperDash",
                            "com.unity3d.player.UnityPlayerActivity"
                    );
                    break;

                case "blaston":
                    intent = vrIntent("com.resolutiongames.ignis");
                    break;

                case "homeinvasion":
                    intent = vrIntent("com.soulassembly.homeinvasion");
                    break;

                case "trolin":
                    intent = vrIntent("com.resolutiongames.trolin");
                    break;

                case "creed":
                    intent = new Intent();
                    intent.setClassName(
                            "com.survios.Creed",
                            "com.unity3d.player.UnityPlayerActivity"
                    );
                    break;

                // ===== APPS NORMAIS (DINÂMICO DO FIREBASE) =====
                default:
                    String pkg = appPackages.get(action);
                    if (pkg == null) {
                        responder(commandId, "error", "App não encontrado");
                        log("App não encontrado: " + action);
                        return;
                    }

                    intent = getLauncherIntent(pkg);
                    if (intent == null) {
                        responder(commandId, "error", "Launcher não encontrado");
                        log("Launcher não encontrado: " + pkg);
                        return;
                    }
                    break;
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            deviceRef.child("currentApp").setValue(action);
            responder(commandId, "success", "App iniciado");
            log("App iniciado: " + action);

        } catch (Exception e) {
            responder(commandId, "error", "Falha ao abrir app");
            log("Erro: " + e.getMessage());
        }
    }

    /* ===================== HELPERS ===================== */

    private Intent vrIntent(String pkg) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.addCategory("com.oculus.intent.category.VR");
        i.setClassName(pkg, "com.unity3d.player.UnityPlayerActivity");
        return i;
    }

    private Intent getLauncherIntent(String packageName) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(packageName);

        List<ResolveInfo> list =
                getPackageManager().queryIntentActivities(intent, 0);

        if (list != null && !list.isEmpty()) {
            ResolveInfo ri = list.get(0);
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.setClassName(
                    ri.activityInfo.packageName,
                    ri.activityInfo.name
            );
            return launch;
        }
        return null;
    }

    private void voltarAoMenu() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void responder(String id, String status, String msg) {
        Map<String, Object> r = new HashMap<>();
        r.put("commandId", id);
        r.put("status", status);
        r.put("message", msg);
        deviceRef.child("commandResponse").setValue(r);
    }

    /* ===================== STATUS ===================== */

    private void ouvirStatus() {
        deviceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                txtStatus.setText("Status: " + s.child("status").getValue());
                txtBattery.setText("Bateria: " + getBattery() + "%");
                txtIp.setText("IP: " + getIp());
                txtCurrentApp.setText("App: " + s.child("currentApp").getValue());
                txtLastUpdate.setText(hora(s.child("lastUpdate").getValue(Long.class)));
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    /* ===================== HEARTBEAT ===================== */

    private void iniciarHeartbeat() {
        heartbeat.postDelayed(new Runnable() {
            @Override
            public void run() {
                deviceRef.child("battery").setValue(getBattery());
                deviceRef.child("lastUpdate").setValue(System.currentTimeMillis());
                heartbeat.postDelayed(this, 15000);
            }
        }, 15000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceRef != null) deviceRef.child("status").setValue("offline");
        heartbeat.removeCallbacksAndMessages(null);
    }

    /* ===================== UTIL ===================== */

    private int getBattery() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
    }

    private String getIp() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wi = wm.getConnectionInfo();
        int ip = wi.getIpAddress();
        return (ip & 255) + "." +
                ((ip >> 8) & 255) + "." +
                ((ip >> 16) & 255) + "." +
                ((ip >> 24) & 255);
    }

    private void log(String msg) {
        txtLog.append("\n" + hora(System.currentTimeMillis()) + " — " + msg);
    }

    private String hora(Long t) {
        if (t == null) return "-";
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(t));
    }
}
