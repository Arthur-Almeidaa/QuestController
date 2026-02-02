package com.teste.adb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        Log.d("BootReceiver", "Recebido: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {

            Intent serviceIntent = new Intent(context, QuestMonitorService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
