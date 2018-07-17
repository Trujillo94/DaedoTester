package com.avant.eng.daedotester;

import android.Manifest;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

public class TestActivity extends AppCompatActivity {

    // Normal variables ----------------------------------------------------------------------------
    private TextView category;
    private TextView timerView;
    private TextView info;
    private TextView test_strength;
    private TextView test_regime;
    private TextView successful_score;
    private TextView failed_score;
    private TextView number_tests;
    private TextView number_kicks;
    private TextView speed_indicator;
    private ImageView connection_icon;
    private ImageButton connect_button;
    private ImageButton start;
    private ImageButton cancel;
    private LinearLayout connect_container;
    private LinearLayout start_container;
    private LinearLayout panel;
    private LinearLayout scoreboard;
    private ListView listview;
    //    private String[] values;
    final ArrayList<String> list = new ArrayList<>();
    ToneGenerator buzzer = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME);
    CountDownTimer timer;

    // Measurements & Counters
    public int tests_done, kicks_done, success_tests = 0, failed_tests;
    public SharedPreferences sharedPref;
    public String regimePref, strengthPref, maxTestsPref, maxKicksPref, testsTimePref, kicksTimePref, tolPref;
    private boolean test_correct = true;

    // USB variables -------------------------------------------------------------------------------
    private static final String TAG = TestActivity.class.getSimpleName();

    private PendingIntent mPermissionIntent;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private boolean mPermissionRequestPending;

    private UsbManager mUsbManager;
    private UsbDevice mDevice;
    private UsbDeviceConnection mUsbConnection;
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;

    private static final byte CHECK_CONNECTION = 0x10;
    private static final int CHECK_CONN_INT = 16;

    private static final byte CHANGE_CONFIG = 0x11;
    private static final byte AUTOLIFT_OFF = 0x0;
    private static final byte AUTOLIFT_ON = 0x1;

    private static final byte KICK_BYTE = 0x12;
    private static final byte STOP_BYTE = 0x13;
    private static final byte ACKNOWLEDGE_BYTE = 0x14;

    public static Boolean connection_status = false;
    public boolean testCompleted = false;

    public final UsbReceiver mUsbReceiver = new UsbReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openDevice(device);
                    } else {
                        Log.d(TAG, "permission denied for device "
                                + device);
                    }
                    mPermissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.equals(mDevice)) {
                    closeDevice();
                }
            }
            Toast.makeText(TestActivity.this, "Intent received.", Toast.LENGTH_SHORT).show();
        }
    };

    // Normal methods ------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_test);

        panel = findViewById(R.id.panel);
        scoreboard = findViewById(R.id.scoreboard);
        scoreboard.setVisibility(View.GONE);
        successful_score = findViewById(R.id.successful);
        successful_score.setVisibility(View.GONE);
        failed_score = findViewById(R.id.failed);
        failed_score.setVisibility(View.GONE);

        connect_container = findViewById(R.id.ConnectContent);
        connection_icon = findViewById(R.id.ConnectionIcon);
        connect_button = findViewById(R.id.ConnectButton);

        start_container = findViewById(R.id.StartContent);
        start = findViewById(R.id.Start);
        cancel = findViewById(R.id.Cancel);
        timerView = findViewById(R.id.Timer);
        info = findViewById(R.id.Info);

        test_strength = findViewById(R.id.tests_strength);
        test_regime = findViewById(R.id.tests_regime);
//        measurements = findViewById(R.id.);
        speed_indicator = findViewById(R.id.read_speed);
        number_tests = findViewById(R.id.tests_done);
        number_kicks = findViewById(R.id.kicks_done);
        Bundle bundle = this.getIntent().getExtras();

        getTestPreferences(bundle);

        timer = createNewTimer(kicksTimePref);

        // List View
        listview = findViewById(R.id.lectures_list);

        // USB onCreate lines ----------------------------------------------------------------------
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.EXTRA_PERMISSION_GRANTED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

//        try_connect();
        if (connection_status) {
            createNewTest();
        } else {
            createConnectButton();
        }
    }

    /**
     * Called when the activity is paused by the system.
     */
    @Override
    protected void onPause() {
        super.onPause();
        cancelTimer();
        if (mUsbManager.getDeviceList() != null) {
            closeDevice();
        }
    }

    void cancelTimer() {
        timer.cancel();
        buzzer.stopTone();
    }

    private CountDownTimer createNewTimer(String millisInFuture) {
        CountDownTimer timer = new CountDownTimer(Long.valueOf(millisInFuture) * 1000, 100) {
            int counter = 0;
            byte[] b;

            @Override
            public void onTick(long millisUntilFinished) {
                info.setText(R.string.kick_advertise);
                timerView.setText("" + ((millisUntilFinished / 1000) + 1));
                if (counter % 10 == 0) {
                    buzzer.startTone(ToneGenerator.TONE_PROP_BEEP);
                }
                counter = counter + 1;

                if (millisUntilFinished < 300) {
                    timerView.setText("READY");
                    info.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFinish() {
                counter = 0;
//                info.setVisibility(View.GONE);
//                cancel.setVisibility(View.GONE);
                buzzer.startTone(ToneGenerator.TONE_DTMF_A, 2000);
                sendKickOrder();
                if (connection_status) {
                    countKick();
                }
                this.cancel();
                b = readByte(1);
                if (b[0] != ACKNOWLEDGE_BYTE) {
//                        Toast.makeText(this,"Acknowledge did not received.",Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(TestActivity.this, TestCategory.class);
                    Bundle bundle = new Bundle();
                    intent.putExtras(bundle);
                    startActivity(intent);
                }
                if (!testCompleted) {
                    beginKick();
                }
            }
        };
        return timer;
    }

    private void getTestPreferences(Bundle bundle) {
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        tests_done = 0;
        kicks_done = 0;

        // LOAD CATEGORY (STRENGTH)
        if (bundle.getString("Category_String") == null) {
            test_strength.setText("null");
        } else {
            switch (bundle.getInt("Category_int")) {
                case R.integer.high:
                    strengthPref = sharedPref.getString("high_level", "");
                    tolPref = sharedPref.getString("high_tol", "");
                    break;
                case R.integer.medium:
                    strengthPref = sharedPref.getString("medium_level", "");
                    tolPref = sharedPref.getString("medium_tol", "");
                    break;
                case R.integer.low:
                    strengthPref = sharedPref.getString("low_level", "");
                    tolPref = sharedPref.getString("low_tol", "");
                    break;
                default:
                    strengthPref = "null";
            }

            switch (bundle.getInt("Category_int")) {
                case R.integer.high:
                    regimePref = sharedPref.getString("high_regime", "");
                    break;
                case R.integer.medium:
                    regimePref = sharedPref.getString("medium_regime", "");
                    break;
                case R.integer.low:
                    regimePref = sharedPref.getString("low_regime", "");
                    break;
                default:
                    regimePref = "null";
            }

            test_strength.setText(bundle.getString("Category_String") + " - " + strengthPref);
            test_regime.setText("Regime - " + regimePref + "%");
        }

        kicksTimePref = sharedPref.getString("time_kicks", "");
        testsTimePref = sharedPref.getString("time_tests", "");
        refreshCounts();
    }

    // USB methods ---------------------------------------------------------------------------------
    @Override
    public void onResume() {
        super.onResume();
//        try_connect();
        clearLectureList();
    }

    /**
     * Called when the activity is no longer needed prior to being removed from
     * the activity stack.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mUsbManager.getDeviceList() != null) {
            closeDevice();
        }
    }

    private void createNewTest() {
        connect_container.setVisibility(View.GONE);
        start_container.setVisibility(View.VISIBLE);
        cancel.setVisibility(View.GONE);
        start.setVisibility(View.VISIBLE);
        timerView.setVisibility(View.GONE);
        info.setVisibility(View.GONE);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkConnection();
                if (connection_status) {
                    Animation animationOFF = new AlphaAnimation(1, 0);
                    animationOFF.setDuration(200);
                    start.startAnimation(animationOFF);
                    start.setVisibility(View.GONE);

                    beginTest();
                } else {
                    showStartButton();
                    cancelTimer();
                }
            }
        });

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelTimer();
                showStartButton();
            }
        });
    }

    private void beginTest() {
        checkConnection();
        if (connection_status) {
            showCancelButton();
            beginKick();
        } else {
            showStartButton();
            cancelTimer();
        }
    }

    private void beginKick() {
        timerView.setVisibility(View.VISIBLE);
        info.setVisibility(View.VISIBLE);
        timer.start();
    }

    private void showStartButton() {
        Animation animationOFF = new AlphaAnimation(1, 0);
        animationOFF.setDuration(200);
        cancel.startAnimation(animationOFF);
        cancel.setVisibility(View.GONE);

        start.setVisibility(View.VISIBLE);
        Animation animationON = new AlphaAnimation(0, 1);
        animationON.setDuration(200);
        start.startAnimation(animationON);

        timerView.setVisibility(View.GONE);
        info.setVisibility(View.GONE);
    }

    private void showCancelButton() {
        cancel.setVisibility(View.VISIBLE);
        Animation animationON = new AlphaAnimation(0, 1);
        animationON.setDuration(200);
        cancel.startAnimation(animationON);
    }

    private void sendKickOrder() {
        byte[] buffer = new byte[2];
        buffer[0] = KICK_BYTE;
        buffer[1] = getDesiredRegime();
        byte[] tp_sl;
        int tp, sl;
        float incr_encd_time_pulse = 0;

        checkConnection();
        if (connection_status) {
            sendAutoLiftParam();
            tp_sl = sendByte(buffer, 5);
            tp = ((tp_sl[0] & 0xFF) << 0)
                    | ((tp_sl[1] & 0xFF) << 8)
                    | ((tp_sl[2] & 0xFF) << 16)
                    | ((tp_sl[3] & 0xFF) << 24);
            incr_encd_time_pulse = Float.intBitsToFloat(tp);
            sl = ((tp_sl[4] & 0xFF) << 32);
            newLecture(incr_encd_time_pulse, sl);
        } else {
            Toast.makeText(this, "Micro-controller not found.", Toast.LENGTH_SHORT).show();
        }
        Toast.makeText(this, "KICK COMPLETED.", Toast.LENGTH_SHORT).show();
    }


    private void newLecture(float tp, int lecture) {
        double pi = 3.1415, speed, L = 1;

//        speed = (3.6 * (2*pi/60) * L * (2500 / tp) / 35); // km/h
        speed = 15000 / tp;
        if (speed < 10000) {
            speed_indicator.setText(String.valueOf((int) speed) + " km/h");
        } else {
            speed_indicator.setText("- km/h");
        }

        updateLectureList(String.valueOf(lecture));

        if (Math.abs(lecture - Integer.parseInt(strengthPref)) > Integer.parseInt(tolPref)) {
            test_correct = false;
        }
    }

    private void updateLectureList(String value) {
        list.add(value);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
        listview.setAdapter(adapter);
    }

    private void clearLectureList() {
        listview.setAdapter(null);
    }

    private byte getDesiredRegime() {
        int desired_regime = Integer.parseInt(regimePref);
        return (byte) desired_regime;
    }

    private void countKick() {
        kicks_done = kicks_done + 1;
        if (kicks_done > Integer.parseInt(maxKicksPref)) {
            kicks_done = 1;
        }
        if (kicks_done == Integer.parseInt(maxKicksPref)) {
            timer = createNewTimer(testsTimePref);
            tests_done = tests_done + 1;
            if (test_correct) {
                success_tests = success_tests + 1;
            }
            test_correct = true;
        } else {
            timer = createNewTimer(kicksTimePref);
        }

        if (tests_done >= Integer.parseInt(maxTestsPref) & kicks_done >= Integer.parseInt(maxKicksPref)) {
            testCompleted = true;
            panel.setVisibility(View.GONE);
            cancel.setVisibility(View.GONE);
            scoreboard.setVisibility(View.VISIBLE);
            failed_tests = Integer.parseInt(maxTestsPref) - success_tests;
            if (success_tests > 0) {
                successful_score.setVisibility(View.VISIBLE);
                successful_score.setText("Passed: " + success_tests + "/" + maxTestsPref);
            }

            if (failed_tests > 0) {
                failed_score.setVisibility(View.VISIBLE);
                failed_score.setText("Failed: " + failed_tests + "/" + maxTestsPref);
            }
        }
        refreshCounts();
    }

    private void refreshCounts() {
        // LOAD MAX TESTS
        maxTestsPref = sharedPref.getString("max_tests", "");
        number_tests.setText("Tests: " + tests_done + "/" + maxTestsPref);

        // LOAD MAX KICKS
        maxKicksPref = sharedPref.getString("max_kicks", "");
        number_kicks.setText("Kicks: " + kicks_done + "/" + maxKicksPref);
    }

    void try_connect() {
        if (mInputStream == null || mOutputStream == null) {
            connect();
        }
        checkConnection();
    }

    private void checkConnection() {
        byte[] buffer_snd = new byte[1];
        byte[] buffer_rcv;

        buffer_snd[0] = CHECK_CONNECTION;
        buffer_rcv = sendByte(buffer_snd, 1);
        if (buffer_rcv[0] == CHECK_CONN_INT) {
            connection_status = true;
        } else {
            connection_status = false;
            cancelTimer();
            createConnectButton();
        }
        updateConnectionIcon();
    }

    private void sendAutoLiftParam() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean autoLift = sharedPref.getBoolean("lift_switch", false);
        byte[] buffer = new byte[2];
        buffer[0] = CHANGE_CONFIG;
        if (autoLift) {
            buffer[1] = AUTOLIFT_ON;
        } else {
            buffer[1] = AUTOLIFT_OFF;
        }

        sendByte(buffer, 1);
    }

    private void updateConnectionIcon() {
        if (connection_status) {
            connection_icon.setImageResource((R.drawable.usb_connected));
        } else {
            connection_icon.setImageResource((R.drawable.usb_disconnected));
        }
    }

    private void createConnectButton() {
        start_container.setVisibility(View.GONE);
        connect_container.setVisibility(View.VISIBLE);
        connect_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try_connect();

                if (connection_status) {
                    connect_container.setVisibility(View.GONE);
                    createNewTest();
                }
            }
        });
    }


    // COMMUNICATION FUNCTIONS ---------------------------------------------------------------------
    byte[] sendByte(byte[] buffer, int length) {
        byte readBytes[] = new byte[length];
        if (mOutputStream != null) {
            try {
                mOutputStream.write(buffer);
                readBytes = readByte(length);
            } catch (IOException e) {
            }
        }
        return readBytes;
    }

    byte[] readByte(int length) {
        byte[] b = new byte[length];
        if (mInputStream != null) {
            try {
                mInputStream.read(b);
            } catch (IOException e) {
            }
        }
        return b;
    }

    private void openDevice(UsbDevice device) {
        mUsbConnection = mUsbManager.openDevice(device);
        if (mUsbConnection != null) {
            mDevice = device;
        } else {
            Toast.makeText(TestActivity.this, "Couldn't open device.", Toast.LENGTH_SHORT).show();
        }
    }

    private void closeDevice() {
        try {
            if (mUsbConnection != null) {
                mUsbConnection.close();
            }
        } catch (Exception e) {
        } finally {
            mUsbConnection = null;
            mDevice = null;
        }
    }

    private void connect() {
        HashMap<String, UsbDevice> devices = mUsbManager.getDeviceList();
        UsbDevice device = (devices == null ? null : devices.get(0));

        if (device != null) {
            Toast.makeText(TestActivity.this, "DEVICE: " + device.getDeviceName(), Toast.LENGTH_SHORT).show();
            if (mUsbManager.hasPermission(device)) {
                openDevice(device);
            } else {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbManager.requestPermission(device,
                                mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            Toast.makeText(TestActivity.this, "DEVICE: null", Toast.LENGTH_SHORT).show();
        }
    }
}
