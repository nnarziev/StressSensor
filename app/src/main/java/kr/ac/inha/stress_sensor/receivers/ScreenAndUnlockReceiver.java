package kr.ac.inha.stress_sensor.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Objects;

import kr.ac.inha.stress_sensor.DatabaseHelper;
import kr.ac.inha.stress_sensor.Tools;

import static kr.ac.inha.stress_sensor.services.CustomSensorsService.DATA_SRC_SCREEN_ON_DUR;
import static kr.ac.inha.stress_sensor.services.CustomSensorsService.DATA_SRC_UNLOCKED_DUR;

public class ScreenAndUnlockReceiver extends BroadcastReceiver {
    public static final String TAG = "ScreenAndUnlockReceiver";

    private long phoneUnlockedDurationStart;
    private long screenONStartTime;
    private boolean unlocked = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        DatabaseHelper db = new DatabaseHelper(context);
        if (Objects.equals(intent.getAction(), Intent.ACTION_USER_PRESENT)) {
            Log.e(TAG, "Phone unlocked");
            unlocked = true;
            phoneUnlockedDurationStart = System.currentTimeMillis();
        } else if (Objects.equals(intent.getAction(), Intent.ACTION_SCREEN_OFF)) {
            Log.e(TAG, "Phone locked / Screen OFF");
            //region Handling phone locked state
            if (unlocked) {
                unlocked = false;
                long phoneUnlockedDurationEnd = System.currentTimeMillis();
                long phoneUnlockedDuration = (phoneUnlockedDurationEnd - phoneUnlockedDurationStart) / 1000; // in seconds
                String value = phoneUnlockedDurationStart + " " + phoneUnlockedDurationEnd + " " + phoneUnlockedDuration + " " + Tools.getEMAOrderFromRangeBeforeEMA(phoneUnlockedDurationStart);
                db.insertSensorData(DATA_SRC_UNLOCKED_DUR, value);
            }
            //endregion

            //region Handling screen OFF state
            long screenONEndTime = System.currentTimeMillis();
            long screenOnDuration = (screenONEndTime - screenONStartTime) / 1000; //seconds
            String value = screenONStartTime + " " + screenONEndTime + " " + screenOnDuration + " " + Tools.getEMAOrderFromRangeBeforeEMA(screenONStartTime);
            db.insertSensorData(DATA_SRC_SCREEN_ON_DUR, value);
            //endregion

        } else if (Objects.equals(intent.getAction(), Intent.ACTION_SCREEN_ON)) {
            Log.e(TAG, "Screen ON");
            screenONStartTime = System.currentTimeMillis();
        }
    }
}
