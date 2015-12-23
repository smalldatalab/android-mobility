/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ohmage.mobility.location;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.ohmage.mobility.ActivityUtils;
import org.ohmage.mobility.DetectionRequester;

/**
 * Class for connecting to Location Services and activity recognition updates.
 * <b>
 * Note: Clients must ensure that Google Play services is available before requesting updates.
 * </b> Use GooglePlayServicesUtil.isGooglePlayServicesAvailable() to check.
 * <p/>
 * <p/>
 * To use a DetectionRequester, instantiate it and call requestUpdates(). Everything else is done
 * automatically.
 */
public class LocationDetectionRequester extends DetectionRequester {

    // Location request object to configure the location client
    private final LocationRequest mLocationRequest;

    // Interval for listener
    private long mIntervalMillis;

    // Priority for listener
    private int mPriority;

    public LocationDetectionRequester(Context context) {
        super(context);

        mLocationRequest = new LocationRequest();
    }

    /**
     * Start the activity recognition update request process by
     * getting a connection.
     */
    public void requestUpdates(long intervalMillis, int priority) {
        mIntervalMillis = intervalMillis;
        mPriority = priority;
        requestConnection();
    }

    @Override
    protected void requestUpdatesFromClient(final Context context, final GoogleApiClient client, final PendingIntent intent) {
        Log.d(ActivityUtils.APPTAG, "Starting Location: " + mIntervalMillis + ", " + mPriority);


        mLocationRequest.setPriority(mPriority);
        mLocationRequest.setFastestInterval(1000);
        LocationServices.FusedLocationApi.requestLocationUpdates(client, mLocationRequest, intent);


        // Save request state
        context.getSharedPreferences(ActivityUtils.SHARED_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean(ActivityUtils.KEY_LOCATION_RUNNING, true).commit();
    }

    @Override
    protected void removeUpdatesFromClient(Context context, GoogleApiClient client, PendingIntent intent) {
        LocationServices.FusedLocationApi.removeLocationUpdates(client, intent);
    }

    @Override
    protected GoogleApiClient createGooglePlayServicesClient(Context context) {
        return new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected Intent getIntentService(Context context) {
        return new Intent(context, LocationListenerIntentService.class);
    }
}