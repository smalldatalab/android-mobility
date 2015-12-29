package org.ohmage.mobility;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.EnumMap;
import java.util.Map;

/**
 * Created by changun on 12/29/15.
 */
public class HMMModel {
    static EnumMap<State, Double> DEFAULT_INIT_PROBABILITY = new EnumMap<State, Double>(State.class);

    static {
        DEFAULT_INIT_PROBABILITY.put(State.WALKING, 0.05);
        DEFAULT_INIT_PROBABILITY.put(State.RUNNING, 0.05);
        DEFAULT_INIT_PROBABILITY.put(State.VEHICLE, 0.05);
        DEFAULT_INIT_PROBABILITY.put(State.STILL, 0.80);
        DEFAULT_INIT_PROBABILITY.put(State.BICYCLE, 0.05);
    }

    EnumMap<State, Double> mPrevProbability;

    public HMMModel() {
        mPrevProbability = initStateProbability();

    }

    EnumMap<State, Double> emissionProbability(ActivityRecognitionResult act) {
        EnumMap<State, Double> map = new EnumMap<State, Double>(State.class);
        double walking = 0, running = 0, vehicle = 0, still = 0, bicycle = 0;
        for (DetectedActivity activity : act.getProbableActivities()) {
            double confidence = activity.getConfidence();
            switch (activity.getType()) {
                case DetectedActivity.ON_FOOT:
                case DetectedActivity.WALKING:
                    walking += confidence;
                    break;
                case DetectedActivity.ON_BICYCLE:
                    bicycle += confidence;
                    break;
                case DetectedActivity.RUNNING:
                    running += confidence;
                    break;
                case DetectedActivity.STILL:
                    still += confidence;
                    break;
                case DetectedActivity.TILTING:
                    double portion = confidence / 9;
                    double twoPortions = portion + portion;
                    walking += twoPortions;
                    running += twoPortions;
                    bicycle += twoPortions;
                    vehicle += twoPortions;
                    still += portion;
                    break;
            }
        }
        double normalizer = walking + running + vehicle + still + bicycle;
        map.put(State.WALKING, walking / normalizer);
        map.put(State.STILL, still / normalizer);
        map.put(State.RUNNING, running / normalizer);
        map.put(State.BICYCLE, bicycle / normalizer);
        map.put(State.VEHICLE, vehicle / normalizer);
        return map;

    }

    double transitionProbability(State from, State to) {

        if (from == State.STILL && to == State.VEHICLE) {
            return 0.05;
        } else if (from == State.STILL && (to == State.WALKING || to == State.RUNNING || to == State.BICYCLE)) {
            return 0.1;
        } else if (from == State.STILL && to == State.STILL) {
            return 0.65;
        } else if (from == State.VEHICLE && to == State.STILL) {
            return 0.1;
        } else if (from == State.VEHICLE && (to == State.WALKING || to == State.RUNNING || to == State.BICYCLE)) {
            return 0.05;
        } else if (from == State.VEHICLE && to == State.VEHICLE) {
            return 0.75;
        } else if ((from == State.WALKING || from == State.RUNNING) && (to == State.STILL || to == State.VEHICLE || to == State.BICYCLE)) {
            return 0.1;
        } else if ((from == State.WALKING || from == State.RUNNING) && (to == State.WALKING || to == State.RUNNING)) {
            if (from == to) {
                return 0.5;
            } else {
                return 0.2;
            }
        } else if (from == State.BICYCLE && to == State.STILL) {
            return 0.1;
        } else if (from == State.BICYCLE && (to == State.WALKING || to == State.RUNNING)) {
            return 0.1;
        } else if (from == State.BICYCLE && to == State.VEHICLE) {
            return 0.05;
        } else if (from == State.BICYCLE && to == State.BICYCLE) {
            return 0.65;
        }
        throw new RuntimeException("transitionProbability is incomplete!");

    }

    EnumMap<State, Double> initStateProbability() {
        return DEFAULT_INIT_PROBABILITY;
    }

    State push(ActivityRecognitionResult res) {
        EnumMap<State, Double> emissionProb = emissionProbability(res);
        EnumMap<State, Double> newProbability = new EnumMap<State, Double>(State.class);
        double normalizer = 0;
        State mostProbState = null;
        double maximumProb = 0.0;
        for (State to : State.values()) {
            double probSum = 0.0;
            for (State from : State.values()) {
                probSum += mPrevProbability.get(from) * transitionProbability(from, to) * emissionProb.get(to);
            }
            newProbability.put(to, probSum);
            normalizer += probSum;
            if (probSum > maximumProb) {
                mostProbState = to;
                maximumProb = probSum;
            }
        }
        for (State to : State.values()) {
            newProbability.put(to, newProbability.get(to) / normalizer);
        }
        mPrevProbability = newProbability;
        return mostProbState;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<State, Double> s : mPrevProbability.entrySet()) {
            sb.append(s.getKey().name()).append(":").append(s.getValue()).append(",");
        }
        ;
        return sb.toString();
    }

    public void restore(double walking, double running, double vehicle, double still, double bicycle) {
        EnumMap<State, Double> map = new EnumMap<State, Double>(State.class);
        map.put(State.WALKING, walking);
        map.put(State.STILL, still);
        map.put(State.RUNNING, running);
        map.put(State.BICYCLE, bicycle);
        map.put(State.VEHICLE, vehicle);
    }

    enum State {
        WALKING, RUNNING, VEHICLE, STILL, BICYCLE
    }


}
