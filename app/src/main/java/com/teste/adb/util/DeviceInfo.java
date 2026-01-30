package com.teste.adb.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.os.BatteryManager;

public class DeviceInfo {

    public static int getBatteryLevel(Context context) {
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, iFilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        return (int) ((level / (float) scale) * 100);
    }

    public static String getLocalIp(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        return Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
    }

    public static String getSavedEmail(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE);
        return prefs.getString("email", null);
    }

    public static void saveEmail(Context context, String email) {
        SharedPreferences prefs =
                context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("email", email).apply();
    }
}


