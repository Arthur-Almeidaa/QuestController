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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "QuestController";
    private static final String PREFS_NAME = "QuestControllerPrefs";
    private static final String KEY_EMAIL = "saved_email";

    private SharedPreferences prefs;
    private DatabaseReference deviceRef;
    private ValueEventListener commandListener;
    private Handler heartbeatHandler = new Handler(Looper.getMainLooper());

    private EditText edtEmail;
    private TextView txtEmailAtual;
    private Button btnSalvar, btnTrocarEmail;

    private String deviceName;

    /* ===================== LIFECYCLE ===================== */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        edtEmail = findViewById(R.id.editEmail);
        txtEmailAtual = findViewById(R.id.txtEmailAtual);
        btnSalvar = findViewById(R.id.btnSaveEmail);
        btnTrocarEmail = findViewById(R.id.btnTrocarEmail);

        // BOTÕES DOS JOGOS
        findViewById(R.id.btnHyperDash).setOnClickListener(v -> executar("hyperdash"));
        findViewById(R.id.btnHomeInvasion).setOnClickListener(v -> executar("homeinvasion"));
        findViewById(R.id.btnBeatSaber).setOnClickListener(v -> executar("beatsaber"));
        findViewById(R.id.btnBlaston).setOnClickListener(v -> executar("blaston"));
        findViewById(R.id.btnCreed).setOnClickListener(v -> executar("creed"));
        findViewById(R.id.btnSpatialOps).setOnClickListener(v -> executar("spatialops"));

        btnSalvar.setOnClickListener(v -> salvarEmail());
        btnTrocarEmail.setOnClickListener(v -> trocarEmail());

        carregarEmail();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        heartbeatHandler.removeCallbacksAndMessages(null);

        if (commandListener != null && deviceRef != null) {
            deviceRef.child("command").removeEventListener(commandListener);
        }

        if (deviceRef != null) {
            deviceRef.child("status").setValue("offline");
        }
    }

    /* ===================== EMAIL ===================== */

    private void carregarEmail() {
        String email = prefs.getString(KEY_EMAIL, null);
        if (email != null) {
            edtEmail.setText(email);
            edtEmail.setEnabled(false);
            txtEmailAtual.setText("Conectado: " + email);
            txtEmailAtual.setVisibility(View.VISIBLE);
            btnSalvar.setVisibility(View.GONE);
            btnTrocarEmail.setVisibility(View.VISIBLE);

            deviceName = email.split("@")[0];
            conectarFirebase(email);
        }
    }

    private void salvarEmail() {
        String email = edtEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            Toast.makeText(this, "Email inválido", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit().putString(KEY_EMAIL, email).apply();
        deviceName = email.split("@")[0];
        conectarFirebase(email);
    }

    private void trocarEmail() {
        if (deviceRef != null) {
            deviceRef.child("status").setValue("offline");
        }

        edtEmail.setEnabled(true);
        edtEmail.setText("");
        btnSalvar.setVisibility(View.VISIBLE);
        btnTrocarEmail.setVisibility(View.GONE);
        txtEmailAtual.setVisibility(View.GONE);
    }

    /* ===================== FIREBASE ===================== */

    private void conectarFirebase(String email) {
        deviceRef = FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(deviceName);

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("ip", getLocalIp());
        data.put("battery", getBatteryLevel());
        data.put("status", "online");
        data.put("lastUpdate", System.currentTimeMillis());
        data.put("installedApps", detectarJogos());

        deviceRef.setValue(data).addOnSuccessListener(v -> {
            iniciarListenerComandos();
            iniciarHeartbeat();
            Toast.makeText(this, "Conectado ao servidor", Toast.LENGTH_SHORT).show();
        });
    }

    /* ===================== HEARTBEAT ===================== */

    private void iniciarHeartbeat() {
        heartbeatHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (deviceRef != null) {
                    deviceRef.child("status").setValue("online");
                    deviceRef.child("lastUpdate").setValue(System.currentTimeMillis());
                }
                heartbeatHandler.postDelayed(this, 15000);
            }
        }, 15000);
    }

    /* ===================== LISTENER ===================== */

    private void iniciarListenerComandos() {
        DatabaseReference commandRef = deviceRef.child("command");

        commandListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                if (!snap.exists()) return;

                String action = snap.child("action").getValue(String.class);
                if (action == null) return;

                executar(action);
                commandRef.removeValue();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Erro listener", error.toException());
            }
        };

        commandRef.addValueEventListener(commandListener);
    }

    /* ===================== EXECUÇÃO ===================== */

    private boolean executar(String comando) {

        switch (comando) {

            case "hyperdash":
                abrirHyperDash();
                return true;

            case "homeinvasion":
                abrirHomeInvasion();
                return true;

            case "beatsaber":
                return abrirSeInstalado("com.beatgames.beatsaber", "Beat Saber");

            case "blaston":
                return abrirSeInstalado("com.resolutiongames.ignis", "Blaston");

            case "creed":
                return abrirSeInstalado("com.survios.creed", "Creed");

            case "spatialops":
                return abrirSeInstalado("com.resolutiongames.spatialops", "Spatial Ops");

            default:
                return false;
        }
    }

    /* ===================== HELPERS ===================== */

    private boolean abrirSeInstalado(String pkg, String nome) {
        if (!isPackageInstalled(pkg)) {
            Toast.makeText(this, nome + " não instalado", Toast.LENGTH_SHORT).show();
            return false;
        }
        return abrirMonkeyLike(pkg);
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean abrirMonkeyLike(String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setPackage(packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Falha ao abrir " + packageName, e);
            return false;
        }
    }

    /* ===================== JOGOS VR (INTENT EXPLÍCITO) ===================== */

    private void abrirHyperDash() {
        Intent i = new Intent();
        i.setClassName(
                "com.TriangleFactory.HyperDash",
                "com.unity3d.player.UnityPlayerActivity"
        );
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void abrirHomeInvasion() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.addCategory("com.oculus.intent.category.VR");
            i.setClassName(
                    "com.soulassembly.homeinvasion",
                    "com.unity3d.player.UnityPlayerActivity"
            );
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Falha ao abrir Home Invasion", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Erro Home Invasion", e);
        }
    }

    /* ===================== INFO ===================== */

    private int getBatteryLevel() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm != null
                ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                : -1;
    }

    private String getLocalIp() {
        WifiManager wm = (WifiManager) getApplicationContext()
                .getSystemService(WIFI_SERVICE);
        if (wm == null) return "unknown";
        WifiInfo wi = wm.getConnectionInfo();
        int ip = wi.getIpAddress();
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 24) & 0xFF);
    }

    /* ===================== JOGOS INSTALADOS ===================== */

    private Map<String, Boolean> detectarJogos() {
        Map<String, Boolean> jogos = new HashMap<>();

        jogos.put("hyperdash", isPackageInstalled("com.TriangleFactory.HyperDash"));
        jogos.put("homeinvasion", isPackageInstalled("com.soulassembly.homeinvasion"));
        jogos.put("beatsaber", isPackageInstalled("com.beatgames.beatsaber"));
        jogos.put("blaston", isPackageInstalled("com.resolutiongames.ignis"));
        jogos.put("creed", isPackageInstalled("com.survios.creed"));
        jogos.put("spatialops", isPackageInstalled("com.resolutiongames.spatialops"));

        return jogos;
    }
}
