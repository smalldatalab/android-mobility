package org.ohmage.mobility;

import android.app.Service;
import android.content.Intent;
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

public class StepCountService extends Service {
    /* The extra name of when to stop counting*/
    final static public String RUN_UNTIL_PARAM = "runUntil";
    /* The extra name of parameter of the base step count.
       This will be added to the sensed step count to compensate
        the late start of the tracking */
    final static public String BASE_STEP_COUNT = "baseStepCount";
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
    /* the base step count. This will be added to the sensed step count to compensate
        the late start of the tracking */
    long baseStepCount = 0;

    public StepCountService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onStart(Intent intent, int startId) {
        handleCommand(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread mHandlerThread = new HandlerThread("sensorThread");
        mHandlerThread.start();
        Handler handler = new Handler(mHandlerThread.getLooper());
        counter = new StepCounter(this, handler) {
            @Override
            void onDetectStep(final long total) {
                Log.v(TAG, "Step count:" + total + " Base step count:" + baseStepCount);
            }
        };
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "WakeLockForStepCount");
        wakeLock.acquire();
        startTime = System.currentTimeMillis();
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
        long runUntil = intent.getExtras().getLong(RUN_UNTIL_PARAM);
        baseStepCount = intent.getLongExtra(BASE_STEP_COUNT, 0);

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
    }

    private void saveCountAndEndService() {
        writeResultToDsu(startTime, System.currentTimeMillis(), counter.getStepCount() + baseStepCount);
        ended = true;
        counter.pause();
        if (wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Throwable th) {
                // ignoring this exception, probably wakeLock was already released
            }

        }
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
