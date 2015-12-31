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
import com.google.android.gms.location.LocationResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.EnumMap;
import java.util.GregorianCalendar;

import io.smalldatalab.omhclient.DSUDataPoint;
import io.smalldatalab.omhclient.DSUDataPointBuilder;

import static com.google.android.gms.location.LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
import static com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY;


/**
 * The central nerve of the tracking functionality. It will receives Intent for
 * Location and Activity updates from Google Play Service, and will adjust the tracking mode
 * (frequency, GPS or Wifi, whether to start step counting service, etc) according to a state machine.
 *
 * See onHandleIntent() for its main logic.
 */
public class UpdateListenerIntentService extends IntentService {

    public static final String ACTION = "UPDATE_TRACKING";
    final static String STATE_TIME_PARAM = "stateTime";
    final static String TRACKING_MODE_PARAM = "trackingMode";
    final static String PREV_USER_STATE_PARAM = "prevUserState";
    final static String USER_STATE_SINCE_PARAM = "userStateSince";

    // assume the previous state is outdated when it is more than 4-minute old.
    final static int STATE_EXPIRATION_TIME_MILLIS = 240 * 1000;
    private static final String TAG = UpdateListenerIntentService.class.getSimpleName();

    // The states to be preserved across different service instance.
    private HMMModel hmm;
    private HMMModel.State prevUserState;
    private long userStateSince;
    private long prevStateTime;
    private TrackingMode trackingMode;


    public UpdateListenerIntentService() {
        // Set the label for the service's background thread
        super("UpdateIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // restore the previous states
        restoreState();
    }
    @Override
    public void onDestroy() {
        // save the previous states
        saveState();
    }

    // state machine to determine the tracking mode
    public TrackingMode determineNextTrackingMode(HMMModel.State curState, long statePersistenceMillis, TrackingMode prevMode) {
        if (curState == HMMModel.State.STILL) {
            // If the user is being still for a while OR we were previous in START_WALKING or START_VEHICLE or WALKING tracking mode, transit to DWELL mode
            // We do so instead of transiting to START_DWELL is because we don't want the false positive walking/vehicle events or WALKING mode that is already very accurate
            // to trigger power consuming START_DWELL mode
            // In other word, we only trigger START_DWELL mode when the user was driving for a while and stop at a place in order to get the accurate location of that place
            if (statePersistenceMillis > TrackingMode.START_DWELL.getPersistenceMillis()
                    || (prevMode == TrackingMode.START_WALKING || prevMode == TrackingMode.WALKING || prevMode == TrackingMode.START_VEHICLE || prevMode == TrackingMode.DWELL)) {
                return TrackingMode.DWELL;
            } else {
                return TrackingMode.START_DWELL;
            }
        } else if (curState == HMMModel.State.WALKING || curState == HMMModel.State.RUNNING || curState == HMMModel.State.BICYCLE) {
            // if the user is walking/running/bicycling, transit to walking, or start waling state if the user just starts walking
            if (statePersistenceMillis > TrackingMode.START_WALKING.getPersistenceMillis()) {
                return TrackingMode.WALKING;
            } else {
                return TrackingMode.START_WALKING;
            }

        } else if (curState == HMMModel.State.VEHICLE) {
            if (statePersistenceMillis > TrackingMode.START_VEHICLE.getPersistenceMillis()) {
                return TrackingMode.VEHICLE;
            } else {
                return TrackingMode.START_VEHICLE;
            }

        }
        throw new RuntimeException("Incomplete tracking mode decision");

    }

    /**
     * Called when a new location update is available.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        HMMModel.State userState = prevUserState;
        // If the intent contains a location update
        if (LocationResult.hasResult(intent)) {
            LocationResult result = LocationResult.extractResult(intent);
            Log.v(TAG, "Location update" + result.toString());
            // Write the result to the DSU
            writeResultToDsu(result);
        }
        // If the intent contains an activity update
        if (ActivityRecognitionResult.hasResult(intent)) {

            // Get the update
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            Log.v(TAG, "Activity update" + result.toString());
            // Write the result to the DSU
            writeResultToDsu(result);

            // ** maintain current user states ** //

            // see what HMM say about the user's current state given the Activity Detected Result
            userState = hmm.push(result);
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "new state: " + userState + " Hmm prob: " + hmm.toString());
            }

            // if the user state change ...
            if (userState != prevUserState) {
                // write the prev episode
                writeEpisodeToDsu(userStateSince, prevStateTime, prevUserState);

                // transit to a new episode
                prevUserState = userState;
                // restart the start time of the episode
                userStateSince = System.currentTimeMillis();
            }

        }


        boolean isStartUpEvent = (intent.getAction() != null && intent.getAction().equals(ACTION));
        if (isStartUpEvent) {
            Log.v(TAG, String.format("Triggered by the periodic startup event"));

        }

        // update tracking mode
        if (ActivityRecognitionResult.hasResult(intent) || isStartUpEvent) {

            TrackingMode nextTrackingMode = determineNextTrackingMode(userState, System.currentTimeMillis() - userStateSince, trackingMode);
            Log.v(TAG, String.format("userState: %s, statePersistenceTime %s, trackingMode: %s => nextMode %s", prevUserState, System.currentTimeMillis() - userStateSince, trackingMode, nextTrackingMode));

            // Only request tracking updates again when the tracking mode changed or it is an start up event
            // the later is to make sure the expected tracking mode is used.
            if (trackingMode != nextTrackingMode || isStartUpEvent) {
                DetectionUpdateRequester ld =
                        new DetectionUpdateRequester(this,
                                nextTrackingMode.getLocationPriority(),
                                nextTrackingMode.getMinimumDisplacement(),
                                nextTrackingMode.getFastestLocationIntervalMillis(),
                                nextTrackingMode.getLocationIntervalMillis(),
                                nextTrackingMode.getActivityIntervalMillis());
                    ld.requestUpdates();

                trackingMode = nextTrackingMode;
                saveState();
            }
            // start step count tracking service when the user starts and continues walking
            if (userState == HMMModel.State.RUNNING || userState == HMMModel.State.WALKING) {
                Intent stepCountIntent = new Intent(this, StepCountService.class);
                // let step counting persist for 30 seconds
                stepCountIntent.putExtra(StepCountService.RUN_UNTIL_PARAM, System.currentTimeMillis() + (30 * 1000));
                // see StepCountService.startCommand()
                startService(stepCountIntent);
            }

        }

    }

    public void restoreState() {
        SharedPreferences trackingState = getSharedPreferences(UpdateListenerIntentService.class.getName(), 0);
        prevStateTime = trackingState.getLong(STATE_TIME_PARAM, -1);
        if (System.currentTimeMillis() - prevStateTime > STATE_EXPIRATION_TIME_MILLIS) {
            // ** The stored state is too far ago. Save the previous episode, and reset the states. **//

            // try to commit the prev episode
            String prevStateString = trackingState.getString(PREV_USER_STATE_PARAM, null);
            if (prevStateString != null) {
                prevUserState = HMMModel.State.valueOf(prevStateString);
            } else {
                prevUserState = null;
            }
            userStateSince = trackingState.getLong(USER_STATE_SINCE_PARAM, -1);

            // persist the prev episode if it is valid
            if (prevStateTime != -1 && prevUserState != null && userStateSince != -1) {
                writeEpisodeToDsu(userStateSince, prevStateTime, prevUserState);
            }

            // wipe the prev state
            Log.v(TAG, "State was saved too long ago. Restart the state");
            trackingState.edit().clear().apply();
            prevStateTime = System.currentTimeMillis();

        }

        trackingMode = TrackingMode.valueOf(trackingState.getString(TRACKING_MODE_PARAM, TrackingMode.DWELL.name()));
        prevUserState = HMMModel.State.valueOf(trackingState.getString(PREV_USER_STATE_PARAM, HMMModel.State.STILL.name()));
        userStateSince = trackingState.getLong(USER_STATE_SINCE_PARAM, System.currentTimeMillis());
        boolean complete = true;
        EnumMap<HMMModel.State, Double> probs = new EnumMap<>(HMMModel.State.class);
        for (HMMModel.State s : HMMModel.State.values()) {
            float val = trackingState.getFloat(s.name(), -1);
            if (val == -1) {
                complete = false;
                break;
            } else {
                probs.put(s, (double) val);
            }
        }
        if (complete) {
            // we can restore the HMM state completely
            hmm = new HMMModel(probs);
        } else {
            // otherwise start from default init state
            hmm = new HMMModel();
        }
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Restore states:" + toString());
        }
    }

    public void saveState() {
        SharedPreferences trackingState = getSharedPreferences(UpdateListenerIntentService.class.getName(), 0);
        SharedPreferences.Editor editor =
                trackingState
                .edit()
                        .putLong(USER_STATE_SINCE_PARAM, userStateSince)
                        .putString(TRACKING_MODE_PARAM, trackingMode.name())
                        .putString(PREV_USER_STATE_PARAM, prevUserState.name())
                        .putLong(STATE_TIME_PARAM, System.currentTimeMillis());
        for (HMMModel.State s : HMMModel.State.values()) {
            editor.putFloat(s.name(), (float) hmm.getProb(s));
        }

        editor.apply();

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

    private void writeEpisodeToDsu(long start, long end, HMMModel.State state) {

        try {
            JSONObject body = new JSONObject();
            body.put("start", start);
            body.put("end", end);
            body.put("state", state.name());

            Calendar cal = GregorianCalendar.getInstance();
            cal.setTimeInMillis(end);
            DSUDataPoint datapoint = new DSUDataPointBuilder()
                    .setSchemaNamespace(getString(R.string.schema_namespace))
                    .setSchemaName(getString(R.string.episode_schema_name))
                    .setSchemaVersion(getString(R.string.schema_version))
                    .setAcquisitionModality(getString(R.string.acquisition_modality))
                    .setAcquisitionSource(getString(R.string.acquisition_source_name))
                    .setCreationDateTime(cal)
                    .setBody(body).createDSUDataPoint();
            datapoint.save();
            Log.v(TAG, "Write Episode:" + datapoint.toString());

        } catch (JSONException e) {
            Log.e(TAG, "Write episode error: " + start + " " + end + " " + state.name(), e);
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

    @Override
    public String toString() {
        return "UpdateListenerIntentService{" +
                "hmm=" + hmm +
                ", prevUserState=" + prevUserState +
                ", userStateSince=" + userStateSince +
                ", trackingMode=" + trackingMode +
                '}';
    }


    enum TrackingMode {
        // low power mode when user dwell at a place
        DWELL(-1, PRIORITY_BALANCED_POWER_ACCURACY, 0, 10 * 1000, 60 * 1000, 20 * 1000),
        // high power mode when user is walking
        WALKING(-1, PRIORITY_HIGH_ACCURACY, 0, 1000, 20 * 1000, 10 * 1000),
        // low power for in_vehicle
        VEHICLE(-1, PRIORITY_BALANCED_POWER_ACCURACY, 0, 10 * 1000, 60 * 1000, 20 * 1000),
        // high power mode when the user start to dwell at a place
        START_DWELL(60 * 1000, PRIORITY_HIGH_ACCURACY, 0, 1000, 20 * 1000, 10 * 1000),
        // low power when user start walking. it is to prevent false positives walking detection from draining battery.
        START_WALKING(60 * 1000, PRIORITY_BALANCED_POWER_ACCURACY, 0, 10 * 1000, 10 * 1000, 10 * 1000),
        // low power for in_hehicle
        START_VEHICLE(60 * 1000, PRIORITY_BALANCED_POWER_ACCURACY, 0, 10 * 1000, 30 * 1000, 10 * 1000);

        final int persistenceMillis, locationPriority, minimumDisplacement, fastestLocationIntervalMillis, locationIntervalMillis, activityIntervalMillis;

        TrackingMode(int persistenceMillis, int locationPriority, int minimumDisplacement, int fastestLocationIntervalMillis, int locationIntervalMillis, int activityIntervalMillis) {
            this.persistenceMillis = persistenceMillis;
            this.locationPriority = locationPriority;
            this.minimumDisplacement = minimumDisplacement;
            this.fastestLocationIntervalMillis = fastestLocationIntervalMillis;
            this.locationIntervalMillis = locationIntervalMillis;
            this.activityIntervalMillis = activityIntervalMillis;
        }

        public int getPersistenceMillis() {
            return persistenceMillis;
        }

        public int getLocationPriority() {
            return locationPriority;
        }

        public int getMinimumDisplacement() {
            return minimumDisplacement;
        }

        public int getFastestLocationIntervalMillis() {
            return fastestLocationIntervalMillis;
        }

        public int getLocationIntervalMillis() {
            return locationIntervalMillis;
        }

        public int getActivityIntervalMillis() {
            return activityIntervalMillis;
        }
    }


}
