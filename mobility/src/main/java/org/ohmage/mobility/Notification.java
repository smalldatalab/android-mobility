package org.ohmage.mobility;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

/**
 * This class contains the notifications that are made in different occasions
 * Created by changun on 12/22/15.
 */
public class Notification {
    final static int RESOLVE_ISSUE_CODE = 100;

    public static void showResovleIssueNotification(Context mContext, PendingIntent pendingIntent) {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(mContext)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("Mobility is not working. Click here to resolve the issue.")
                        .setContentText("Mobility needs Google Play Service to work. Please click here to install!")
                        .setContentIntent(pendingIntent);
        // Sets an ID for the notification
        int mNotificationId = RESOLVE_ISSUE_CODE;
        // Gets an instance of the NotificationManager service
        NotificationManager mNotifyMgr =
                (NotificationManager) mContext.getSystemService(Activity.NOTIFICATION_SERVICE);
        // Builds the notification and issues it.
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }

    public static void showResovleIssueNotification(Context mContext) {
        showResovleIssueNotification(mContext,
                PendingIntent.getActivity(
                        mContext,
                        0,
                        new Intent(mContext, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT
                )
        );
    }
}
