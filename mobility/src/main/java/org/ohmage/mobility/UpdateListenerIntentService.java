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

package org.ohmage.mobility;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.GregorianCalendar;

import io.smalldatalab.omhclient.DSUDataPoint;
import io.smalldatalab.omhclient.DSUDataPointBuilder;


/**
 * Service that receives location updates. It receives updates
 * in the background, even if the main Activity is not visible.
 */
public class UpdateListenerIntentService extends IntentService {

    public static final String ACTION = "UPDATE_TRACKING";
    private static final String TAG = UpdateListenerIntentService.class.getSimpleName();
    // The states
    private Mode userMode = Mode.STILL;
    private long userModeSince = System.currentTimeMillis();
    private Mode trackingMode = Mode.STILL;

    public UpdateListenerIntentService() {
        // Set the label for the service's background thread
        super("UpdateIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        restoreState();
        Log.v(TAG, String.format("created with userMode: %s, userModeSince: %s, trackingMode: %s", userMode, userModeSince, trackingMode));
    }

    @Override
    public void onDestroy() {
        saveState();
    }

    // evaluate the state machine to determine the next tracking mode
    private Mode determineNextTrackingMode(Mode curMode, Mode curTrackingMode, long modePersistenceTime) {
        if (curTrackingMode == curMode) {
            // do not transit, if the user activity is consistent with the tracking mode
            return curMode;
        } else if (curMode.equals(Mode.WALKING) && modePersistenceTime >= 40) {
            // transit to WALKING mode, as soon as the user starts walking
            return Mode.WALKING;
        } else if (curMode.equals(Mode.VEHICLE) && modePersistenceTime > 50 * 1000) {
            // transit to VEHICLE mode, when the user starts driving for 50 secs
            return Mode.VEHICLE;
        } else if (curMode.equals(Mode.STILL) && modePersistenceTime > 120 * 1000) {
            // transit to STILL mode, when the user stay a place for 5 minutes
            return Mode.STILL;
        } else {
            // otherwise stay at whatever trackingMode it was
            return curTrackingMode;
        }
    }

    private Mode determineCurrentUserMode(ActivityRecognitionResult result, Mode prevMode) {
        int activityType = result.getMostProbableActivity().getType();
        if (activityType == DetectedActivity.TILTING || activityType == DetectedActivity.UNKNOWN) {
            // unknown activity, use the previous user mode
            return prevMode;
        } else if (activityType == DetectedActivity.WALKING
                || activityType == DetectedActivity.RUNNING
                || activityType == DetectedActivity.ON_FOOT
                || activityType == DetectedActivity.ON_BICYCLE) {
            return Mode.WALKING;
        } else if (activityType == DetectedActivity.IN_VEHICLE) {
            return Mode.VEHICLE;

        } else {
            return Mode.STILL;
        }

    }

    /**
     * Called when a new location update is available.
     */
    @Override
    protected void onHandleIntent(Intent intent) {

        // If the intent contains an update
        if (LocationResult.hasResult(intent)) {

            LocationResult result = LocationResult.extractResult(intent);
            Log.v(TAG, "Location update" + result.toString());
            // Write the result to the DSU
            writeResultToDsu(result);
        }
        // If the intent contains an update
        if (ActivityRecognitionResult.hasResult(intent)) {

            // Get the update
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            Log.v(TAG, "Location update" + result.toString());
            // Write the result to the DSU
            writeResultToDsu(result);

            // maintain current user states
            Mode curUserMode = determineCurrentUserMode(result, userMode);
            if (curUserMode != userMode) {
                userModeSince = System.currentTimeMillis();
                userMode = curUserMode;
            }
        }
        boolean isStartUpEvent = (intent.getAction() != null && intent.getAction().equals(ACTION));
        if (isStartUpEvent) {
            Log.v(TAG, String.format("Triggered by the periodic startup event"));

        }
        // update tracking request, but don't update it when it is location update intent which could occur too frequently
        if (ActivityRecognitionResult.hasResult(intent) || isStartUpEvent) {

            Mode nextTrackingMode = determineNextTrackingMode(userMode, trackingMode, System.currentTimeMillis() - userModeSince);
            Log.v(TAG, String.format("userMode: %s, modePersistenceTime %s, trackingMode: %s => nextMode %s", userMode, System.currentTimeMillis() - userModeSince, trackingMode, nextTrackingMode));

            // Only request tracking updates again when the tracking mode changed or it is an start up event
            if (trackingMode != nextTrackingMode || isStartUpEvent) {
                // use higher location accuracy and shorter interval for WALKING mode
                if (nextTrackingMode.equals(Mode.WALKING)) {
                    DetectionUpdateRequester ld = new DetectionUpdateRequester(this, LocationRequest.PRIORITY_HIGH_ACCURACY, 0, 10 * 1000, 10 * 1000, 10 * 1000);
                    ld.requestUpdates();
                } else if (nextTrackingMode.equals(Mode.VEHICLE)) {
                    DetectionUpdateRequester ld = new DetectionUpdateRequester(this, LocationRequest.PRIORITY_HIGH_ACCURACY, 50, 10 * 1000, 300 * 1000, 20 * 1000);
                    ld.requestUpdates();
                } else if (nextTrackingMode.equals(Mode.STILL)) {
                    DetectionUpdateRequester ld = new DetectionUpdateRequester(this, LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, 0, 10 * 1000, 600 * 1000, 20 * 1000);
                    ld.requestUpdates();
                } else {
                    throw new RuntimeException("Unknow nextTrackingMode" + nextTrackingMode);
                }
                trackingMode = nextTrackingMode;
                saveState();
            }
            // start step count tracking service when the user starts and continues walking
            if (userMode == Mode.WALKING) {
                Intent stepCountIntent = new Intent(this, StepCountService.class);
                stepCountIntent.putExtra(StepCountService.RUN_UNTIL_PARAM, System.currentTimeMillis() + (30 * 1000));
                stepCountIntent.putExtra(StepCountService.BASE_STEP_COUNT, (long) 20);

                startService(stepCountIntent);
            }

        }

    }

    public void restoreState() {
        SharedPreferences trackingState = getSharedPreferences(UpdateListenerIntentService.class.getName(), 0);
        userMode = Mode.valueOf(trackingState.getString("preMode", Mode.STILL.toString()));
        userModeSince = trackingState.getLong("userModeSince", System.currentTimeMillis());
        trackingMode = Mode.valueOf(trackingState.getString("trackingMode", Mode.STILL.toString()));
    }

    public void saveState() {
        SharedPreferences trackingState = getSharedPreferences(UpdateListenerIntentService.class.getName(), 0);
        trackingState
                .edit()
                .putString("preMode", userMode.toString())
                .putLong("userModeSince", userModeSince)
                .putString("trackingMode", trackingMode.toString())
                .apply();

    }

    private void writeResultToDsu(ActivityRecognitionResult result) {

        if (result != null) {
            try {
                JSONArray json = new JSONArray();

                // Get all the probable activities from the updated result
                for (DetectedActivity detectedActivity : result.getProbableActivities()) {

                    // Get the activity type, confidence level, and human-readable name
                    int activityType = detectedActivity.getType();
                    int confidence = detectedActivity.getConfidence();
                    String activityName = getNameFromType(activityType);

                    JSONObject activity = new JSONObject();

                    activity.put("activity", activityName);
                    activity.put("confidence", confidence);
                    json.put(activity);

                }
                JSONObject body = new JSONObject();
                body.put("activities", json);
                Calendar cal = GregorianCalendar.getInstance();
                cal.setTimeInMillis(result.getTime());
                DSUDataPoint datapoint = new DSUDataPointBuilder()
                        .setSchemaNamespace(getString(R.string.schema_namespace))
                        .setSchemaName(getString(R.string.activity_schema_name))
                        .setSchemaVersion(getString(R.string.schema_version))
                        .setAcquisitionModality(getString(R.string.acquisition_modality))
                        .setAcquisitionSource(getString(R.string.acquisition_source_name))
                        .setCreationDateTime(cal)
                        .setBody(body).createDSUDataPoint();
                datapoint.save();
            } catch (Exception e) {
                Log.e(this.getClass().getSimpleName(), "Create datapoint failed", e);
            }

        }

    }

    private void writeResultToDsu(LocationResult result) {

        for (Location location : result.getLocations()) {
            if (location != null) {
                try {
                    JSONObject body = new JSONObject();
                    body.put("latitude", location.getLatitude());
                    body.put("longitude", location.getLongitude());
                    body.put("accuracy", location.getAccuracy());
                    body.put("altitude", location.getAltitude());
                    body.put("bearing", location.getBearing());
                    body.put("speed", location.getSpeed());
                    body.put("timestamp", location.getTime());

                    Calendar cal = GregorianCalendar.getInstance();
                    cal.setTimeInMillis(location.getTime());
                    DSUDataPoint datapoint = new DSUDataPointBuilder()
                            .setSchemaNamespace(getString(R.string.schema_namespace))
                            .setSchemaName(getString(R.string.location_schema_name))
                            .setSchemaVersion(getString(R.string.schema_version))
                            .setAcquisitionModality(getString(R.string.acquisition_modality))
                            .setAcquisitionSource(getString(R.string.acquisition_source_name))
                            .setCreationDateTime(cal)
                            .setBody(body).createDSUDataPoint();
                    datapoint.save();
                } catch (JSONException e) {
                    e.printStackTrace();
                }


            }
        }

    }

    /**
     * Map detected activity types to strings
     *
     * @param activityType The detected activity type
     * @return A user-readable name for the type
     */
    private String getNameFromType(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "in_vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "on_bicycle";
            case DetectedActivity.ON_FOOT:
                return "on_foot";
            case DetectedActivity.STILL:
                return "still";
            case DetectedActivity.UNKNOWN:
                return "unknown";
            case DetectedActivity.TILTING:
                return "tilting";
            case DetectedActivity.WALKING:
                return "walking";
            case DetectedActivity.RUNNING:
                return "running";
        }
        return "unknown";
    }

    enum Mode {
        STILL, WALKING, VEHICLE;
    }


}
