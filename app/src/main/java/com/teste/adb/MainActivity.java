package com.teste.adb;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.teste.adb.util.DeviceInfo;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    EditText emailInput;
    Button saveEmailButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        emailInput = findViewById(R.id.emailInput);
        saveEmailButton = findViewById(R.id.saveEmailButton);

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String email = prefs.getString("user_email", null);

        if (email == null) {
            // 🔹 Primeira vez → mostra input
            emailInput.setVisibility(View.VISIBLE);
            saveEmailButton.setVisibility(View.VISIBLE);

            saveEmailButton.setOnClickListener(v -> {
                String typedEmail = emailInput.getText().toString().trim();

                if (typedEmail.isEmpty()) {
                    Toast.makeText(this, "Digite um email", Toast.LENGTH_SHORT).show();
                    return;
                }

                salvarEmail(typedEmail);
            });

        } else {
            // 🔹 Email já existe → envia dados
            enviarDados(email);
        }
    }

    private void salvarEmail(String email) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        prefs.edit().putString("user_email", email).apply();

        enviarDados(email);

        emailInput.setVisibility(View.GONE);
        saveEmailButton.setVisibility(View.GONE);

        Toast.makeText(this, "Email salvo!", Toast.LENGTH_SHORT).show();
    }

    private void enviarDados(String email) {

        String nomeDispositivo = email.split("@")[0];

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(nomeDispositivo);

        int battery = DeviceInfo.getBatteryLevel(this);
        String ip = DeviceInfo.getLocalIp(this);

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("battery", battery);
        data.put("ip", ip);
        data.put("timestamp", System.currentTimeMillis());

        ref.updateChildren(data);
    }

}
