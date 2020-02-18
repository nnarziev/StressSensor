package kr.ac.inha.stress_sensor.receivers;

import android.content.Context;
import android.util.Log;

import kr.ac.inha.stress_sensor.DatabaseHelper;
import kr.ac.inha.stress_sensor.Tools;
import kr.ac.inha.stress_sensor.services.AudioFeatureRecorder;
import kr.ac.inha.stress_sensor.services.CustomSensorsService;

import static kr.ac.inha.stress_sensor.services.CustomSensorsService.DATA_SRC_PHONE_CALLS;
import static kr.ac.inha.stress_sensor.services.CustomSensorsService.audioFeatureRecorder;

public class CallReceiver extends PhonecallReceiver {
    public static final String TAG = "CallReceiver";
    final String CALL_TYPE_OUTGOING = "OUT";
    final String CALL_TYPE_INCOMING = "IN";
    public static boolean AudioRunningForCall = false;

    @Override
    protected void onOutgoingCallEnded(Context ctx, String number, long start, long end) {
        DatabaseHelper db = new DatabaseHelper(ctx);
        Log.e(TAG, "onOutgoingCallEnded -> " + "number: " + number + "; start date: " + start + "; end date: " + end);
        long duration = (end - start) / 1000; // in seconds
        String value = start + " " + end + " " + CALL_TYPE_OUTGOING + " " + duration + " " + Tools.getEMAOrderFromRangeBeforeEMA(start);
        db.insertSensorData(DATA_SRC_PHONE_CALLS, value);
        //finish the audio
        AudioRunningForCall = false;
    }

    @Override
    protected void onIncomingCallEnded(Context ctx, String number, long start, long end) {
        DatabaseHelper db = new DatabaseHelper(ctx);
        Log.e(TAG, "onIncomingCallEnded -> " + "number: " + number + "; start date: " + start + "; end date: " + end);
        long duration = (end - start) / 1000; // in seconds
        String value = start + " " + end + " " + CALL_TYPE_INCOMING + " " + duration + " " + Tools.getEMAOrderFromRangeBeforeEMA(start);
        db.insertSensorData(DATA_SRC_PHONE_CALLS, value);
        //finish the audio
        AudioRunningForCall = false;
    }

    @Override
    protected void onIncomingCallReceived(Context ctx, String number, long start) {
        Log.e(TAG, "onIncomingCallReceived -> " + "number: " + number + "; start date: " + start);
        //start the audio
        AudioRunningForCall = true;
    }

    @Override
    protected void onIncomingCallAnswered(Context ctx, String number, long start) {
        Log.e(TAG, "onIncomingCallAnswered -> " + "number: " + number + "; start date: " + start);
    }

    @Override
    protected void onOutgoingCallStarted(Context ctx, String number, long start) {
        Log.e(TAG, "onOutgoingCallStarted -> " + "number: " + number + "; start date: " + start);
        //start the audio
        AudioRunningForCall = true;
    }

    @Override
    protected void onMissedCall(Context ctx, String number, long start) {
        Log.e(TAG, "onMissedCall -> " + "number: " + number + "; start date: " + start);
        //finish the audio
        AudioRunningForCall = false;
    }
}
