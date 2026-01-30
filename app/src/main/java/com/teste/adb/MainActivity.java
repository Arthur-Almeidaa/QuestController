package com.teste.adb;

import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
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

    private DatabaseReference deviceRef;
    private EditText edtEmail;
    private String deviceName;
    private ValueEventListener commandListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "🚀 App iniciado");

        edtEmail = findViewById(R.id.editEmail);

        Button btnSalvar = findViewById(R.id.btnSaveEmail);
        Button btnBeatSaber = findViewById(R.id.btnBeatSaber);
        Button btnBlaston = findViewById(R.id.btnBlaston);
        Button btnHyperDash = findViewById(R.id.btnHyperDash);
        Button btnGoogle = findViewById(R.id.btnBrowser);

        btnSalvar.setOnClickListener(v -> salvarDados());

        btnBeatSaber.setOnClickListener(v ->
                abrirAppPadrao("com.beatgames.beatsaber")
        );

        btnBlaston.setOnClickListener(v ->
                abrirAppPadrao("com.resolutiongames.blaston")
        );

        btnHyperDash.setOnClickListener(v ->
                abrirHyperDash()
        );

        btnGoogle.setOnClickListener(v ->
                abrirAppPadrao("com.android.chrome")
        );
    }

    /* ===================== FIREBASE ===================== */

    private void salvarDados() {
        String email = edtEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            Toast.makeText(this, "Digite um email válido", Toast.LENGTH_SHORT).show();
            return;
        }

        // Nome do dispositivo = antes do @
        deviceName = email.split("@")[0];
        Log.d(TAG, "📱 Device name: " + deviceName);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        deviceRef = database.getReference("devices").child(deviceName);

        int battery = getBatteryLevel();
        String ip = getLocalIp();

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("battery", battery);
        data.put("ip", ip);
        data.put("lastUpdate", System.currentTimeMillis());

        Log.d(TAG, "📤 Enviando dados para Firebase: " + data.toString());

        deviceRef.setValue(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "✅ Dados enviados para o Firebase", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "✅ Dados enviados com sucesso");
                    // Inicia o listener de comandos após salvar
                    iniciarListenerComandos();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "❌ Erro ao enviar dados", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "❌ Erro ao enviar dados", e);
                });
    }

    /* ===================== LISTENER DE COMANDOS ===================== */

    private void iniciarListenerComandos() {
        if (deviceRef == null || deviceName == null) {
            Log.e(TAG, "❌ deviceRef ou deviceName é null");
            return;
        }

        DatabaseReference commandRef = deviceRef.child("command");
        Log.d(TAG, "👂 Iniciando listener em: devices/" + deviceName + "/command");

        // Remove listener anterior se existir
        if (commandListener != null) {
            commandRef.removeEventListener(commandListener);
            Log.d(TAG, "🗑️ Listener anterior removido");
        }

        commandListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Log.d(TAG, "🔔 onDataChange disparado!");
                Log.d(TAG, "📊 Snapshot exists: " + snapshot.exists());

                if (snapshot.exists()) {
                    String action = snapshot.child("action").getValue(String.class);
                    Long timestamp = snapshot.child("timestamp").getValue(Long.class);

                    Log.d(TAG, "📥 Comando recebido:");
                    Log.d(TAG, "   - action: " + action);
                    Log.d(TAG, "   - timestamp: " + timestamp);

                    if (action != null) {
                        processarComando(action);

                        // Remove o comando após processar
                        commandRef.removeValue()
                                .addOnSuccessListener(unused ->
                                        Log.d(TAG, "🗑️ Comando removido do Firebase"))
                                .addOnFailureListener(e ->
                                        Log.e(TAG, "❌ Erro ao remover comando", e));
                    } else {
                        Log.w(TAG, "⚠️ Action é null");
                    }
                } else {
                    Log.d(TAG, "ℹ️ Nenhum comando no Firebase");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MainActivity.this,
                        "❌ Erro ao escutar comandos", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "❌ Listener cancelado: " + error.getMessage());
            }
        };

        commandRef.addValueEventListener(commandListener);

        Toast.makeText(this, "👂 Aguardando comandos remotos...", Toast.LENGTH_LONG).show();
        Log.d(TAG, "✅ Listener registrado com sucesso");
    }

    /* ===================== PROCESSAR COMANDO REMOTO ===================== */

    private void processarComando(String comando) {
        Log.d(TAG, "⚙️ Processando comando: " + comando);
        Toast.makeText(this, "🎮 Executando: " + comando, Toast.LENGTH_SHORT).show();

        switch (comando.toLowerCase()) {
            case "beatsaber":
                Log.d(TAG, "🎮 Abrindo Beat Saber");
                abrirAppPadrao("com.beatgames.beatsaber");
                break;

            case "blaston":
                Log.d(TAG, "🔫 Abrindo Blaston");
                abrirAppPadrao("com.resolutiongames.blaston");
                break;

            case "hyperdash":
                Log.d(TAG, "⚡ Abrindo Hyper Dash");
                abrirHyperDash();
                break;

            case "chrome":
                Log.d(TAG, "🌐 Abrindo Chrome");
                abrirAppPadrao("com.android.chrome");
                break;

            default:
                Log.w(TAG, "⚠️ Comando não reconhecido: " + comando);
                Toast.makeText(this, "❌ Comando não reconhecido: " + comando,
                        Toast.LENGTH_SHORT).show();
        }
    }

    /* ===================== INFO DO DISPOSITIVO ===================== */

    private int getBatteryLevel() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm != null) {
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
        return -1;
    }

    private String getLocalIp() {
        try {
            WifiManager wifiManager =
                    (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

            if (wifiManager == null) return "unknown";

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipInt = wifiInfo.getIpAddress();

            return (ipInt & 0xFF) + "." +
                    ((ipInt >> 8) & 0xFF) + "." +
                    ((ipInt >> 16) & 0xFF) + "." +
                    ((ipInt >> 24) & 0xFF);
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao obter IP", e);
            return "unknown";
        }
    }

    /* ===================== ABRIR APPS ===================== */

    private void abrirAppPadrao(String packageName) {
        try {
            Log.d(TAG, "🚀 Tentando abrir: " + packageName);

            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                if (deviceRef != null) {
                    deviceRef.child("lastApp").setValue(packageName);
                }

                Log.d(TAG, "✅ App aberto com sucesso");
                Toast.makeText(this, "✅ App aberto!", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "⚠️ App não encontrado: " + packageName);
                Toast.makeText(this, "❌ App não instalado", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao abrir app: " + packageName, e);
            Toast.makeText(this, "❌ Erro ao abrir app", Toast.LENGTH_SHORT).show();
        }
    }

    private void abrirHyperDash() {
        try {
            Log.d(TAG, "⚡ Tentando abrir Hyper Dash");

            Intent intent = new Intent();
            intent.setClassName(
                    "com.TriangleFactory.HyperDash",
                    "com.unity3d.player.UnityPlayerActivity"
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            if (deviceRef != null) {
                deviceRef.child("lastApp").setValue("Hyper Dash");
            }

            Log.d(TAG, "✅ Hyper Dash aberto com sucesso");
            Toast.makeText(this, "✅ Hyper Dash aberto!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao abrir Hyper Dash", e);
            Toast.makeText(this, "❌ Não foi possível abrir o Hyper Dash", Toast.LENGTH_SHORT).show();
        }
    }

    /* ===================== LIFECYCLE ===================== */

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "📱 onResume - App voltou ao foreground");

        // Reconecta o listener se já tiver salvo dados
        if (deviceName != null && deviceRef != null) {
            Log.d(TAG, "🔄 Reconectando listener...");
            iniciarListenerComandos();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "💤 onPause - App foi para background");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "💀 onDestroy - App sendo destruído");

        // Remove o listener quando o app for destruído
        if (commandListener != null && deviceRef != null) {
            deviceRef.child("command").removeEventListener(commandListener);
            Log.d(TAG, "🗑️ Listener removido no onDestroy");
        }
    }
}