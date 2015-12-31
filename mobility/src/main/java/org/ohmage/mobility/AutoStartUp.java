package org.ohmage.mobility;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

/**
 * Start detection when the phone boots up, battery connected or
 * disconnected so that the tracking will keep running */

public class AutoStartUp extends BroadcastReceiver {
    public static void repeatingAutoStart(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(UpdateListenerIntentService.ACTION, null, context, UpdateListenerIntentService.class);
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), AlarmManager.INTERVAL_FIFTEEN_MINUTES, pendingIntent);

        context.startService(intent);


    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("AutoStart", intent.toString());
        repeatingAutoStart(context);
    }
}
