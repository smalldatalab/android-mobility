package org.ohmage.mobility;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;

import io.smalldatalab.omhclient.DSUDataPoint;
import io.smalldatalab.omhclient.DSUDataPointBuilder;

/**
 * A step counting service running in the background. It starts step counting when onCreate and
 * will stop itself til "runUntil" time that can be set by an Intent (see handleCommand()).
 * It will keep CPU awake at at its lifetime, so use its wisely.
 * <p>
 * It will write a DSU record when the counting ends.
 * Override the saveCountAndEndService() to make it do something else.
 */
public class StepCountService extends Service {
    /* The extra name of when to stop counting*/
    final static public String RUN_UNTIL_PARAM = "runUntil";
    final static public String STEP_COUNT_KEY = "stepCount";
    final static public String START_TIME_KEY = "startTime";
    final static public String END_TIME_KEY = "endTime";
    final static String TAG = StepCountService.class.getSimpleName();
    /* when the step counter started */
    long startTime;
    /* the timer to end counting */
    Timer timer;
    /* wakelock to keep CPU awake */
    PowerManager.WakeLock wakeLock;
    /* step counter */
    StepCounter counter;
    /* whether this step counting session ended and data has been saved or not */
    boolean ended = false;

    // state store
    SharedPreferences trackingState;
    public StepCountService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onCreate() {
        super.onCreate();


        trackingState = getSharedPreferences(StepCountService.class.getName(), 0);
        // commit the counts of the previous counting service instance that didn't get saved;
        commitPrevCount();
        // create a handler thread to run step counting computation
        HandlerThread mHandlerThread = new HandlerThread("sensorThread");
        mHandlerThread.start();
        Handler handler = new Handler(mHandlerThread.getLooper());
        counter = new StepCounter(this, handler) {
            @Override
            void onDetectStep(final long total) {

                // save step count and endTime
                trackingState.edit()
                        .putInt(STEP_COUNT_KEY, (int) (total))
                        .putLong(END_TIME_KEY, System.currentTimeMillis())
                        .apply();

                Log.v(StepCountService.TAG, "Step count:" + total);
            }
        };
        // acquire a wakelock to prevent CPU from sleeping
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "WakeLockForStepCount");
        wakeLock.acquire();
        startTime = System.currentTimeMillis();
        // save start time
        trackingState.edit()
                .putLong(START_TIME_KEY, startTime)
                .apply();

        // start counter
        counter.start();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!ended) {
        /* try to save counting if the service is being destroyed without save the counts*/
            saveCountAndEndService();
        }
    }

    private void handleCommand(Intent intent) {
        if (intent != null && intent.hasExtra(RUN_UNTIL_PARAM)) {
            long runUntil = intent.getLongExtra(RUN_UNTIL_PARAM, 0);


            if (timer != null) {
                timer.cancel();
                timer.purge();
            }
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    saveCountAndEndService();
                }
            }, new Date(runUntil));
        } else {
            Log.i(TAG, "Unknown intent");
        }
    }

    public void commitPrevCount() {
        long startTime = trackingState.getLong(START_TIME_KEY, 0);
        long endTime = trackingState.getLong(END_TIME_KEY, 0);
        int stepCount = trackingState.getInt(STEP_COUNT_KEY, 0);
        if (startTime > 0 && endTime > 0 && stepCount > 0 && endTime > startTime) {
            writeResultToDsu(startTime, endTime, stepCount);
        }
        // clear state
        trackingState.edit().clear().apply();
    }

    protected void saveCountAndEndService() {
        writeResultToDsu(startTime, System.currentTimeMillis(), counter.getStepCount());
        ended = true;
        counter.pause();
        if (wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Throwable th) {
                // ignoring this exception, probably wakeLock was already released
            }

        }
        // clear state
        trackingState.edit().clear().apply();
        this.stopSelf();
    }

    private void writeResultToDsu(long start, long end, long stepCount) {

        try {
            JSONObject body = new JSONObject();
            body.put("start", start);
            body.put("end", end);
            body.put("steps", stepCount);

            Calendar cal = GregorianCalendar.getInstance();
            cal.setTimeInMillis(end);
            DSUDataPoint datapoint = new DSUDataPointBuilder()
                    .setSchemaNamespace(getString(R.string.schema_namespace))
                    .setSchemaName(getString(R.string.step_schema_name))
                    .setSchemaVersion(getString(R.string.schema_version))
                    .setAcquisitionModality(getString(R.string.acquisition_modality))
                    .setAcquisitionSource(getString(R.string.acquisition_source_name))
                    .setCreationDateTime(cal)
                    .setBody(body).createDSUDataPoint();
            datapoint.save();
            Log.v(TAG, datapoint.toString());
        } catch (JSONException e) {
            Log.e(TAG, String.format("JSONException: start %s end %s steps %s", start, end, stepCount), e);
        }

    }


}
