package kr.ac.inha.stress_sensor.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;

import kr.ac.inha.stress_sensor.DatabaseHelper;
import kr.ac.inha.stress_sensor.Tools;

import static kr.ac.inha.stress_sensor.services.CustomSensorsService.DATA_SRC_ACTIVITY_DURATION;

public class ActivityTransitionsReceiver extends BroadcastReceiver {
    public static final String TAG = "ActivityTransReceiver";

    static long start_time_STILL = 0;
    static long start_time_WALKING = 0;
    static long start_time_RUNNING = 0;
    static long start_time_ON_BICYCLE = 0;
    static long start_time_IN_VEHICLE = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            DatabaseHelper db = new DatabaseHelper(context);
            if (ActivityTransitionResult.hasResult(intent)) {
                ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
                if (result != null)
                    for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                        if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                            long start_timestamp = System.currentTimeMillis();
                            switch (event.getActivityType()) {
                                case DetectedActivity.STILL:
                                    start_time_STILL = start_timestamp;
                                    break;
                                case DetectedActivity.WALKING:
                                    start_time_WALKING = start_timestamp;
                                    break;
                                case DetectedActivity.RUNNING:
                                    start_time_RUNNING = start_timestamp;
                                    break;
                                case DetectedActivity.ON_BICYCLE:
                                    start_time_ON_BICYCLE = start_timestamp;
                                    break;
                                case DetectedActivity.IN_VEHICLE:
                                    start_time_IN_VEHICLE = start_timestamp;
                                    break;
                                default:
                                    break;
                            }
                        } else if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                            long activity_end_time = System.currentTimeMillis();
                            long activity_start_time = 0;
                            String activity_name = "";
                            switch (event.getActivityType()) {
                                case DetectedActivity.STILL:
                                    activity_start_time = start_time_STILL;
                                    activity_name = "STILL";
                                    break;
                                case DetectedActivity.WALKING:
                                    activity_start_time = start_time_WALKING;
                                    activity_name = "WALKING";
                                    break;
                                case DetectedActivity.RUNNING:
                                    activity_start_time = start_time_RUNNING;
                                    activity_name = "RUNNING";
                                    break;
                                case DetectedActivity.ON_BICYCLE:
                                    activity_start_time = start_time_ON_BICYCLE;
                                    activity_name = "ON_BICYCLE";
                                    break;
                                case DetectedActivity.IN_VEHICLE:
                                    activity_start_time = start_time_IN_VEHICLE;
                                    activity_name = "IN_VEHICLE";
                                    break;
                                default:
                                    break;
                            }
                            if (activity_start_time > 0) {
                                long duration = (activity_end_time - activity_start_time) / 1000;
                                db.insertSensorData(DATA_SRC_ACTIVITY_DURATION, activity_start_time + " " + activity_end_time + " " + activity_name + " " + duration + " " + Tools.getEMAOrderFromRangeBeforeEMA(activity_end_time));
                            }
                        }
                    }
            }
        }
    }
}
