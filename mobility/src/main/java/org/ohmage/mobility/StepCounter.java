package org.ohmage.mobility;

import android.app.Service;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

/**
 * Streaming step counter algorithm. Compute windowed simple moving average perform windowed peak
 * detection with magnitude the variance threshold, where a window can only contain at most one peak
 * , and the peak magnitude and the variance of the signal need to exceed the thresholds.
 * <p>
 * Refer to this paper for parameterization and other details http://dl.acm.org/citation.cfm?id=2493449
 */
public abstract class StepCounter implements SensorEventListener {

    final static public long SECONDS_IN_NANO = 1000000000;

    // peak detection window size. This coincides the normal walking pace
    final static public long PEAK_WINDOW = (long) (0.59 * SECONDS_IN_NANO);
    // moving average window size
    final static public long CENTERED_MOVING_AVG_WINDOW = (long) (0.31 * SECONDS_IN_NANO);

    // sensing frequency. can set lower to save computation
    final static public int SENSING_SPEED = SensorManager.SENSOR_DELAY_GAME;
    final static public String TAG = StepCounter.class.getSimpleName();

    final Context context;
    final Handler handler;
    final SensorManager mSensorManager;
    final Sensor mAccelerometer;

    // peak detector with magnitude and variance threshold
    PeakDetector peakDetector = new PeakDetector(10.5, 5, 0.36);

    // ring buffer that maintain the sum of elements
    SumRingBuffer<TimeValue> buffer = new SumRingBuffer<TimeValue>(400) {

        @Override
        public double addToSum(TimeValue item, double sum) {
            return sum + item.value;
        }

        @Override
        public double subtractFromSum(TimeValue item, double sum) {
            return sum - item.value;
        }
    };
    /* total step count detected during the service lifetime */
    private long stepCount;

    /**
     *
     * @param context a context object that has the permission to access accelerometer sensor.
     * @param handler the handler that will be used to handle the step count computation and run the onDetectStep callback.
     *                Do NOT use the main activity thread.
     */
    public StepCounter(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
        mSensorManager = (SensorManager) context.getSystemService(Service.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /**
     * A callback function when a step is detected.
     *
     * @param totalStepCount total step count so far.
     */
    abstract void onDetectStep(long totalStepCount);



    public void start() {
        mSensorManager.registerListener(this, mAccelerometer, SENSING_SPEED, handler);
    }

    public void pause() {
        mSensorManager.unregisterListener(this);

    }

    // compute moving average and estimate the time using the average of the head and tail elements' times
    private TimeValue computeSMA() {
        return new TimeValue((buffer.head().time + buffer.peek().time) / 2, (float) (buffer.getSum() / buffer.size()));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // load SMA to peak detector after the simple moving average buffer has been warmed up
        if (buffer.size() > 10) {
            // 1st Peak detection
            TimeValue sma = computeSMA();
            if (!peakDetector.started()) {
                peakDetector.start(sma.time);
            }
            // check if we are still in the current peak detection window
            if (sma.time < peakDetector.getStartTime() + PEAK_WINDOW) {
                // if no peak detected yet, conduct peak detection
                // (at most one step is allowed in a peak detection window)
                if (!peakDetector.detected()) {
                    if (peakDetector.pushAndDetect(sma.value)) {
                        stepCount++;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                onDetectStep(stepCount);
                            }
                        });

                    }
                }
            } else {
                // arrive the next peak detection window. restart the detector with the current value
                peakDetector.restart(sma.time);
                peakDetector.pushAndDetect(sma.value);
            }
        }
        // 2nd Maintain SMA
        float[] vector = event.values;
        float magnitude = (float) Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        while (!buffer.isEmpty() && buffer.peek().time < event.timestamp - CENTERED_MOVING_AVG_WINDOW) {
            buffer.pop();
        }
        buffer.push(new TimeValue(event.timestamp, magnitude));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public long getStepCount() {
        return stepCount;
    }

    private class TimeValue {
        final long time;
        final float value;

        private TimeValue(long time, float value) {
            this.time = time;
            this.value = value;
        }
    }
}
