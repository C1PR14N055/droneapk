package ro.drone.ciprian.droneapp;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

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
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // API level
    int apiLevel;

    // networking stuff
    final String localPiIP = "192.168.1.1";
    //BufferedReader in = null;
    //PrintWriter out = null;
    String messageStr;
    boolean sendData = true;

    // Flight commands
    int cmd = 1; // command to send
    long lastCmdTimestamp = 0;
    static final int delayResendCmd = 33;
    static final int delayUpdateThrottle = 33;

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
    // TODO measure units, feet / meters
    ImageView altitude_icon;
    TextView altitude;

    // Angles / inclination
    TextView angX;
    TextView angY;
    TextView angZ;


    ImageButton menuBtn;

    //WebView
    WebView webView;

    //JoyStick
    FrameLayout jsLayoutLeft;
    FrameLayout jsLayoutRight;
    JoyStick jsLeft;
    JoyStick jsRight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        //set default settings
        setSettings();
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


        // WebView stream
        webView = (WebView) findViewById(R.id.webView);

        // TODO If is webRTC start() then pause() onPause()
        if (device.isInternetAvailable() && device.networkIsWifi()
                && device.isWifiOn() && device.getWifiSSID().equals(wifiSSID)) {
            webView.loadUrl("http://192.168.1.1:9090/stream"); // /stream/webrtc
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    //injectCSS();
                    super.onPageFinished(view, url);
                }
            });
        }
        else {
            webView.setVisibility(View.GONE);
        }

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

        Runnable runnable = new Runnable() {
            int deviceSignal = 0;
            @Override
            public void run() {
                if (!device.isWifiOn() || !device.getWifiSSID().equals(wifiSSID)) return;
                deviceSignal = device.getWifiSignalLevel();
                if (deviceSignal >= 75) {
                    wifi_icon.setColorFilter(Color.parseColor(Constants.COLOR_GREEN));
                }
                else if (deviceSignal >= 50) {
                    wifi_icon.setColorFilter(Color.parseColor(Constants.COLOR_ORANGE));
                }
                else if (deviceSignal >= 25) {
                    wifi_icon.setColorFilter(Color.parseColor(Constants.COLOR_RED));
                }
                else if (deviceSignal >= 0) {
                    wifi_icon.setColorFilter(Color.parseColor(Constants.COLOR_RED));
                    wifi_icon.setImageResource(R.drawable.ic_signal_wifi_off_white_48dp);
                    vibrate();
                    speak(getString(R.string.WIFI_SIGNAL_LOW));
                }

                wifi.setText(device.getWifiSignalLevel());
                handler.postDelayed(this, 500);
            }
        };
        runnable.run();

        // UDP THREAD
        Thread runableUDP = new Thread() {
            public void run() {

                try {
                    InetAddress serverAddress = InetAddress.getByName("10.0.3.2");
                    Socket socket = new Socket(serverAddress, 21);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));

                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }


                /*
                try {
                    DatagramSocket s = new DatagramSocket();
                    InetAddress local = InetAddress.getByName(localPiIP);
                    // Raspberry Pi Hotspot Address
                    //InetAddress serverAddress = InetAddress.getByName(localPiIP);
                    //Socket socket = new Socket(serverAddress, SERVER_PORT);
                    //in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    //out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
                    //Log.d("TEST", String.valueOf(socket.getInputStream()));
                    // TODO IP:PORT SETTINGS
                    byte[] message;
                    DatagramPacket p;
                    while (sendData) {
                        if (useController) {
                            messageStr = String.valueOf(Controller.roll + "" + Controller.pitch +
                                        Controller.yaw + Controller.throttle  + cmd);
                        } else {

                        }
                        //Log.d("SENT:", messageStr);
                        message = messageStr.getBytes();
                        p = new DatagramPacket(message, messageStr.length(), local, portNumber);
                        s.send(p);
                        //out.write(messageStr);
                        //out.flush();
                        //s.send(p);
                        Thread.sleep(delayResendCmd);
                        if (cmd != Constants.CMD_FLY && System.currentTimeMillis() - lastCmdTimestamp > 500) {
                            //cmd = CMD_FLY; // ONLY SEND COMMANDS ONCE
                            //lastCmdTimestamp = System.currentTimeMillis();
                            Log.d("PASS", String.valueOf(cmd));
                        }
                    }
                } catch (Exception ex) {
                    Log.e("err", ex.getMessage());
                }
                */
            }
        };

        runableUDP.start();
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
                                    case R.id.shutdown: {
                                        cmd = Constants.CMD_SHUTDOWN;
                                        Toast.makeText(MainActivity.this, "Shutting Down Pi", Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                    case R.id.exit: {
                                        System.exit(0);
                                    }
                                }
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
}
