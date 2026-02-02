package com.teste.adb;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class QuestMonitorService extends Service {

    private static final String CHANNEL_ID = "quest_monitor";
    private static final long INTERVAL = 60_000; // 1 minuto

    private Handler handler;
    private Runnable task;
    private DatabaseReference deviceRef;

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        criarCanal();
        startForeground(1, criarNotificacao());

        deviceRef = FirebaseDatabase
                .getInstance()
                .getReference("devices")
                .child(DeviceSession.deviceId); // vem da Activity

        handler = new Handler(Looper.getMainLooper());
        task = new Runnable() {
            @Override
            public void run() {
                enviarDados();
                handler.postDelayed(this, INTERVAL);
            }
        };

        handler.post(task);
    }

    private void enviarDados() {
        Map<String, Object> data = new HashMap<>();
        data.put("battery", getBattery());
        data.put("ip", getIp());
        data.put("lastUpdate", System.currentTimeMillis());

        deviceRef.updateChildren(data);
    }

    private int getBattery() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm != null
                ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                : -1;
    }

    private String getIp() {
        WifiManager wm =
                (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wi = wm.getConnectionInfo();
        int ip = wi.getIpAddress();
        return (ip & 255) + "." +
                ((ip >> 8) & 255) + "." +
                ((ip >> 16) & 255) + "." +
                ((ip >> 24) & 255);
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Quest Monitor",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification criarNotificacao() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Quest Controller ativo")
                .setContentText("Monitorando bateria e status")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(task);
        super.onDestroy();
    }
}
