package org.ohmage.mobility;

import java.util.Arrays;

/**
 * Created by changun on 12/28/15.
 */
public class PeakDetector {
    final Float peakPrevValues[] = new Float[3];
    final double magnitudeThreshold;
    final double varianceThreshold;

    double sum = 0;
    double sumOfSquare = 0;
    long count = 0;
    long peakWindowStart = -1;
    boolean magnitudeExceeded = false;
    boolean varianceExceeded = false;


    public PeakDetector(double magnitudeThreshold, double varianceThreshold) {
        this.magnitudeThreshold = magnitudeThreshold;
        this.varianceThreshold = varianceThreshold;
    }

    public void start(long startTime) {
        if (BuildConfig.DEBUG) {
            if (startTime == -1) {
                throw new RuntimeException("startTime cannot be -1");
            }
        }
        peakWindowStart = startTime;
        magnitudeExceeded = false;
        varianceExceeded = false;
        peakPrevValues[0] = null;
        peakPrevValues[1] = null;
        peakPrevValues[2] = null;
        sum = 0;
        sumOfSquare = 0;
        count = 0;
    }

    public void restart(long startTime) {
        start(startTime);
    }

    public boolean started() {
        return peakWindowStart != -1;
    }

    public boolean detected() {
        return magnitudeExceeded && varianceExceeded;
    }

    public boolean pushAndDetect(float val) {
        if (BuildConfig.DEBUG) {
            if (!started()) {
                throw new RuntimeException("Cannot push without starting the detector.");
            }
        }
        if (!magnitudeExceeded) {
            if (peakPrevValues[0] == null) {
                peakPrevValues[0] = val;
            } else if (peakPrevValues[1] == null) {
                peakPrevValues[1] = val;
            } else if (peakPrevValues[2] == null) {
                peakPrevValues[2] = val;
            } else {
                peakPrevValues[0] = peakPrevValues[1];
                peakPrevValues[1] = peakPrevValues[2];
                peakPrevValues[2] = val;
            }
            magnitudeExceeded =
                    peakPrevValues[1] != null
                            && peakPrevValues[2] != null
                            && peakPrevValues[1] > magnitudeThreshold
                            && peakPrevValues[1] > peakPrevValues[0]
                            && peakPrevValues[1] > peakPrevValues[2];
        }
        if (!varianceExceeded) {
            sum += val;
            sumOfSquare += val * val;
            count++;
            double variance = ((count * sumOfSquare) - (sum * sum)) / (count * count);
            if (variance > varianceThreshold) {
                varianceExceeded = true;
            }

        }

        return magnitudeExceeded && varianceExceeded;
    }

    public long getStartTime() {
        return peakWindowStart;
    }

    @Override
    public String toString() {
        return "PeakDetector{" +
                "peakWindowStart=" + peakWindowStart +
                ", peakPrevValues=" + Arrays.toString(peakPrevValues) +
                '}';
    }
}
