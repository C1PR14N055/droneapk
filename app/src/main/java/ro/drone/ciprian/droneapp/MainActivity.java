package ro.drone.ciprian.droneapp;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // API level
    int apiLevel;

    // networking stuff
    BufferedReader in = null;
    PrintWriter out = null;
    private boolean tcpIsConnected = true;
    private String messageStr;
    private boolean sendData = true;

    // Flight commands
    private int cmd = 1; // command to send
    private long lastCmdTimestamp = 0;
    private static final int delayResendCmd = 33;
    private static final int delayUpdateThrottle = 33;

    // Flight status
    int conn_status = -1;
    int heading = 0;
    int alt = 0;
    int angle_x = 0;
    int angle_y = 0;
    int angle_z = 0;

    // Device singleton instance
    Device device;

    //UI post handler
    Handler handler;

    //Vibrator
    Vibrator v;

    // Settings
    SharedPreferences prefs;
    String wifiSSID;
    boolean useController;
    boolean enableVibration;
    boolean enableSound;
    boolean overclockWIFI;
    boolean safeAutoLand;
    boolean captureBackButton;
    int portNumber;
    int warningsDelay;

    //TTS
    TextToSpeech tts;

    // Header / Menu
    // Connection Status
    CircleView conn_icon;
    TextView conn;

    // Battery status
    ImageView battery_icon;
    TextView battery;

    // Wifi signal status
    ImageView wifi_icon;
    TextView wifi;

    // Compas / Heading
    ImageView compas_icon;
    TextView compas;

    // Altitude
    ImageView altitude_icon;
    TextView altitude;

    // Angles / inclination
    TextView angX;
    TextView angY;
    TextView angZ;

    // Dot menu
    ImageButton menuBtn;

    //WebView
    WebView webView;

    //JoyStick
    LinearLayout jsLayout;
    FrameLayout jsLayoutLeft;
    FrameLayout jsLayoutRight;
    JoyStick jsLeft;
    JoyStick jsRight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initAll();
        updateBatteryStatus(); // broadcast recv
        openWebView();
        updateUI();
        tcpClient();
        updThread();
        handleJoysticks();
        updateCompas(107);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                //webView.evaluateJavascript("stop();", null);
            }
            else {
                //webView.loadUrl("javascript:stop();");
            }
        }
    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();
        if (captureBackButton) {
            Toast.makeText(MainActivity.this, "Back button captured!", Toast.LENGTH_SHORT).show();
        }
    }

    public void setSettings() {
        wifiSSID = prefs.getString("wifiSSID", "XDRONE");
        useController = prefs.getBoolean("useController", false);
        enableVibration = prefs.getBoolean("enableVibration", true);
        enableSound = prefs.getBoolean("enableSound", true);
        overclockWIFI = prefs.getBoolean("overclockWIFI", false);
        safeAutoLand = prefs.getBoolean("safeAutoLand", true);
        captureBackButton = prefs.getBoolean("captureBackButton", true);
        portNumber = Integer.valueOf(prefs.getString("portNumber", "12345"));
        warningsDelay = Integer.valueOf(prefs.getString("warningsDelay", "7000"));

        if (useController) {
            jsLayout.setVisibility(View.GONE);
        }
        else {
            jsLayout.setVisibility(View.VISIBLE);
        }

    }

    SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            setSettings();
            Toast.makeText(MainActivity.this, "Settings updated!", Toast.LENGTH_SHORT).show();
        }
    };

    public void showPopup(View v) {
        PopupMenu popup = new PopupMenu(MainActivity.this, menuBtn);
        popup.getMenuInflater().inflate(R.menu.menu, popup.getMenu());

        //registering popup with OnMenuItemClickListener
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem item) {
                final int itemId = item.getItemId();
                if (itemId == R.id.settings) {
                    Intent intent = new Intent(MainActivity.this, PreferencesActivity.class);
                    startActivity(intent);
                    return true;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("WARNING")
                        .setMessage("Are you sure you want to continue?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                lastCmdTimestamp = System.currentTimeMillis();
                                switch (itemId) {
                                    case R.id.arm: {
                                        cmd = Constants.CMD_ARM;
                                        Toast.makeText(MainActivity.this, "Board Armed", Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                    case R.id.disarm: {
                                        cmd = Constants.CMD_DISARM;
                                        Toast.makeText(MainActivity.this, "Board Disarmed", Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                    case R.id.reboot: {
                                        cmd = Constants.CMD_REBOOT;
                                        Toast.makeText(MainActivity.this, "Shutting Down Pi", Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                    case R.id.shutdown: {
                                        cmd = Constants.CMD_SHUTDOWN;
                                        Toast.makeText(MainActivity.this, "Shutting Down Pi", Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                    case R.id.exit: {
                                        System.exit(0);
                                    }
                                }
                                Log.d("CMD", "" + cmd);
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
                return true;
            }
        });
        popup.show(); //showing popup menu
    }

    String lastWarning = "";
    long lastWarningTimeStamp = 0;

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void speak(String text) {
        if (!enableSound) return;
        if (!lastWarning.equals(text) || System.currentTimeMillis() - lastWarningTimeStamp > warningsDelay) {
            lastWarning = text;
            lastWarningTimeStamp = System.currentTimeMillis();
            if (apiLevel >= Build.VERSION_CODES.M) {
                tts.speak(text, TextToSpeech.QUEUE_ADD, null, null);
            } else {
                tts.speak(text, TextToSpeech.QUEUE_ADD, null);
            }
        }
    }

    private void vibrate() {
        if (enableVibration) {
            v.vibrate(100);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            Log.d("INPUT KEYCODE", String.valueOf(event.getKeyCode()) + " FROM: " + event.getDeviceId());
            return true; // if handled
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        Log.d("Motion event:", event.toString());
        // Check that the event came from a game controller
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) ==
                InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {

            // Process all historical movement samples in the batch
            final int historySize = event.getHistorySize();

            // Process the movements starting from the
            // earliest historical position in the batch
            for (int i = 0; i < historySize; i++) {
                // Process the event at historical position i
                Controller.processJoystickInput(event, i);
            }

            // Process the current movement sample in the batch (position -1)
            Controller.processJoystickInput(event, -1);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private void injectCSS() {
        try {
            InputStream inputStream = getAssets().open("style.css");
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();
            String encoded = Base64.encodeToString(buffer, Base64.NO_WRAP);
            webView.loadUrl("javascript:(function() {" +
                    "var parent = document.getElementsByTagName('head').item(0);" +
                    "var style = document.createElement('style');" +
                    "style.type = 'text/css';" +
                    // Tell the browser to BASE64-decode the string into your script !!!
                    "style.innerHTML = window.atob('" + encoded + "');" +
                    "parent.appendChild(style)" +
                    "})();");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tcpClient() {

        // TCP THREAD
        Thread tcpThread = new Thread() {
            public void run() {

                Socket socket = null;
                tcpIsConnected = false;
                JSONObject jo = null;

                while(true) {

                    try {
                        if (socket != null && socket.isConnected()
                                && in != null && out != null) {

                            String msg = in.readLine();
                            if (msg != null && !msg.equals("---")) {
                                jo = new JSONObject(msg);
                                heading = (int) jo.getDouble("heading");
                                angle_x = (int) jo.getDouble("angx");
                                angle_y = (int) jo.getDouble("angy");
                                angle_z = 0;
                            }

                            out.write(String.valueOf(cmd));
                            out.flush();

                            Thread.sleep(500);

                            tcpIsConnected = true;
                            conn_status = 1;
                            alt = 1; // not set yet


                            if (cmd != Constants.CMD_FLY && System.currentTimeMillis() > lastCmdTimestamp + 1000) {
                                cmd = Constants.CMD_FLY; // ONLY SEND COMMANDS ONCE
                                lastCmdTimestamp = System.currentTimeMillis();
                                Log.d("CMD", String.valueOf(cmd));
                            }
                        } else {
                            conn_status = 0;
                            tcpIsConnected = false;
                            socket = new Socket(); // DO NOT REUSE SOCKET IF CONN FAILED!
                            socket.connect(new InetSocketAddress("192.168.1.1", portNumber), 2000);
                            try {
                                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            Log.d("TCP", "CONNECTED");
                        }
                    } catch (SocketTimeoutException sex) { // haha, sex
                        //sex.printStackTrace();
                        conn_status = -1;
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            Thread.sleep(2000);
                            conn_status = -1;
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }


            }
        };
        tcpThread.start();
    }

    private void updThread() {
        Thread udp = new Thread() {
            public void run() {

                while (true) { // FIXME
                    if (tcpIsConnected) {
                        try {
                            DatagramSocket s = new DatagramSocket();
                            InetAddress local = InetAddress.getByName("192.168.1.1");
                            // TODO IP:PORT SETTINGS
                            byte[] message;
                            DatagramPacket p;
                            while (sendData) {
                                if (useController) {
                                    messageStr = String.valueOf(Controller.roll + "" + Controller.pitch +
                                            Controller.yaw + Controller.throttle);
                                } else {

                                }
                                //Log.d("SENT:", messageStr);
                                message = messageStr.getBytes();
                                p = new DatagramPacket(message, messageStr.length(), local, portNumber);
                                s.send(p);
                                Thread.sleep(delayResendCmd);
                            }
                        } catch (Exception ex) {
                            Log.e("err", ex.getMessage());
                        }
                    } else {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Log.d("UPD", "WAITING FOR TCP");
                    }
                }
            }
        };
        udp.start();
    }

    private void updateConnStatus(int status) {
        conn_icon.setColor(status);

        switch (status) {
            case -1: {
                conn.setText("DISCONNECTED");
                break;
            }
            case 0: {
                conn.setText("CONNECTING...");
                break;
            }
            case 1: {
                conn.setText("CONNECTED");
                break;
            }
        }
    }

    private void updateUI() {
        Runnable runnable = new Runnable() {
            int deviceSignal = 0;
            @Override
            public void run() {
                if (!device.isWifiOn() || !device.getWifiSSID().equals(wifiSSID)) return;
                deviceSignal = device.getWifiSignalLevel();
                if (deviceSignal >= 75) {
                    wifi_icon.setColorFilter(getApplicationContext().getResources().getColor(R.color.FLAT_GREEN));
                }
                else if (deviceSignal >= 50) {
                    wifi_icon.setColorFilter(getApplicationContext().getResources().getColor(R.color.FLAT_YELLOW));
                }
                else if (deviceSignal >= 25) {
                    wifi_icon.setColorFilter(getApplicationContext().getResources().getColor(R.color.FLAT_ORANGE));
                }
                else if (deviceSignal >= 0) {
                    wifi_icon.setColorFilter(getApplicationContext().getResources().getColor(R.color.FLAT_RED));
                    vibrate();
                    speak(getString(R.string.WIFI_SIGNAL_LOW));
                }

                wifi.setText(device.getWifiSignalLevel() + "%");

                updateConnStatus(conn_status);
                //updateAltitude(alt);
                updateCompas(heading);
                updateAngles(angle_x, angle_y, angle_z);

                handler.postDelayed(this, 500);
            }
        };
        runnable.run();
    }

    private void updateBatteryStatus() {

        BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int percent = -1;
                if (rawLevel >= 0 && scale > 0) {
                    percent = (rawLevel * 100) / scale;
                }
                battery.setText(percent + "%");

                if (percent >= 75) {
                    battery_icon.setColorFilter(getApplicationContext().getResources().getColor(R.color.FLAT_GREEN));
                } else if (percent >= 50) {
                    battery_icon.setColorFilter(getApplicationContext().getResources().getColor(R.color.FLAT_YELLOW));
                } else if (percent >= 25) {
                    battery_icon.setColorFilter(getApplicationContext().getResources().getColor(R.color.FLAT_ORANGE));
                } else if (percent >= 0) {
                    battery_icon.setColorFilter(getApplicationContext().getResources().getColor(R.color.FLAT_RED));
                    vibrate();
                    speak(getString(R.string.WARNING_BATTERY_LOW));
                }
            }
        };
        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelReceiver, batteryLevelFilter);


    }

    private void updateCompas(int heading) {
        compas.setText(heading + "º");
        compas_icon.setRotation(heading);
    }

    private void updateAltitude(float alt) {
        if (true) {// TODO use meters vs feet
            altitude.setText(String.format("%.1f", alt));
        }
    }

    private void updateAngles(int x, int y, int z){
        angX.setText("X : " + x);
        angY.setText("Y : " + y);
        angZ.setText("Z : " + z);
    }

    private void handleJoysticks() {
        jsLayoutLeft = (FrameLayout) findViewById(R.id.js_layout_left);
        jsLeft = new JoyStick(getApplicationContext(), jsLayoutLeft, R.drawable.image_button);
        jsLeft.setDefaults();
        jsLayoutLeft.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                jsLeft.drawStick(event);
                if (event.getAction() == MotionEvent.ACTION_DOWN
                        || event.getAction() == MotionEvent.ACTION_MOVE) {

                    Log.d("X : ", String.valueOf(jsLeft.getX()));
                    Log.d("Y : ", String.valueOf(jsLeft.getY()));
                    Log.d("Angle : ", String.valueOf(jsLeft.getAngle()));
                    Log.d("Distance : ", String.valueOf(jsLeft.getDistance()));

                    switch (jsLeft.get8Direction()) {
                        case JoyStick.STICK_UP: {
                            Log.d("JS LEFT", "Direction : Up");
                            break;
                        }
                        case JoyStick.STICK_UP_RIGHT: {
                            Log.d("JS LEFT", "Direction : Up Right");
                            break;
                        }
                        case JoyStick.STICK_RIGHT: {
                            Log.d("JS LEFT", "Direction : Right");
                            break;
                        }
                        case JoyStick.STICK_DOWN_RIGHT: {
                            Log.d("JS LEFT", "Direction : Down Right");
                            break;
                        }
                        case JoyStick.STICK_DOWN: {
                            Log.d("JS LEFT", "Direction : Down");
                            break;
                        }
                        case JoyStick.STICK_DOWN_LEFT: {
                            Log.d("JS LEFT", "Direction : Down Left");
                            break;
                        }
                        case JoyStick.STICK_LEFT: {
                            Log.d("JS LEFT", "Direction : Left");
                            break;
                        }
                        case JoyStick.STICK_UP_LEFT: {
                            Log.d("JS LEFT", "Direction : Up Left");
                            break;
                        }
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    Log.d("JS LEFT", "ACTION UP");
                }
                return true;
            }
        });

        jsLayoutRight = (FrameLayout) findViewById(R.id.js_layout_right);
        jsRight = new JoyStick(getApplicationContext(), jsLayoutRight, R.drawable.image_button);
        jsRight.setDefaults();
        jsLayoutRight.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                jsRight.drawStick(event);
                if (event.getAction() == MotionEvent.ACTION_DOWN
                        || event.getAction() == MotionEvent.ACTION_MOVE) {

                    Log.d("X : ", String.valueOf(jsRight.getX()));
                    Log.d("Y : ", String.valueOf(jsRight.getY()));
                    Log.d("Angle : ", String.valueOf(jsRight.getAngle()));
                    Log.d("Distance : ", String.valueOf(jsRight.getDistance()));

                    switch (jsRight.get8Direction()) {
                        case JoyStick.STICK_UP: {
                            Log.d("JS RIGHT", "Direction : Up");
                            break;
                        }
                        case JoyStick.STICK_UP_RIGHT: {
                            Log.d("JS RIGHT", "Direction : Up Right");
                            break;
                        }
                        case JoyStick.STICK_RIGHT: {
                            Log.d("JS RIGHT", "Direction : Right");
                            break;
                        }
                        case JoyStick.STICK_DOWN_RIGHT: {
                            Log.d("JS RIGHT", "Direction : Down Right");
                            break;
                        }
                        case JoyStick.STICK_DOWN: {
                            Log.d("JS RIGHT", "Direction : Down");
                            break;
                        }
                        case JoyStick.STICK_DOWN_LEFT: {
                            Log.d("JS RIGHT", "Direction : Down Left");
                            break;
                        }
                        case JoyStick.STICK_LEFT: {
                            Log.d("JS RIGHT", "Direction : Left");
                            break;
                        }
                        case JoyStick.STICK_UP_LEFT: {
                            Log.d("JS RIGHT", "Direction : Up Left");
                            break;
                        }
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    Log.d("JS RIGHT", "ACTION UP");
                }
                return true;
            }
        });
    }

    private void openWebView() {
        // TODO If is webRTC start() then pause() onPause()
        if (device.isInternetAvailable() && device.networkIsWifi()
                && device.isWifiOn() && device.getWifiSSID().equals(wifiSSID)) {

            webView.setVisibility(View.VISIBLE);
            webView.loadUrl("http://192.168.1.1:9090/stream"); // /stream/webrtc
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    injectCSS();
                    super.onPageFinished(view, url);
                }
            });
        }
        else {
            webView.setVisibility(View.GONE);
        }
    }

    private void initAll() {
        //fullscreen flags
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //screen stay on flag
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //preferences
        PreferenceManager.setDefaultValues(getApplicationContext(), R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(listener);
        //api level
        apiLevel = android.os.Build.VERSION.SDK_INT;
        //inflate main activity
        setContentView(R.layout.activity_main);

        device = Device.getInstance(getApplicationContext());
        handler = new Handler();
        v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        // Header menu init
        // Connection Status
        conn_icon = (CircleView) findViewById(R.id.conn_icon);
        conn = (TextView) findViewById(R.id.conn);

        // Battery status
        battery_icon = (ImageView) findViewById(R.id.battery_icon);
        battery = (TextView) findViewById(R.id.battery);

        // Wifi signal
        wifi_icon = (ImageView) findViewById(R.id.wifi_icon);
        wifi = (TextView) findViewById(R.id.wifi);

        // Compas / Heading
        compas_icon = (ImageView) findViewById(R.id.compas_icon);
        compas = (TextView) findViewById(R.id.compas);

        // Altitude
        altitude_icon = (ImageView) findViewById(R.id.altitude_icon);
        altitude = (TextView) findViewById(R.id.altitude);

        // Angles / Inclination
        angX = (TextView) findViewById(R.id.angx);
        angY = (TextView) findViewById(R.id.angy);
        angZ = (TextView) findViewById(R.id.angz);

        // Joysticks wrapper
        jsLayout = (LinearLayout) findViewById(R.id.jsLayout);

        // WebView stream
        webView = (WebView) findViewById(R.id.webView);

        //set default settings
        setSettings();

        //menu button asignment and onclick listener
        menuBtn = (ImageButton) findViewById(R.id.menuBtn);
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPopup(v);
                speak("menu opened");
            }
        });

        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.ENGLISH);
                }
            }
        });
    }

}
