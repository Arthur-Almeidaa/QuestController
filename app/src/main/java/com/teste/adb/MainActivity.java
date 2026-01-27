package com.teste.adb;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

import com.teste.adb.util.DeviceInfo;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference ref = database.getReference("devices").child("teste1");

        int battery = DeviceInfo.getBatteryLevel(this);
        String ip = DeviceInfo.getLocalIp(this);

        Map<String, Object> data = new HashMap<>();
        data.put("battery", battery);
        data.put("ip", ip);
        data.put("timestamp", System.currentTimeMillis());

        ref.setValue(data);
    }
}