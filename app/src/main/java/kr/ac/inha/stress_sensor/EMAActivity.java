package kr.ac.inha.stress_sensor;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import static kr.ac.inha.stress_sensor.services.CustomSensorsService.EMA_NOTIFICATION_ID;

public class EMAActivity extends AppCompatActivity {

    //region Constants
    public static final String TAG = "EMAActivity";
    public static final Short[] EMA_NOTIF_HOURS = {8, 11, 14, 17, 20, 23};  //in hours of day
    public static final long[] EMA_NOTIF_MILLIS = new long[]{EMA_NOTIF_HOURS[0] * 3600 * 1000, EMA_NOTIF_HOURS[1] * 3600 * 1000, EMA_NOTIF_HOURS[2] * 3600 * 1000, EMA_NOTIF_HOURS[3] * 3600 * 1000, EMA_NOTIF_HOURS[4] * 3600 * 1000, EMA_NOTIF_HOURS[5] * 3600 * 1000};  //in milliseconds
    //endregion

    //region UI  variables
    TextView question1;
    TextView question2;
    TextView question3;
    TextView question4;

    SeekBar seekBar1;
    SeekBar seekBar2;
    SeekBar seekBar3;
    SeekBar seekBar4;

    Button btnSubmit;
    //endregion

    DatabaseHelper db;
    private short emaOrder;

    private SharedPreferences loginPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
        if (!loginPrefs.getBoolean("logged_in", false)) {
            finish();
        }
        setContentView(R.layout.activity_ema);
        db = new DatabaseHelper(this);
        init();
    }

    public void init() {
        question1 = findViewById(R.id.question1);
        question2 = findViewById(R.id.question2);
        question3 = findViewById(R.id.question3);
        question4 = findViewById(R.id.question4);

        seekBar1 = findViewById(R.id.scale_q1);
        seekBar2 = findViewById(R.id.scale_q2);
        seekBar3 = findViewById(R.id.scale_q3);
        seekBar4 = findViewById(R.id.scale_q4);

        btnSubmit = findViewById(R.id.btn_submit);

        //emaResponses = new EmaResponses();
        //current_question = 1;
        emaOrder = getIntent().getShortExtra("ema_order", (short) -1);

        //prepareViewForQuestion(current_question);
    }

    public void clickSubmit(View view) {

        long timestamp = System.currentTimeMillis();

        int answer1 = seekBar1.getProgress() + 1;
        int answer2 = seekBar2.getProgress() + 1;
        int answer3 = 5;
        int answer4 = 5;
        switch (seekBar3.getProgress() + 1) {
            case 1:
                answer3 = 5;
                break;
            case 2:
                answer3 = 4;
                break;
            case 3:
                answer3 = 3;
                break;
            case 4:
                answer3 = 2;
                break;
            case 5:
                answer3 = 1;
                break;
        }
        switch (seekBar4.getProgress() + 1) {
            case 1:
                answer4 = 5;
                break;
            case 2:
                answer4 = 4;
                break;
            case 3:
                answer4 = 3;
                break;
            case 4:
                answer4 = 2;
                break;
            case 5:
                answer4 = 1;
                break;
        }

        if (Tools.isNetworkAvailable(this))
            Tools.execute(new MyRunnable(
                    this,
                    getString(R.string.url_ema_submit, getString(R.string.server_ip)),
                    loginPrefs.getString(SignInActivity.user_id, null),
                    loginPrefs.getString(SignInActivity.password, null),
                    timestamp,
                    answer1,
                    answer2,
                    answer3,
                    answer4
            ) {
                @Override
                public void run() {
                    String url = (String) args[0];
                    String email = (String) args[1];
                    String password = (String) args[2];
                    long timestamp = (long) args[3];
                    int ans1 = (int) args[4];
                    int ans2 = (int) args[5];
                    int ans3 = (int) args[6];
                    int ans4 = (int) args[7];
                    try {
                        JSONObject body = new JSONObject();
                        body.put("username", email);
                        body.put("password", password);
                        body.put("ema_timestamp", timestamp);
                        body.put("ema_order", emaOrder);
                        String answers = String.format(Locale.US, "%d %d %d %d",
                                ans1,
                                ans2,
                                ans3,
                                ans4);
                        body.put("answers", answers);

                        Log.e(TAG, "EMA: " + body.toString());
                        JSONObject json = new JSONObject(Tools.post(url, body));
                        switch (json.getInt("result")) {
                            case Tools.RES_OK:
                                runOnUiThread(new MyRunnable(activity, args) {
                                    @Override
                                    public void run() {
                                        Toast.makeText(activity, "Submitted to Server", Toast.LENGTH_SHORT).show();
                                        finish();
                                    }
                                });
                                break;
                            case Tools.RES_FAIL:
                                Thread.sleep(2000);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(activity, "Failed to submit to server", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                break;
                            case Tools.RES_SRV_ERR:
                                Thread.sleep(2000);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(activity, "Failed to submit. (SERVER SIDE ERROR)", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                break;
                            default:
                                break;
                        }
                    } catch (IOException | JSONException | InterruptedException e) {
                        e.printStackTrace();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity, "Failed to submit to server", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    enableTouch();
                }
            });
        else {
            Log.d(TAG, "No connection case");
            String answers = String.format(Locale.US, "%d %d %d %d",
                    answer1,
                    answer2,
                    answer3,
                    answer4);

            boolean isInserted = db.insertEMAData(emaOrder, timestamp, answers);
            if (isInserted) {
                Toast.makeText(this, "Response saved", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish();
            } else
                Log.d(TAG, "Failed to save");

        }

        SharedPreferences.Editor editor = loginPrefs.edit();
        editor.putBoolean("ema_btn_make_visible", false);
        editor.apply();

        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(EMA_NOTIFICATION_ID);

        /*
        SharedPreferences.Editor editor = Tools.loginPrefs.edit();
        editor.putLong("ema_btn_set_visible", 0);
        editor.putInt("ema_order", -1);
        editor.apply();*/
    }
}
