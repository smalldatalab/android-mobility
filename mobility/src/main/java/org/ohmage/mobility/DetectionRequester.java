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

import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;

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
public abstract class DetectionRequester
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    // Request code to use when launching the resolution activity
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";
    // Storage for a context from the calling client
    private Context mContext;
    // Stores the PendingIntent used to send activity recognition events back to the app
    private PendingIntent mRequestPendingIntent;
    // Stores the current instantiation of the activity recognition client
    private GoogleApiClient mGooglePlayServicesClient;
    // Bool to track whether the app is already resolving an error
    private boolean mResolvingError = false;

    public DetectionRequester(Context context) {
        // Save the context
        mContext = context;

        // Initialize the globals to null
        mRequestPendingIntent = null;
        mGooglePlayServicesClient = null;
    }

    /**
     * Returns the current PendingIntent to the caller.
     *
     * @return The PendingIntent used to request activity recognition updates
     */
    public PendingIntent getRequestPendingIntent() {
        return mRequestPendingIntent;
    }

    /**
     * Sets the PendingIntent used to make activity recognition update requests
     *
     * @param intent The PendingIntent
     */
    public void setRequestPendingIntent(PendingIntent intent) {
        mRequestPendingIntent = intent;
    }

    /**
     * Allows callers to cancel this pending intent which will force the request to stop.
     */
    public void cancelPendingIntent() {
        if (mRequestPendingIntent != null) {
            mRequestPendingIntent.cancel();
            mRequestPendingIntent = null;
        }
    }

    public void removeUpdates() {
        requestUpdatesFromClient(mContext, getGooglePlayServicesClient(), createRequestPendingIntent());
    }

    /**
     * Make the actual update request. This is called from onConnected().
     */
    private void continueRequestUpdates() {
        /*
         * Request updates, using the given detection interval.
         * The PendingIntent sends updates to ActivityRecognitionIntentService
         */
        requestUpdatesFromClient(mContext, getGooglePlayServicesClient(), createRequestPendingIntent());

    }

    /**
     * Extending classes should implement this method and remove the request for updates
     */
    protected abstract void requestUpdatesFromClient(Context context, GoogleApiClient client, PendingIntent intent);

    /**
     * Extending classes should implement this method and remove the request for updates
     */
    protected abstract void removeUpdatesFromClient(Context context, GoogleApiClient client, PendingIntent intent);

    /**
     * Extending classes should implement this method and create the play services client they need
     */
    protected abstract GoogleApiClient createGooglePlayServicesClient(Context context);

    /**
     * Extending classes should implement this method and return an intent to the IntentService
     * they want to receive updates with.
     *
     * @param context
     * @return
     */
    protected abstract Intent getIntentService(Context context);

    /**
     * Request a connection to Location Services. This call returns immediately,
     * but the request is not complete until onConnected() or onConnectionFailure() is called.
     */
    protected void requestConnection() {
        getGooglePlayServicesClient().connect();
    }

    /**
     * Get the current activity recognition client, or create a new one if necessary.
     * This method facilitates multiple requests for a client, even if a previous
     * request wasn't finished. Since only one client object exists while a connection
     * is underway, no memory leaks occur.
     *
     * @return An ActivityRecognitionClient object
     */
    private GoogleApiClient getGooglePlayServicesClient() {
        if (mGooglePlayServicesClient == null) {
            mGooglePlayServicesClient = createGooglePlayServicesClient(mContext);
        }
        return mGooglePlayServicesClient;
    }


    /*
     * Called by Location Services once the activity recognition client is connected.
     *
     * Continue by requesting activity updates.
     */
    @Override
    public void onConnected(Bundle arg0) {
        // If debugging, log the connection
        Log.d(ActivityUtils.APPTAG, mContext.getString(R.string.connected));

        // Continue the process of requesting activity recognition updates
        continueRequestUpdates();
        getGooglePlayServicesClient().disconnect();
    }

    @Override
    public void onConnectionSuspended(int arg) {
        Log.d(ActivityUtils.APPTAG, "Connection Suspended arg:" + arg);
    }

    /**
     * Get a PendingIntent to send with the request to get activity recognition updates. Location
     * Services issues the Intent inside this PendingIntent whenever a activity recognition update
     * occurs.
     *
     * @return A PendingIntent for the IntentService that handles activity recognition updates.
     */
    private PendingIntent createRequestPendingIntent() {

        // If the PendingIntent already exists
        if (null != getRequestPendingIntent()) {

            // Return the existing intent
            return mRequestPendingIntent;

            // If no PendingIntent exists
        } else {
            // Create an Intent pointing to the IntentService
            Intent intent = getIntentService(mContext);

            /*
             * Return a PendingIntent to start the IntentService.
             * Always create a PendingIntent sent to Location Services
             * with FLAG_UPDATE_CURRENT, so that sending the PendingIntent
             * again updates the original. Otherwise, Location Services
             * can't match the PendingIntent to requests made with it.
             */
            PendingIntent pendingIntent = PendingIntent.getService(mContext, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            setRequestPendingIntent(pendingIntent);
            return pendingIntent;
        }

    }

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);

        dialogFragment.show(((MainActivity) mContext).getSupportFragmentManager(), "errordialog");
    }

    /*
     * Implementation of OnConnectionFailedListener.onConnectionFailed
     * If a connection or disconnection request fails, report the error
     * connectionResult is passed in from Location Services
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (connectionResult.hasResolution()) {
            mResolvingError = true;
            if (mContext instanceof MainActivity) {
                try {

                    connectionResult.startResolutionForResult((MainActivity) mContext, REQUEST_RESOLVE_ERROR);
                } catch (SendIntentException e) {
                    // There was an error with the resolution intent. Try again.
                    getGooglePlayServicesClient().connect();
                }
            } else {
                Notification.showResovleIssueNotification(mContext);
            }
        } else if (mContext instanceof MainActivity) {

            // Show dialog using GoogleApiAvailability.getErrorDialog()
            showErrorDialog(connectionResult.getErrorCode());
            mResolvingError = true;
        } else {
            Notification.showResovleIssueNotification(mContext);
            mResolvingError = true;
        }
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GoogleApiAvailability.getInstance().getErrorDialog(
                    this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
        }
    }
}
