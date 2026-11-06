package com.nexus.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives boot events. The launcher itself is started by the system through the HOME intent
 * filter, so here we only make sure the stored settings are readable after a reboot.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            LauncherSettings settings = new LauncherSettings(context);
            // Touch the preferences so the values are loaded and available to the launcher.
            settings.getLauncherMode();
            settings.getThemeMode();
        }
    }
}
