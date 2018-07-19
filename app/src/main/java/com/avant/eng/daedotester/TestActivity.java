package com.avant.eng.daedotester;

import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import static java.lang.System.currentTimeMillis;

public class TestActivity extends AppCompatActivity {

    // Normal variables ----------------------------------------------------------------------------
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
    private ListView listview_lectures;
    //    private String[] values;
    final ArrayList<String> list_lectures = new ArrayList<>();
    ToneGenerator buzzer = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME);
    CountDownTimer timer;

    // Measurements & Counters
    public int tests_done, kicks_done, success_tests = 0, failed_tests;
    public SharedPreferences sharedPref;
    public String regimePref, strengthPref, maxTestsPref, maxKicksPref, testsTimePref, kicksTimePref, tolPref;
    private boolean test_correct = true;

    // USB variables -------------------------------------------------------------------------------
    private UsbManager mUsbManager;
    private UsbDeviceConnection connection;
    private UsbSerialDevice serialDevice;
    private UsbSerialDevice serial;
    public List<Byte> buffer = new ArrayList<>();
    private Toast toast;

    private static final byte CHECK_CONNECTION = 0x10;
    private static final int CHECK_CONN_INT = 16;
    private static final long CONNECT_TIMEOUT = 10000;

    private static final byte CHANGE_CONFIG = 0x11;
    private static final byte AUTOLIFT_OFF = 0x00;
    private static final byte AUTOLIFT_ON = 0x01;

    private static final byte KICK_BYTE = 0x12;
    private static final byte STOP_BYTE = 0x13;
    private static final byte ACKNOWLEDGE_BYTE = 0x14;

    public static Boolean connection_status = false;
    public boolean testCompleted = false;


    // Normal methods ------------------------------------------------------------------------------
    @RequiresApi(api = Build.VERSION_CODES.M)
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
        listview_lectures = findViewById(R.id.lectures_list);

        // USB onCreate lines ----------------------------------------------------------------------
        mUsbManager = getSystemService(UsbManager.class);
        startUsbConnection();

        try_connect();
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
        stopUsbConnection();
    }

    void cancelTimer() {
        timer.cancel();
        buzzer.stopTone();
    }

    private CountDownTimer createNewTimer(String millisInFuture) {
        CountDownTimer timer = new CountDownTimer(Long.valueOf(millisInFuture) * 1000, 100) {
            int counter = 0;
            Byte[] b;

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
        try_connect();
        clearLectureList();
    }

    /**
     * Called when the activity is no longer needed prior to being removed from
     * the activity stack.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mUsbManager = null;
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
        byte[] byte_snd = new byte[2];
        byte_snd[0] = KICK_BYTE;
        byte_snd[1] = getDesiredRegime();
        byte[] tp_sl;
        int tp, sl;
        float incr_encd_time_pulse = 0;

//        checkConnection();
        if (connection_status) {
            sendAutoLiftParam();
            tp_sl = sendByte(byte_snd, 5);
            tp = ((tp_sl[0] & 0xFF) << 0)
                    | ((tp_sl[1] & 0xFF) << 8)
                    | ((tp_sl[2] & 0xFF) << 16)
                    | ((tp_sl[3] & 0xFF) << 24);
            incr_encd_time_pulse = Float.intBitsToFloat(tp);
            sl = ((tp_sl[4] & 0xFF) << 32);
            newLecture(incr_encd_time_pulse, sl);
        } else {
            toast("Micro-controller not found.");
        }
        toast("KICK COMPLETED.");
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
        list_lectures.add(value);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list_lectures);
        listview_lectures.setAdapter(adapter);
    }

    private void clearLectureList() {
        listview_lectures.setAdapter(null);
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
        if (mUsbManager == null) {
            startUsbConnection();
        }
        checkConnection();
    }

    private void checkConnection() {
        byte[] byte_snd = new byte[1];
        byte[] byte_rcv;

        byte_snd[0] = CHECK_CONNECTION;
        byte_rcv = sendByte(byte_snd, 1);
        if (byte_rcv[0] == CHECK_CONN_INT) {
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
        byte[] byte_snd = new byte[2];
        byte_snd[0] = CHANGE_CONFIG;
        if (autoLift) {
            byte_snd[1] = AUTOLIFT_ON;
        } else {
            byte_snd[1] = AUTOLIFT_OFF;
        }

        sendByte(byte_snd, 1);
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
    private void startUsbConnection() {

        Map<String, UsbDevice> usbDevices = mUsbManager.getDeviceList();

        if (!usbDevices.isEmpty()) {
            UsbDevice device;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                if (device != null) {
                    toast("Device found: " + device.getDeviceName());
                    if (mUsbManager.hasPermission(device)) {
                        startSerialConnection(mUsbManager, device);
                    } else {
                        toast("Permission denied");
                    }
                    return;
                }
            }
        }

        toast("Could not start USB connection - No devices found");
    }

    void startSerialConnection(UsbManager mUsbManager, UsbDevice device) {
        UsbDeviceConnection connection = mUsbManager.openDevice(device);
        serial = UsbSerialDevice.createUsbSerialDevice(device, connection);

        if (serial != null && serial.open()) {
            serial.setBaudRate(9600);
            serial.setDataBits(UsbSerialInterface.DATA_BITS_8);
            serial.setStopBits(UsbSerialInterface.STOP_BITS_1);
            serial.setParity(UsbSerialInterface.PARITY_NONE);
            serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
            serial.read(mCallback);

        }
    }

    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] data) {
            for (int i = 0; i < data.length; i++) {
                buffer.add(data[i]);
            }
            toast("Length: " + String.valueOf(buffer.size()));
        }
    };

    private void stopUsbConnection() {
        try {
            if (serialDevice != null) {
                serialDevice.close();
            }

            if (connection != null) {
                connection.close();
            }
        } finally {
            serialDevice = null;
            connection = null;
        }
    }

    Byte[] readByte(int length) {
        Byte[] B = buffer.toArray(new Byte[buffer.size()]);

        Iterator<Byte> iter = buffer.listIterator();
        for (int k = 0; k < length; k++) {
            if (iter.hasNext()) {
                iter.next();
                iter.remove();
            }
        }

        Byte[] b = new Byte[length];
        for (int i = 0; i < length; i++) {
            b[i] = B[i];
        }
        return b;
    }

    byte[] sendByte(byte[] writeBytes, int length) {
        byte[] out = new byte[length];
        Byte[] readBytes = new Byte[length];
        for (int i = 0; i < length; i++) {
            readBytes[i] = 0x00;
        }

        buffer.clear();
        try {
            serial.write(writeBytes);
            long t0 = System.currentTimeMillis();
            while (currentTimeMillis() - t0 < CONNECT_TIMEOUT) {
                try {
                    if (buffer.size() >= length) {
                        readBytes = readByte(length);
                        break;
                    }
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
        }

        toast("Written: " + String.valueOf(writeBytes[0]) + " Read: " + String.valueOf(readBytes[0]) + " Length: " + String.valueOf(buffer.size()));

        for (int i = 0; i < length; i++) {
            out[i] = readBytes[i];
        }
        return out;
    }

    private void toast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (toast != null) {
                    toast.cancel();
                    toast = null;
                }
                toast = Toast.makeText(TestActivity.this, message, Toast.LENGTH_SHORT);
                toast.show();
            }
        });
    }
}
