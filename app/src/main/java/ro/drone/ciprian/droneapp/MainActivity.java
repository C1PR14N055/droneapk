package ro.drone.ciprian.droneapp;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
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
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.VideoView;

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
import java.net.SocketAddress;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback{

    // API level
    int apiLevel;

    // On screen controls
    VerticalSeekBar throttle;
    VerticalSeekBar pitch;
    SeekBar roll;
    SeekBar yaw;

    // networking stuff
    final String localPiIP = "192.168.1.1";
    final int SERVER_PORT = 12345;
    BufferedReader in = null;
    PrintWriter out = null;
    String messageStr;
    boolean sendData = true;

    // commands
    final int CMD_ARM = 0;
    final int CMD_FLY = 1;
    final int CMD_DISARM = 2;
    final int CMD_SHUTDOWN = 3;
    int cmd = 1; // command to send
    long lastCmdTimestamp = 0;
    int delayResendCmd = 33;

    // Device singleton instance
    Device device;

    //UI post handler
    Handler handler;

    //Vibrator
    Vibrator v;

    // wifi progress bar
    ProgressBar signal;

    // Settings
    Button menuBtn;
    SharedPreferences prefs;
    String wifiSSID;
    boolean useController;
    boolean enableVibration;
    boolean enableSound;
    boolean overclockWIFI;
    boolean safeAutoLand;

    //TTS
    TextToSpeech tts;
    final String WARNING_SIGNAL_LOST = "warning, wifi signal lost";
    final String WARNING_SIGNAL_LOW = "warning, wifi signal low";
    final String ENGAGING_AUTOLAND = "engaging autonomous landing";
    final String WIFI_OFFLINE = "wifi offline";

    //Stream methods 0 = MediaPlayer & SurfaceView, 1 = VideoView, 2 = Native Video Player
    final int STREAM_USING = 3;

    //MediaPlayer on surfaceView
    String streamPath = "rtsp://10.0.2.2:8554/test.3gp";//"rtsp://media.smart-streaming.com/mytest/mp4:sample_phone_150k.mp4";//;"rtp://239.255.0.1:5004/";
    Uri streamUri;
    private MediaPlayer mediaPlayer;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    //VideoView
    VideoView videoView;

    //WebView
    WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //fullscreen flags
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //screen stay on flag
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(listener);
        //api level
        apiLevel = android.os.Build.VERSION.SDK_INT;
        //set default settings
        setSettings();
        //inflate main activity
        setContentView(R.layout.activity_main);

        //MediaPlayer
        switch (STREAM_USING) {
            case 0: {
                //surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
                surfaceHolder = surfaceView.getHolder();
                surfaceHolder.setFixedSize(800, 480);
                surfaceHolder.addCallback(this);
                surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                mediaPlayer = new MediaPlayer();

                mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                            mediaPlayer.release();
                            mediaPlayer = new MediaPlayer();
                            //mediaPlayer.stop();
                            play();
                        }
                        return false;
                    }
                });

                mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                    @Override
                    public void onBufferingUpdate(MediaPlayer mp, int percent) {
                        Toast.makeText(getApplicationContext(), "BUFF : " + percent, Toast.LENGTH_SHORT).show();
                        if (!mediaPlayer.isPlaying()) {
                            mediaPlayer.start();
                        }
                    }
                });

                //Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                //i.setType("video/*");
                //startActivityForResult(i, 1234);
                streamUri = Uri.parse(streamPath);
                play();
                break;
            }
            case 1: {
                //videoView = (VideoView) findViewById(R.id.videoView);
                videoView.setVideoURI(Uri.parse(streamPath));
                MediaController mediaController = new MediaController(this);
                mediaController.setAnchorView(videoView);
                videoView.setMediaController(mediaController);
                videoView.requestFocus();
                try {
                    videoView.start();
                }
                catch (SecurityException se) {
                    Log.e("SE", se.getMessage());
                    se.printStackTrace();
                }
                break;
            }
            case 2: {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(streamPath));
                startActivity(intent);
                break;
            }
            case 3: {
                webView = (WebView) findViewById(R.id.webView);
                webView.loadUrl("http://192.168.1.1:9090/stream/"); // /stream/webrtc
                webView.getSettings().setJavaScriptEnabled(true);
                final String webViewJs = "(function() { " +
                        "var html = document.getElementsByTagName('*'); for (el in html)" +
                        "if (html.hasOwnProperty(el)) {" +
                        "    if(html[el].id != 'remote-video') {" +
                        "        html[el].remove();" +
                        "    }" +
                        "}})();";
                webView.setWebViewClient(new WebViewClient(){
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                            //webView.evaluateJavascript(webViewJs, null);
                            webView.evaluateJavascript("start();", null);
                            webView.evaluateJavascript("fullscreen();", null);
                        } else {
                            //webView.loadUrl("javascript:" + webViewJs);
                        }
                        injectCSS();
                        super.onPageFinished(view, url);
                    }
                });
                break;
            }
            default: {
                //Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(streamPath));
                //startActivity(intent);
            }
        }


        //menu button asignment and onclick listener
        menuBtn = (Button) findViewById(R.id.menuBtn);
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPopup(v);
                //speak("menu opened");
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

        //gameControllerDevicesIds = Controller.getGameControllerIds();
        //Log.d("Controller IDs:", String.valueOf(gameControllerDevicesIds));

        device = Device.getInstance(this);
        signal = (ProgressBar) findViewById(R.id.signal);
        handler = new Handler();
        v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        Runnable runnable = new Runnable() {
            int deviceSignal = 0;
            @Override
            public void run() {
                if (!device.isWifiOn() || !device.getWifiSSID().equals(wifiSSID)) return;
                deviceSignal = device.getWifiSignalLevel();
                if (deviceSignal >= 75) {
                    signal.getProgressDrawable().setColorFilter(
                            Color.rgb(46, 204, 113), android.graphics.PorterDuff.Mode.SRC_IN);
                }
                else if (deviceSignal >= 50) {
                    signal.getProgressDrawable().setColorFilter(
                            Color.rgb(241, 196, 15), android.graphics.PorterDuff.Mode.SRC_IN);
                }
                else if (deviceSignal >= 25) {
                    signal.getProgressDrawable().setColorFilter(
                            Color.rgb(230, 126, 34), android.graphics.PorterDuff.Mode.SRC_IN);
                }
                else if (deviceSignal >= 0) {
                    signal.getProgressDrawable().setColorFilter(
                            Color.rgb(231, 76, 60), android.graphics.PorterDuff.Mode.SRC_IN);
                    vibrate();
                    speak(ENGAGING_AUTOLAND);
                }

                signal.setProgress(device.getWifiSignalLevel());
                Log.d("SIGNAL:", String.valueOf(device.getWifiSignalLevel()));
                handler.postDelayed(this, 1000);
            }
        };
        //handler.post(runnable);
        if (!device.isWifiOn()) {
            Toast.makeText(this, "Wifi is OFF!!", Toast.LENGTH_SHORT).show();
            speak(WIFI_OFFLINE);
        } else if (!device.getWifiSSID().equals(wifiSSID)) {
            Toast.makeText(this, "Please connect to " + wifiSSID + " wifi!!", Toast.LENGTH_SHORT).show();
        }
        runnable.run();

        // TCP CLIENT THREAD
        new Thread(new Runnable() {
            public void run() {
                try {
                    // Raspberry Pi Hotspot Address
                    InetAddress serverAddress = InetAddress.getByName(localPiIP);
                    Socket socket = new Socket(serverAddress, SERVER_PORT);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
                    Log.d("TEST", String.valueOf(socket.getInputStream()));
                    // TODO IP:PORT SETTINGS
                    while (sendData) {
                        if (useController) {
                            messageStr = String.valueOf(Controller.roll + "" + Controller.pitch +
                                        Controller.yaw + Controller.throttle  + cmd);
                        } else {
                            messageStr = String.valueOf((roll.getProgress() + 1000) + "" + (pitch.getProgress() + 1000) +
                                    (yaw.getProgress() + 1000) + (throttle.getProgress() + 1000) + cmd);
                        }
                        //Log.d("SENT:", messageStr);
                        out.write(messageStr);
                        out.flush();
                        //s.send(p);
                        Thread.sleep(delayResendCmd);
                        if (cmd != CMD_FLY && System.currentTimeMillis() - lastCmdTimestamp > 500) {
                            cmd = CMD_FLY; // ONLY SEND COMMANDS ONCE
                            lastCmdTimestamp = System.currentTimeMillis();
                            Log.d("PASS", String.valueOf(cmd));
                        }
                    }
                } catch (Exception ex) {
                    Log.e("err", ex.getMessage());
                }
            }
        }).start();

        throttle = (VerticalSeekBar) findViewById(R.id.throttleVerticalSeekBar);
        throttle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d("throttle", String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        pitch = (VerticalSeekBar) findViewById(R.id.pitchVerticalSeekBar);
        pitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d("pitch", String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                pitch.setProgressAndThumb(500);
            }
        });

        roll = (SeekBar) findViewById(R.id.rollSeekBar);
        roll.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d("roll", String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(seekBar.getMax() / 2);
            }
        });

        yaw = (SeekBar) findViewById(R.id.yawSeekBar);
        yaw.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d("yaw", String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(seekBar.getMax() / 2);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        if (webView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.evaluateJavascript("stop();", null);
            }
            else {
                webView.loadUrl("javascript:stop();");
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.evaluateJavascript("stop();", null);
            }
            else {
                webView.loadUrl("javascript:stop();");
            }
        }
    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();
        Toast.makeText(MainActivity.this, "NO", Toast.LENGTH_SHORT).show();
        //TODO options for this, with alert, no exiting, exiting
    }

    public void setSettings() {
        wifiSSID = prefs.getString("wifiSSID", "XDRONE");
        useController = prefs.getBoolean("useController", false);
        enableVibration = prefs.getBoolean("enableVibration", true);
        enableSound = prefs.getBoolean("enableSound", true);
        overclockWIFI = prefs.getBoolean("overclockWIFI", false);
        safeAutoLand = prefs.getBoolean("safeAutoLand", true);
    }

    SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            setSettings();
            //Toast.makeText(MainActivity.this, String.valueOf(useController), Toast.LENGTH_SHORT).show();
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
                                        cmd = CMD_ARM;
                                        Toast.makeText(MainActivity.this, "Board Armed", Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                    case R.id.disarm: {
                                        cmd = CMD_DISARM;
                                        Toast.makeText(MainActivity.this, "Board Disarmed", Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                    case R.id.shutdown: {
                                        cmd = CMD_SHUTDOWN;
                                        Toast.makeText(MainActivity.this, "Shutting Down Pi", Toast.LENGTH_SHORT).show();
                                        break;
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
        if (!lastWarning.equals(text) || System.currentTimeMillis() - lastWarningTimeStamp > 7000) {
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
        //TODO vibrate intensity
    }

//    @Override
//    public boolean dispatchKeyEvent(KeyEvent event) {
//        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
//            Log.d("INPUT KEYCODE", String.valueOf(event.getKeyCode()) + " FROM: " + event.getDeviceId());
//            return true; // if handled
//        }
//        return super.dispatchKeyEvent(event);
//    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        Log.d("Motion event:", event.toString());
        // Check that the event came from a game controller
        //if (event.getAxisValue(MotionEvent.AXIS_RX) > -1) Log.d("Motion", event.getAxisValue(MotionEvent.AXIS_RX) + "");
        //if (event.getAxisValue(MotionEvent.AXIS_RY) > -1) Log.d("Motion", event.getAxisValue(MotionEvent.AXIS_RY) + "");
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1234) {
            if (resultCode == Activity.RESULT_OK) {
                streamUri = data.getData();
                play();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mediaPlayer != null) {
            mediaPlayer.setDisplay(surfaceHolder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    void play() {
        try {
            //final FileInputStream fis = new FileInputStream(streamPath);
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.setDataSource(getApplicationContext(), streamUri);
            //mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    //mediaPlayer.reset();
                    mediaPlayer.start();
                }
            });


        } catch (SecurityException se) {
            Log.e("SE", se.getMessage());
            se.printStackTrace();
        } catch (IOException ie) {
            ie.printStackTrace();
        }
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
