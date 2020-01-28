package kr.ac.inha.stress_sensor;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

import android.Manifest;
import android.os.Build;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.os.Bundle;

import org.json.JSONArray;

import java.io.IOException;

import org.json.JSONObject;

import android.app.Activity;
import android.widget.Button;
import android.widget.Toast;
import android.view.MenuItem;
import android.content.Intent;
import android.widget.Toolbar;

import org.json.JSONException;

import android.app.AlertDialog;
import android.widget.TextView;
import android.content.IntentFilter;
import android.widget.RelativeLayout;

import android.content.DialogInterface;
import android.content.SharedPreferences;

import kr.ac.inha.stress_sensor.old.ViewFilesActivity;
import kr.ac.inha.stress_sensor.receivers.ConnectionMonitor;
import kr.ac.inha.stress_sensor.receivers.ConnectionReceiver;
import kr.ac.inha.stress_sensor.services.AudioRecorder;
import kr.ac.inha.stress_sensor.services.CustomSensorsService;


public class MainActivity extends Activity {

    //region Constants
    private static final String TAG = "MainActivity";
    static int PERMISSION_ALL = 1;
    static String[] PERMISSIONS = {
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    //endregion

    //region UI variables
    private Button btnEMA;
    private TextView tvServiceStatus;
    private TextView tvInternetStatus;
    public TextView tvFileCount;
    public TextView tvDayNum;
    public TextView tvEmaNum;
    public TextView tvHBPhone;
    public TextView tvDataLoadedPhone;
    private RelativeLayout loadingPanel;
    private TextView ema_tv_8;
    private TextView ema_tv_11;
    private TextView ema_tv_14;
    private TextView ema_tv_17;
    private TextView ema_tv_20;
    private TextView ema_tv_23;
    //endregion

    private AudioRecorder audioRecorder;
    private Intent customSensorsService;
    ConnectionReceiver connectionReceiver;
    ConnectionMonitor connectionMonitor;
    IntentFilter intentFilter;

    private SharedPreferences loginPrefs;
    private SharedPreferences locationPrefs;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setActionBar((Toolbar) findViewById(R.id.my_toolbar));
        }

        //region Init UI variables
        btnEMA = findViewById(R.id.btn_late_ema);
        tvServiceStatus = findViewById(R.id.tvStatus);
        tvInternetStatus = findViewById(R.id.connectivityStatus);
        tvFileCount = findViewById(R.id.filesCountTextView);
        loadingPanel = findViewById(R.id.loadingPanel);
        tvDayNum = findViewById(R.id.txt_day_num);
        tvEmaNum = findViewById(R.id.ema_responses_phone);
        tvHBPhone = findViewById(R.id.heartbeat_phone);
        tvDataLoadedPhone = findViewById(R.id.data_loaded_phone);
        ema_tv_8 = findViewById(R.id.ema_tv_8);
        ema_tv_11 = findViewById(R.id.ema_tv_11);
        ema_tv_14 = findViewById(R.id.ema_tv_14);
        ema_tv_17 = findViewById(R.id.ema_tv_17);
        ema_tv_20 = findViewById(R.id.ema_tv_20);
        ema_tv_23 = findViewById(R.id.ema_tv_23);
        //endregion

        final SwipeRefreshLayout pullToRefresh = findViewById(R.id.pullToRefresh);
        pullToRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                updateStats();
                Tools.sendHeartbeat(getApplicationContext());
                pullToRefresh.setRefreshing(false);
            }
        });

        //region Registering BroadcastReciever for connectivity changed
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // only for LOLLIPOP and newer versions
            connectionMonitor = new ConnectionMonitor(this);
            connectionMonitor.enable();
        } else {
            intentFilter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
            intentFilter.addAction(getPackageName() + "android.net.wifi.WIFI_STATE_CHANGED");
            connectionReceiver = new ConnectionReceiver();
            registerReceiver(connectionReceiver, intentFilter);
        }
        //endregion

    }

    @Override
    protected void onResume() {
        super.onResume();
        loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
        locationPrefs = getSharedPreferences("UserLocations", MODE_PRIVATE);

        int ema_order = Tools.getEMAOrderFromRangeAfterEMA(Calendar.getInstance());
        if (ema_order == 0) {
            btnEMA.setVisibility(View.GONE);
        } else {
            boolean ema_btn_visible = loginPrefs.getBoolean("ema_btn_make_visible", true);
            if (!ema_btn_visible) {
                btnEMA.setVisibility(View.GONE);
            } else {
                btnEMA.setVisibility(View.VISIBLE);
            }
        }


        customSensorsService = new Intent(this, CustomSensorsService.class);
        initUserStats(true, 0, 0, new JSONArray(), 0, null);

        //region Update files counter
        /*File[] filePaths = getFilesDir().listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return (file.getPath().endsWith(".csv"));
            }
        });
        tvFileCount.setText("Files: " + String.valueOf(filePaths.length));*/
        //endregion

        //region Start service if all permissions are granted and service is not running yet
        if (!Tools.hasPermissions(this, PERMISSIONS)) {
            Tools.grantPermissions(this, PERMISSIONS);
        } else {
            if (!Tools.isMainServiceRunning(this)) {
                Log.e(TAG, "RESTART SERVICE");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(customSensorsService);
                } else {
                    startService(customSensorsService);
                }
            }
        }
        //endregion

        //region Update service running status
        if (Tools.isMainServiceRunning(this)) {
            tvServiceStatus.setTextColor(getResources().getColor(R.color.green));
            tvServiceStatus.setText(getString(R.string.service_runnig));
        } else {
            tvServiceStatus.setTextColor(getResources().getColor(R.color.red));
            tvServiceStatus.setText(getString(R.string.service_stopped));
        }
        //endregion

        updateStats();
    }

    public void initUserStats(boolean error, int dayNum, int emaNum, JSONArray ema_responses, int hbPhone, String dataLoadedPhone) {
        if (!error) {
            tvDayNum.setVisibility(View.VISIBLE);
            tvEmaNum.setVisibility(View.GONE);
            tvDataLoadedPhone.setVisibility(View.VISIBLE);
            tvHBPhone.setVisibility(View.VISIBLE);

            tvInternetStatus.setTextColor(getResources().getColor(R.color.green));
            if (hbPhone > 30)
                tvHBPhone.setTextColor(getResources().getColor(R.color.red));
            else
                tvHBPhone.setTextColor(getResources().getColor(R.color.green));

            tvInternetStatus.setText(getString(R.string.internet_on));

            tvDayNum.setText(getString(R.string.day_num, dayNum));
            tvEmaNum.setText(getString(R.string.ema_responses, emaNum));
            tvDataLoadedPhone.setText(getString(R.string.data_loaded, dataLoadedPhone));
            String last_active_text = hbPhone == 0 ? "just now" : formatMinutes(hbPhone) + " ago";
            tvHBPhone.setText(getString(R.string.last_active, last_active_text));

            try {
                if (ema_responses.getString(0).equals("1"))
                    ema_tv_8.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
                else
                    ema_tv_8.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);

                if (ema_responses.getString(1).equals("1"))
                    ema_tv_11.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
                else
                    ema_tv_11.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);

                if (ema_responses.getString(2).equals("1"))
                    ema_tv_14.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
                else
                    ema_tv_14.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);

                if (ema_responses.getString(3).equals("1"))
                    ema_tv_17.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
                else
                    ema_tv_17.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);

                if (ema_responses.getString(4).equals("1"))
                    ema_tv_20.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
                else
                    ema_tv_20.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);

                if (ema_responses.getString(5).equals("1"))
                    ema_tv_23.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
                else
                    ema_tv_23.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
            } catch (JSONException e) {
                e.printStackTrace();
            }


        } else {
            tvInternetStatus.setTextColor(getResources().getColor(R.color.red));
            tvInternetStatus.setText(getString(R.string.internet_off));
            tvDayNum.setVisibility(View.GONE);
            tvEmaNum.setVisibility(View.GONE);
            tvDataLoadedPhone.setVisibility(View.GONE);
            tvHBPhone.setVisibility(View.GONE);
            ema_tv_8.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
            ema_tv_11.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
            ema_tv_14.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
            ema_tv_17.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
            ema_tv_20.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
            ema_tv_23.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
        }
    }

    private String formatMinutes(int minutes) {
        if (minutes > 60) {
            if (minutes > 1440) {
                return minutes / 60 / 24 + "days";
            } else {
                int h = minutes / 60;
                float dif = (float) minutes / 60 - h;
                //Toast.makeText(MainActivity.this, dif + "", Toast.LENGTH_SHORT).show();
                int m = (int) (dif * 60);
                return h + "h " + m + "m";
            }
        } else
            return minutes + "m";
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        loadingPanel.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        loadingPanel.setVisibility(View.GONE);
    }

    public void updateStats() {
        if (Tools.isNetworkAvailable(this))
            Tools.execute(new MyRunnable(
                    this,
                    getString(R.string.url_usr_stat, getString(R.string.server_ip)),
                    loginPrefs.getString(SignInActivity.user_id, null),
                    loginPrefs.getString(SignInActivity.password, null)
            ) {
                @Override
                public void run() {
                    String url = (String) args[0];
                    String email = (String) args[1];
                    String password = (String) args[2];

                    try {
                        JSONObject body = new JSONObject();
                        body.put("username", email);
                        body.put("password", password);

                        JSONObject json = new JSONObject(Tools.post(url, body));

                        final int day_num = json.getInt("day_number");
                        final int ema_num = json.getInt("ema_responses_number");
                        final JSONArray ema_responses = json.getJSONArray("ema_responses");
                        final String data_phone = json.getString("data_loaded_phone");
                        final int hb_phone = json.getInt("heartbeat_phone");


                        switch (json.getInt("result")) {
                            case Tools.RES_OK:
                                Log.d(TAG, "Stats retrieved from server");
                                runOnUiThread(new MyRunnable(activity, json) {
                                    @Override
                                    public void run() {
                                        initUserStats(false, day_num, ema_num, ema_responses, hb_phone, data_phone);
                                    }
                                });

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {

                                    }
                                });
                                break;
                            case Tools.RES_FAIL:
                                Thread.sleep(200);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(activity, "Failed to retrieve stats", Toast.LENGTH_SHORT).show();
                                        initUserStats(true, 0, 0, null, 0, null);
                                    }
                                });
                                break;
                            case Tools.RES_SRV_ERR:
                                Thread.sleep(200);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(activity, "Failed to retrieve stats(SERVER)", Toast.LENGTH_SHORT).show();
                                        initUserStats(true, 0, 0, null, 0, null);
                                    }
                                });
                                Log.d(TAG, "Failed to retrieve stats(SERVER)");
                                break;
                            default:
                                break;
                        }
                    } catch (IOException | JSONException | InterruptedException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to retrieve");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity, "Failed to retrieve stats", Toast.LENGTH_SHORT).show();
                                initUserStats(true, 0, 0, null, 0, null);
                            }
                        });

                    }
                    enableTouch();
                }
            });
        else {
            Toast.makeText(MainActivity.this, "Please connect to Internet!", Toast.LENGTH_SHORT).show();
            initUserStats(true, 0, 0, null, 0, null);
        }
    }

    public void lateEMAClick(View view) {
        short ema_order = Tools.getEMAOrderFromRangeAfterEMA(Calendar.getInstance());
        if (ema_order != 0) {
            Intent intent = new Intent(this, EMAActivity.class);
            Log.e(TAG, "ema_order: " + ema_order);
            intent.putExtra("ema_order", ema_order);
            startActivity(intent);
        }
    }

    public void restartServiceClick(MenuItem item) {
        customSensorsService = new Intent(this, CustomSensorsService.class);
        stopService(customSensorsService);
        if (!Tools.hasPermissions(this, PERMISSIONS)) {
            Tools.grantPermissions(this, PERMISSIONS);
        } else {
            Log.e(TAG, "RESTART SERVICE");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(customSensorsService);
            } else {
                startService(customSensorsService);
            }

        }

        if (Tools.isMainServiceRunning(this)) {
            tvServiceStatus.setTextColor(getResources().getColor(R.color.green));
            tvServiceStatus.setText(getString(R.string.service_runnig));
        } else {
            tvServiceStatus.setTextColor(getResources().getColor(R.color.red));
            tvServiceStatus.setText(getString(R.string.service_stopped));
        }
    }

    public void setLocationsClick(MenuItem item) {
        Intent intent = new Intent(MainActivity.this, LocationsSettingActivity.class);
        startActivity(intent);
    }

    public void startAudioClick(MenuItem item) {
        audioRecorder = new AudioRecorder(String.format(Locale.getDefault(), "%s/%d.mp4", getCacheDir(), System.currentTimeMillis()));
        try {
            audioRecorder.start();
            Toast.makeText(MainActivity.this, "Audio recording Started!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Audio couldn't be recorded!");
        }
    }

    public void stopAudioClick(MenuItem item) {
        if (audioRecorder != null && audioRecorder.isRecording()) {
            audioRecorder.stop();
            Toast.makeText(MainActivity.this, "Audio recording Stopped!", Toast.LENGTH_SHORT).show();
            Tools.execute(new MyRunnable(null) {
                @Override
                public void run() {
                    try {
                        Tools.postFiles(getString(R.string.url_audio_submit, getString(R.string.server_ip)), loginPrefs.getString(SignInActivity.user_id, null), loginPrefs.getString(SignInActivity.password, null), new File(audioRecorder.getPath()));
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, String.format(Locale.getDefault(), "Couldn't process audio file %s", audioRecorder.getPath()));
                    }
                }
            });
        }
    }

    public void stopService(View view) {
        if (Tools.isMainServiceRunning(this))
            new AlertDialog.Builder(this)
                    .setTitle("Warning!")
                    .setMessage("Do you really want to stop the server?")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int whichButton) {
                            stopService(customSensorsService);
                            if (Tools.isMainServiceRunning(MainActivity.this)) {
                                tvServiceStatus.setTextColor(getResources().getColor(R.color.green));
                                tvServiceStatus.setText(getString(R.string.service_runnig));
                            } else {
                                tvServiceStatus.setTextColor(getResources().getColor(R.color.red));
                                tvServiceStatus.setText(getString(R.string.service_stopped));
                                //FileHelper.closeFile(getApplicationContext());
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.no, null).show();
        else
            Toast.makeText(MainActivity.this, "Service is not currently running", Toast.LENGTH_SHORT).show();
    }

    public void logoutClick(MenuItem item) {

        SharedPreferences.Editor editorLocation = locationPrefs.edit();
        editorLocation.clear();
        editorLocation.apply();

        SharedPreferences.Editor editorLogin = loginPrefs.edit();
        editorLogin.remove("username");
        editorLogin.remove("password");
        editorLogin.putBoolean("logged_in", false);
        editorLogin.remove("ema_btn_make_visible");
        editorLogin.clear();
        editorLogin.apply();

        GeofenceHelper.removeAllGeofences(this);
        stopService(customSensorsService);
        finish();
    }

    public void viewFiles(View view) {
        Intent intent = new Intent(MainActivity.this, ViewFilesActivity.class);
        startActivity(intent);
    }

    //region Not being called
    public void submitFilesToServer(View view) {
        File[] allFiles = getFilesDir().listFiles();
        Log.e(TAG, "Files num: " + allFiles.length);
        if (allFiles.length > 0) {
            loadingPanel.setVisibility(View.VISIBLE);
            for (File f : allFiles) {
                if (!f.isDirectory()) {
                    String extension = f.getName().substring(f.getName().lastIndexOf("."));
                    if (extension.equals(".csv"))
                        submitFileData(f);
                }
            }
        } else {
            Toast.makeText(MainActivity.this, "No sensor data yet!", Toast.LENGTH_SHORT).show();
        }

        //region Previous submission style
        /*short device = DatabaseHelper.SM_PHONE;
        Cursor cursor = db.getAllSensorData(device);
        if (cursor.moveToFirst())
            do {
                byte sensorId = (byte) cursor.getShort(0);
                long timestamp = cursor.getLong(1);
                int accuracy = cursor.getInt(2);
                String data = cursor.getString(3);
                //byte[] data = cursor.getBlob(3);
                submitRaw(sensorId, timestamp, accuracy, data, device);
            } while (cursor.moveToNext());
        cursor.close();*/
        //endregion
    }

    public void submitFileData(File file) {
        if (Tools.isNetworkAvailable(this))
            Tools.execute(new MyRunnable(
                    this,
                    getString(R.string.url_sensor_data_submit, getString(R.string.server_ip)),
                    loginPrefs.getString(SignInActivity.user_id, null),
                    loginPrefs.getString(SignInActivity.password, null),
                    file
            ) {
                @Override
                public void run() {
                    String url = (String) args[0];
                    String email = (String) args[1];
                    String password = (String) args[2];
                    File file = (File) args[3];

                    try {
                        JSONObject json = new JSONObject(Tools.postFiles(url, email, password, file));

                        Log.e(TAG, "Result: " + json);
                        switch (json.getInt("result")) {
                            case Tools.RES_OK:
                                Log.d(TAG, "Submitted to server");
                                if (file.delete()) {
                                    Log.e(TAG, "File " + file.getName() + " deleted");
                                } else {
                                    Log.e(TAG, "File " + file.getName() + " NOT deleted");
                                }
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        loadingPanel.setVisibility(View.GONE);
                                    }
                                });
                                break;
                            case Tools.RES_FAIL:
                                Thread.sleep(2000);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        loadingPanel.setVisibility(View.GONE);
                                    }
                                });
                                Log.e(TAG, "Submission to server Failed");
                                break;
                            case Tools.RES_SRV_ERR:
                                Thread.sleep(2000);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        loadingPanel.setVisibility(View.GONE);
                                    }
                                });
                                Log.d(TAG, "Submission Failed (SERVER)");
                                break;
                            default:
                                break;
                        }
                    } catch (IOException | JSONException | InterruptedException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Submission to server Failed");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                loadingPanel.setVisibility(View.GONE);
                            }
                        });
                    }
                    enableTouch();
                }
            });
        else {
            Toast.makeText(MainActivity.this, "Internet is OFF!", Toast.LENGTH_SHORT).show();
        }
    }
    //endregion

}
