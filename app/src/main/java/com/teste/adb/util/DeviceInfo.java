package com.teste.adb.util;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.text.format.Formatter;

public class DeviceInfo {

    public static int getBatteryLevel(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        if (batteryStatus == null) return -1;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        return (int) ((level / (float) scale) * 100);
    }

    public static String getLocalIp(Context context) {
        try {
            WifiManager wifiManager =
                    (WifiManager) context.getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);

            if (wifiManager == null) return "0.0.0.0";

            int ip = wifiManager.getConnectionInfo().getIpAddress();
            return Formatter.formatIpAddress(ip);

        } catch (Exception e) {
            return "0.0.0.0";
        }
    }
}