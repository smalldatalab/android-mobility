package org.ohmage.mobility;

import android.content.Context;
import android.content.SharedPreferences;

import org.ohmage.mobility.activity.ActivityDetectionRequester;
import org.ohmage.mobility.location.LocationDetectionRequester;

/**
 * Created by changun on 12/22/15.
 */
public class StartTracking {
    static void start(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ActivityUtils.SHARED_PREFERENCES, Context.MODE_PRIVATE);

        if (prefs.getBoolean(ActivityUtils.KEY_ACTIVITY_RUNNING, DefaultPreferences.ACTIVITY_RUNNING)) {

            int intervalPref = prefs.getInt(ActivityUtils.KEY_ACTIVITY_INTERVAL, DefaultPreferences.ACTIVITY_INTERVAL);
            long interval = ActivityUtils.getIntervalMillis(context, intervalPref);
            ActivityDetectionRequester ad = new ActivityDetectionRequester(context);
            ad.requestUpdates(interval);
        }

        if (prefs.getBoolean(ActivityUtils.KEY_LOCATION_RUNNING, DefaultPreferences.LOCATION_RUNNING)) {

            int intervalPref = prefs.getInt(ActivityUtils.KEY_LOCATION_INTERVAL, DefaultPreferences.LOCATION_INTERVAL);
            long interval = ActivityUtils.getIntervalMillis(context, intervalPref);
            int priorityPref = prefs.getInt(ActivityUtils.KEY_LOCATION_PRIORITY, DefaultPreferences.LOCATION_PRIORITY);
            int priority = ActivityUtils.getPriority(priorityPref);
            LocationDetectionRequester ld = new LocationDetectionRequester(context);
            ld.requestUpdates(interval, priority);
        }
    }

}
