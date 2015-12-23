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

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;

import com.google.android.gms.location.LocationResult;

import org.json.JSONException;
import org.json.JSONObject;
import org.ohmage.mobility.R;

import java.util.Calendar;
import java.util.GregorianCalendar;

import io.smalldatalab.omhclient.DSUDataPoint;
import io.smalldatalab.omhclient.DSUDataPointBuilder;


/**
 * Service that receives location updates. It receives updates
 * in the background, even if the main Activity is not visible.
 */
public class LocationListenerIntentService extends IntentService {


    public LocationListenerIntentService() {
        // Set the label for the service's background thread
        super("LocationIntentService");
    }

    /**
     * Called when a new location update is available.
     */
    @Override
    protected void onHandleIntent(Intent intent) {


        // If the intent contains an update
        if (LocationResult.hasResult(intent)) {

            LocationResult result = LocationResult.extractResult(intent);
            // Write the result to the DSU
            writeResultToDsu(result);

            // Log the update
            logLocationResult(result);
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
     * Write the location update to the log file
     *
     * @param result The result extracted from the incoming Intent
     */
    private void logLocationResult(LocationResult result) {
/*
        StringBuilder msg = new StringBuilder(DateTimeFormat.mediumDateTime()
                .print(new LocalDateTime())).append("|");
        for(Location location: result.getLocations()) {
            msg.append(location.getLatitude()).append(", ").append(location.getLongitude());
        }

        ContentValues values = new ContentValues();
        values.put(MobilityContentProvider.LocationPoint.DATA, msg.toString());
        getContentResolver().insert(MobilityContentProvider.LocationPoint.CONTENT_URI, values);*/
    }
}
