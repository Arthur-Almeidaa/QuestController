package com.teste.adb;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class EmailActivity extends AppCompatActivity {

    EditText emailInput;
    Button saveEmailButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        emailInput = findViewById(R.id.emailInput);
        saveEmailButton = findViewById(R.id.saveEmailButton);

        saveEmailButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();

            if (email.isEmpty()) {
                Toast.makeText(this, "Digite um email", Toast.LENGTH_SHORT).show();
                return;
            }

            enviarEmailFirebase(email);
        });
    }

    private void enviarEmailFirebase(String email) {
        String deviceId = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(deviceId);

        ref.child("email").setValue(email)
                .addOnSuccessListener(unused -> {
                    // Salva local
                    SharedPreferences prefs =
                            getSharedPreferences("app_prefs", MODE_PRIVATE);
                    prefs.edit().putString("user_email", email).apply();

                    Toast.makeText(this, "Email salvo!", Toast.LENGTH_SHORT).show();

                    // Vai para MainActivity
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Erro Firebase: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }
}
