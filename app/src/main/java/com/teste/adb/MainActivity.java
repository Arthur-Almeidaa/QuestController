package com.teste.adb;

import android.content.Intent;
import android.content.SharedPreferences;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "QuestControllerPrefs";
    private static final String KEY_EMAIL = "email";

    private EditText edtEmail;
    private TextView txtEmailAtual, txtStatus, txtBattery, txtIp, txtLastUpdate, txtCurrentApp, txtLog;
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
        txtEmailAtual = findViewById(R.id.txtEmailAtual);
        btnSalvar = findViewById(R.id.btnSaveEmail);
        btnTrocar = findViewById(R.id.btnTrocarEmail);

        txtStatus = findViewById(R.id.txtStatus);
        txtBattery = findViewById(R.id.txtBattery);
        txtIp = findViewById(R.id.txtIp);
        txtLastUpdate = findViewById(R.id.txtLastUpdate);
        txtCurrentApp = findViewById(R.id.txtCurrentApp);
        txtLog = findViewById(R.id.txtLog);

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
        String email = edtEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            Toast.makeText(this, "Email inválido", Toast.LENGTH_SHORT).show();
            return;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_EMAIL, email)
                .apply();

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
        deviceId = email.split("@")[0];
        rootRef = FirebaseDatabase.getInstance().getReference();
        deviceRef = rootRef.child("devices").child(deviceId);

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("ip", getIp());
        data.put("battery", getBattery());
        data.put("status", "online");
        data.put("lastUpdate", System.currentTimeMillis());

        deviceRef.setValue(data);

        carregarApps();
        ouvirComandos();
        ouvirStatus();
        iniciarHeartbeat();
        log("Conectado ao servidor");
    }

    /* ===================== APPS ===================== */

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

    private void executar(String commandId, String action) {

        try {
            Intent intent = null;

            // ===== JOGOS VR QUE EXIGEM INTENT EXPLÍCITO =====
            switch (action) {

                case "hyperdash":
                    intent = new Intent();
                    intent.setClassName(
                            "com.TriangleFactory.HyperDash",
                            "com.unity3d.player.UnityPlayerActivity"
                    );
                    break;

                case "gorillatag":
                    intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);
                    intent.addCategory("com.oculus.intent.category.VR");
                    intent.setClassName(
                            "com.AnotherAxiom.GorillaTag",
                            "com.unity3d.player.UnityPlayerActivity"
                    );
                    break;




                case "homeinvasion":
                    intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);
                    intent.addCategory("com.oculus.intent.category.VR");
                    intent.setClassName(
                            "com.soulassembly.homeinvasion",
                            "com.unity3d.player.UnityPlayerActivity"
                    );
                    break;

                // ===== TODOS OS OUTROS (DINÂMICO DO SITE) =====
                default:
                    String pkg = appPackages.get(action);

                    if (pkg == null || !isInstalled(pkg)) {
                        responder(commandId, "error", "App não instalado");
                        log("App não instalado: " + action);
                        return;
                    }

                    intent = getPackageManager().getLaunchIntentForPackage(pkg);

                    if (intent == null) {
                        intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_LAUNCHER);
                        intent.addCategory("com.oculus.intent.category.VR");
                        intent.setClassName(pkg, "com.unity3d.player.UnityPlayerActivity");
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
            log("Erro ao abrir " + action + ": " + e.getMessage());
        }
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
                txtCurrentApp.setText("App atual: " + s.child("currentApp").getValue());
                txtLastUpdate.setText("Última atualização: " +
                        hora(s.child("lastUpdate").getValue(Long.class)));
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    /* ===================== HEARTBEAT ===================== */

    private void iniciarHeartbeat() {
        heartbeat.postDelayed(new Runnable() {
            @Override
            public void run() {
                deviceRef.child("status").setValue("online");
                deviceRef.child("lastUpdate").setValue(System.currentTimeMillis());
                heartbeat.postDelayed(this, 15000);
            }
        }, 15000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceRef != null) {
            deviceRef.child("status").setValue("offline");
        }
        heartbeat.removeCallbacksAndMessages(null);
    }

    /* ===================== UTIL ===================== */

    private void log(String msg) {
        txtLog.append("\n" + hora(System.currentTimeMillis()) + " — " + msg);
    }

    private String hora(Long t) {
        if (t == null) return "-";
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(t));
    }

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int getBattery() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm != null
                ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                : -1;
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
}
