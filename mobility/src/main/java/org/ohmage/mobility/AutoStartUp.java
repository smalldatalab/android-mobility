package org.ohmage.mobility;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

/**
 * Start detection when the phone boots up if it was running
 */
public class AutoStartUp extends BroadcastReceiver {

    public static void repeatingAutoStart(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, AutoStartUp.class), PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), AlarmManager.INTERVAL_FIFTEEN_MINUTES, pendingIntent);

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("AutoStart", intent.toString());
        StartTracking.start(context);
        if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            repeatingAutoStart(context);
        }
    }
}
