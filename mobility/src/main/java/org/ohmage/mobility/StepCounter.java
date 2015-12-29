package org.ohmage.mobility;

import android.app.Service;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;


public abstract class StepCounter implements SensorEventListener {

    final static public long SECONDS_IN_NANO = 1000000000;
    final static public long PEAK_WINDOW = (long) (0.59 * SECONDS_IN_NANO);
    final static public long CENTERED_MOVING_AVG_WINDOW = (long) (0.31 * SECONDS_IN_NANO);
    final static public String TAG = StepCounter.class.getSimpleName();
    final Context context;
    final Handler handler;
    final SensorManager mSensorManager;
    final Sensor mAccelerometer;
    PeakDetector peakDetector = new PeakDetector(10.5, 0.36);
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

    public StepCounter(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
        mSensorManager = (SensorManager) context.getSystemService(Service.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void start() {
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST, handler);
    }

    public void pause() {
        mSensorManager.unregisterListener(this);

    }

    private TimeValue computeSMA() {
        return new TimeValue((buffer.head().time + buffer.peek().time) / 2, (float) (buffer.getSum() / buffer.size()));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //Log.v(TAG, peakDetector.toString());
        //Log.v(TAG, buffer.toString());
        if (buffer.size() > 10) {
            // 1st Peak detection
            TimeValue sma = computeSMA();
            if (!peakDetector.started()) {
                peakDetector.start(sma.time);
            }
            if (sma.time < peakDetector.getStartTime() + PEAK_WINDOW) {
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
                peakDetector.restart(sma.time);
                peakDetector.pushAndDetect(sma.value);
            }
        }

        // 2nd Maintain SMA
        float[] vector = event.values;
        float magnitude = (float) Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1]
                + vector[2] * vector[2]);
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

    abstract void onDetectStep(long totalStepCount);

    private class TimeValue {
        final long time;
        final float value;

        private TimeValue(long time, float value) {
            this.time = time;
            this.value = value;
        }
    }
}
