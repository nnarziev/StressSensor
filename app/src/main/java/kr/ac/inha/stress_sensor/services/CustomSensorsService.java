package kr.ac.inha.stress_sensor.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import android.util.Log;


import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import kr.ac.inha.stress_sensor.DatabaseHelper;
import kr.ac.inha.stress_sensor.EMAActivity;
import kr.ac.inha.stress_sensor.FileHelper;
import kr.ac.inha.stress_sensor.R;
import kr.ac.inha.stress_sensor.receivers.ActivityTransitionsReceiver;
import kr.ac.inha.stress_sensor.Tools;
import kr.ac.inha.stress_sensor.receivers.ActivityRecognitionReceiver;
import kr.ac.inha.stress_sensor.receivers.CallReceiver;
import kr.ac.inha.stress_sensor.receivers.ScreenAndUnlockReceiver;

public class CustomSensorsService extends Service implements SensorEventListener {
    private static final String TAG = "CustomSensorsService";

    //region Constants
    private static final int ID_SERVICE = 101;
    public static final int EMA_NOTIFICATION_ID = 1234; //in sec
    public static final long EMA_NOTIF_EXPIRE = 3600;  //in sec
    public static final int EMA_BTN_VISIBLE_X_MIN_AFTER_EMA = 60; //min
    public static final int SERVICE_START_X_MIN_BEFORE_EMA = 3 * 60; //min
    public static final short HEARTBEAT_PERIOD = 5;  //in min
    public static final short APP_USAGE_SEND_PERIOD = 30;  //in sec
    public static final short DATA_SUBMIT_PERIOD = 5;  //in min
    private static final short LIGHT_SENSOR_READ_PERIOD = 5 * 60;  //in sec
    private static final short AUDIO_RECORDING_PERIOD = 5 * 60;  //in sec
    private static final int ACTIVITY_RECOGNITION_INTERVAL = 60; //in sec


    public static final short DATA_SRC_ACC = 1;
    public static final short DATA_SRC_STATIONARY_DUR = 2;
    public static final short DATA_SRC_SCREEN_ON_DUR = 3;
    public static final short DATA_SRC_STEP_DETECTOR = 4;
    public static final short DATA_SRC_UNLOCKED_DUR = 5;
    public static final short DATA_SRC_PHONE_CALLS = 6;
    public static final short DATA_SRC_LIGHT = 7;
    public static final short DATA_SRC_APP_USAGE = 8;
    public static final short DATA_SRC_GPS_LOCATIONS = 9;
    public static final short DATA_SRC_ACTIVITY = 10;
    public static final short DATA_SRC_TOTAL_DIST_COVERED = 11;
    public static final short DATA_SRC_MAX_DIST_FROM_HOME = 12;
    public static final short DATA_SRC_MAX_DIST_TWO_LOCATIONS = 13;
    public static final short DATA_SRC_RADIUS_OF_GYRATION = 14;
    public static final short DATA_SRC_STDDEV_OF_DISPLACEMENT = 15;
    public static final short DATA_SRC_NUM_OF_DIF_PLACES = 16;
    public static final short DATA_SRC_AUDIO_LOUDNESS = 17;
    public static final short DATA_SRC_ACTIVITY_DURATION = 18;
    //endregion

    DatabaseHelper db;

    long prevLightSensorReadingTime = 0;
    long prevAudioRecordedTime = 0;

    static boolean isAccelerometerSensing = false;
    static boolean isLightSensing = false;
    static boolean isAudioRecording = false;

    //private StationaryDetector mStationaryDetector;
    NotificationManager mNotificationManager;
    private SensorManager mSensorManager;
    private Sensor sensorLight;
    private Sensor sensorStepDetect;
    private Sensor sensorAcc;

    private ScreenAndUnlockReceiver mPhoneUnlockedReceiver;
    private CallReceiver mCallReceiver;

    //private AudioRecorder audioRecorder;
    private AudioFeatureRecorder audioFeatureRecorder;

    private ActivityRecognitionClient activityRecognitionClient;
    private PendingIntent activityRecPendingIntent;

    private ActivityRecognitionClient activityTransitionClient;
    private PendingIntent activityTransPendingIntent;

    ScheduledExecutorService dataSubmitScheduler = Executors.newSingleThreadScheduledExecutor();
    ScheduledExecutorService appUsageSubmitScheduler = Executors.newSingleThreadScheduledExecutor();
    ScheduledExecutorService heartbeatSendScheduler = Executors.newSingleThreadScheduledExecutor();

    private boolean canSendNotif = true;

    private Handler mHandler = new Handler();
    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            long curTimestamp = System.currentTimeMillis();
            Calendar curCal = Calendar.getInstance();

            //region Sending Notification periodically
            short ema_order = Tools.getEMAOrderAtExactTime(curCal);
            if (ema_order != 0 && canSendNotif) {
                Log.e(TAG, "EMA order 1: " + ema_order);
                sendNotification(ema_order);
                canSendNotif = false;
            }

            if (curCal.get(Calendar.MINUTE) != 0)
                canSendNotif = true;
            //endregion

            //region Registering ACC sensor periodically
            int nowHour = curCal.get(Calendar.HOUR_OF_DAY);
            if (6 <= nowHour && nowHour < 22)  // register ACC only between 06.00 and 22.00
            {
                int nowMinutes = curCal.get(Calendar.MINUTE);
                if (!isAccelerometerSensing && nowMinutes % 30 < 3) // register ACC with 3 min duration, every 30 min
                {
                    mSensorManager.registerListener(CustomSensorsService.this, sensorAcc, SensorManager.SENSOR_DELAY_GAME);
                    isAccelerometerSensing = true;
                } else if (isAccelerometerSensing && nowMinutes % 30 >= 3) // unregister ACC if it is recording more than  for 3 min, every 30 min
                {
                    mSensorManager.unregisterListener(CustomSensorsService.this, sensorAcc);
                    isAccelerometerSensing = false;
                }
            }
            //endregion

            //region Registering Light sensor periodically
            boolean canLightSense = curTimestamp > prevLightSensorReadingTime + LIGHT_SENSOR_READ_PERIOD * 1000;
            if (canLightSense) {
                Timer timer = new Timer();
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        if (isLightSensing)
                            if (sensorLight != null) {
                                mSensorManager.unregisterListener(CustomSensorsService.this, sensorLight);
                                isLightSensing = false;
                            }
                    }
                };
                timer.schedule(task, 1000);     // unregister Light sensor after 2000 ms

                if (!isLightSensing) {
                    if (sensorLight != null) {
                        mSensorManager.registerListener(CustomSensorsService.this, sensorLight, SensorManager.SENSOR_DELAY_NORMAL);
                        prevLightSensorReadingTime = curTimestamp;
                        isLightSensing = true;
                    }
                }
            }
            //endregion

            //region Registering Audio recorder periodically
            boolean canRecord = curTimestamp > prevAudioRecordedTime + AUDIO_RECORDING_PERIOD * 1000;
            if (canRecord) {
                audioFeatureRecorder = new AudioFeatureRecorder(CustomSensorsService.this);
                Timer timer = new Timer();
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        if (isAudioRecording) {
                            audioFeatureRecorder.stop();
                            isAudioRecording = false;
                        }
                    }
                };
                timer.schedule(task, 20 * 1000);  // unregister Audio record after 200 ms

                if (!isAudioRecording) {
                    audioFeatureRecorder.start();
                    prevAudioRecordedTime = curTimestamp;
                    isAudioRecording = true;
                }
            }
            //endregion

            /*
            //check the current time in desired range or not
            Calendar now = Calendar.getInstance();
            if (!isAccelerometerSensing && Tools.checkIfInEMARange(curCal)) {
                //region Register Acc sensor
                if (sensorAcc != null)
                    mSensorManager.registerListener(CustomSensorsService.this, sensorAcc, SensorManager.SENSOR_DELAY_GAME);
                else
                    Log.e(TAG, "Sensor ACC is NOT available");
                //endregion
                isAccelerometerSensing = true;

                //region Starting voice recording
                // TODO: start audio here
                audioRecorder = new AudioRecorder(String.format(Locale.getDefault(), "%s/%d.mp4", getCacheDir(), System.currentTimeMillis()));
                try {
                    audioRecorder.start();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Audio couldn't be recorded!");
                }
                //endregion
            } else if (isAccelerometerSensing && !Tools.checkIfInEMARange(curCal)) {
                if (sensorAcc != null)
                    mSensorManager.unregisterListener(CustomSensorsService.this, sensorAcc);
                isAccelerometerSensing = false;

                //region Stopping voice recording
                // TODO: end audio here
                if (audioRecorder != null && audioRecorder.isRecording()) {
                    audioRecorder.stop();
                    Tools.execute(new MyRunnable(null) {
                        @Override
                        public void run() {
                            try {
                                Tools.postFiles(getString(R.string.url_audio_submit,
                                        getString(R.string.server_ip)),
                                        Tools.loginPrefs.getString(SignInActivity.user_id, null),
                                        Tools.loginPrefs.getString(SignInActivity.password, null),
                                        new File(audioRecorder.getPath()));
                            } catch (IOException e) {
                                e.printStackTrace();
                                Log.e(TAG, String.format(Locale.getDefault(), "Couldn't process audio file %s", audioRecorder.getPath()));
                            }
                        }
                    });
                }
                //endregion
            }
            */

            mHandler.postDelayed(this, 2 * 1000);
        }
    };

    private boolean isDataSubmissionRunning = false;

    private Runnable SensorDataSubmitRunnable = new Runnable() {
        public void run() {
            if (isDataSubmissionRunning)
                return;
            isDataSubmissionRunning = true;
            try {
                long current_timestamp = System.currentTimeMillis();
                String filename = "sp_" + current_timestamp + ".csv";
                db.updateSensorDataForDelete();
                List<String[]> results_temp = db.getSensorData();
                if (results_temp.size() > 0) {

                    FileOutputStream fileOutputStream = openFileOutput(filename, Context.MODE_APPEND);

                    for (String[] raw : results_temp) {
                        String value = raw[0] + "," + raw[1] + "\n";
                        fileOutputStream.write(value.getBytes());
                    }

                    fileOutputStream.close();

                    db.deleteSensorData();
                }

                FileHelper.submitSensorData(getApplicationContext());
            } catch (Exception e) {
                e.printStackTrace();
            }
            isDataSubmissionRunning = false;
        }
    };

    private Runnable AppUsageSubmitRunnable = new Runnable() {
        public void run() {
            try {
                Tools.checkAndSendUsageAccessStats(getApplicationContext());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private Runnable HeartBeatSendRunnable = new Runnable() {
        public void run() {
            Tools.sendHeartbeat(CustomSensorsService.this);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        db = new DatabaseHelper(this);

        activityRecognitionClient = ActivityRecognition.getClient(getApplicationContext());
        activityRecPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 2, new Intent(getApplicationContext(), ActivityRecognitionReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT);
        activityRecognitionClient.requestActivityUpdates(ACTIVITY_RECOGNITION_INTERVAL * 1000, activityRecPendingIntent)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Registered: Activity Recognition");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Failed: Activity Recognition");
                    }
                });

        activityTransitionClient = ActivityRecognition.getClient(getApplicationContext());
        activityTransPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(getApplicationContext(), ActivityTransitionsReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT);
        activityTransitionClient.requestActivityTransitionUpdates(new ActivityTransitionRequest(getActivityTransitions()), activityTransPendingIntent)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Registered: Activity Transition");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Failed: Activity Transition " + e.toString());
                    }
                });

        isAccelerometerSensing = false;
        isLightSensing = false;

        sensorLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        sensorAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //region Register Step detector sensor
        sensorStepDetect = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        if (sensorStepDetect != null) {
            mSensorManager.registerListener(this, sensorStepDetect, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Log.e(TAG, "Step detector sensor is NOT available");
        }
        //endregion

        //region Register Phone unlock and Screen On state receiver
        mPhoneUnlockedReceiver = new ScreenAndUnlockReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mPhoneUnlockedReceiver, filter);
        //endregion


        //region Register Phone call logs receiver
        mCallReceiver = new CallReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        intentFilter.addAction(Intent.EXTRA_PHONE_NUMBER);
        registerReceiver(mCallReceiver, intentFilter);
        //endregion

        //region Posting Foreground notification when service is started
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channel_id = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? createNotificationChannel() : "";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channel_id)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSmallIcon(R.mipmap.ic_launcher_no_bg)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);
        Notification notification = builder.build();
        startForeground(ID_SERVICE, notification);
        //endregion

        mRunnable.run();
        heartbeatSendScheduler.scheduleAtFixedRate(HeartBeatSendRunnable, 0, HEARTBEAT_PERIOD, TimeUnit.MINUTES);
        dataSubmitScheduler.scheduleAtFixedRate(SensorDataSubmitRunnable, 0, DATA_SUBMIT_PERIOD, TimeUnit.MINUTES);
        appUsageSubmitScheduler.scheduleAtFixedRate(AppUsageSubmitRunnable, 0, APP_USAGE_SEND_PERIOD, TimeUnit.SECONDS);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public String createNotificationChannel() {
        String id = "YouNoOne_channel_id";
        String name = "You no one channel id";
        String description = "This is description";
        NotificationChannel mChannel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW);
        mChannel.setDescription(description);
        mChannel.enableLights(true);
        mChannel.setLightColor(Color.RED);
        mChannel.enableVibration(true);
        NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.createNotificationChannel(mChannel);
        return id;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        //region Unregister listeners
        mSensorManager.unregisterListener(this, sensorLight);
        mSensorManager.unregisterListener(this, sensorAcc);
        mSensorManager.unregisterListener(this, sensorStepDetect);
        activityRecognitionClient.removeActivityUpdates(activityRecPendingIntent);
        activityTransitionClient.removeActivityTransitionUpdates(activityTransPendingIntent);
        audioFeatureRecorder.stop();
        unregisterReceiver(mPhoneUnlockedReceiver);
        unregisterReceiver(mCallReceiver);
        mHandler.removeCallbacks(mRunnable);
        //endregion

        //region Stop foreground service
        stopForeground(false);
        mNotificationManager.cancel(ID_SERVICE);
        //endregion

        Tools.sleep(1000);

        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            String value = System.currentTimeMillis() + " " + event.values[0] + " " + event.values[1] + " " + event.values[2] + " " + Tools.getEMAOrderFromRangeBeforeEMA(System.currentTimeMillis());
            db.insertSensorData(DATA_SRC_ACC, value);
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            db.insertSensorData(DATA_SRC_STEP_DETECTOR, System.currentTimeMillis() + " " + Tools.getEMAOrderFromRangeBeforeEMA(System.currentTimeMillis()));
        } else if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            String value = System.currentTimeMillis() + " " + event.values[0] + " " + Tools.getEMAOrderFromRangeBeforeEMA(System.currentTimeMillis());
            db.insertSensorData(DATA_SRC_LIGHT, value);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public List<ActivityTransition> getActivityTransitions() {
        List<ActivityTransition> transitionList = new ArrayList<>();
        ArrayList<Integer> activities = new ArrayList<>(Arrays.asList(
                DetectedActivity.STILL,
                DetectedActivity.WALKING,
                DetectedActivity.RUNNING,
                DetectedActivity.ON_BICYCLE,
                DetectedActivity.IN_VEHICLE));
        for (int activity : activities) {
            transitionList.add(new ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build());

            transitionList.add(new ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build());
        }

        return transitionList;

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendNotification(short ema_order) {
        final NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent notificationIntent = new Intent(CustomSensorsService.this, EMAActivity.class);
        Log.e(TAG, "EMA order 2: " + ema_order);
        notificationIntent.putExtra("ema_order", ema_order);
        //PendingIntent pendingIntent = PendingIntent.getActivities(CustomSensorsService.this, 0, new Intent[]{notificationIntent}, 0);
        PendingIntent pendingIntent = PendingIntent.getActivity(CustomSensorsService.this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String channelId = this.getString(R.string.notif_channel_id);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this.getApplicationContext(), channelId);
        builder.setContentTitle(this.getString(R.string.app_name))
                .setTimeoutAfter(1000 * EMA_NOTIF_EXPIRE)
                .setContentText(this.getString(R.string.daily_notif_text))
                .setTicker("New Message Alert!")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher_no_bg)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, this.getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        final Notification notification = builder.build();
        notificationManager.notify(EMA_NOTIFICATION_ID, notification);

        final SharedPreferences loginPrefs = this.getSharedPreferences("UserLogin", MODE_PRIVATE);
        SharedPreferences.Editor editor = loginPrefs.edit();
        editor.putBoolean("ema_btn_make_visible", true);
        editor.apply();

        Intent gpsIntent = new Intent(this, SendGPSStats.class);
        gpsIntent.putExtra("ema_order", ema_order);
        this.startService(gpsIntent);
    }
}
