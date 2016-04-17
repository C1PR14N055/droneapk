package ro.drone.ciprian.droneapp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.InputDevice;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MainActivity extends AppCompatActivity {

    VerticalSeekBar throttle;
    VerticalSeekBar pitch;
    SeekBar roll;
    SeekBar yaw;

    final String localPiIP = "192.168.1.1";
    final int SERVER_PORT = 12345;
    String messageStr;
    boolean sendData = true;

    final int CMD_ARM = 0;
    final int CMD_FLY = 1;
    final int CMD_DISARM = 2;
    final int CMD_SHUTDOWN = 3;
    int cmd = 1; // command to send
    long lastCmdTimestamp = 0;

    boolean useController = true;

    Device device;
    ProgressBar signal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        final Button menuBtn = (Button) findViewById(R.id.menuBtn);
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popup = new PopupMenu(MainActivity.this, menuBtn);
                popup.getMenuInflater().inflate(R.menu.menu, popup.getMenu());

                //registering popup with OnMenuItemClickListener
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(final MenuItem item) {
                        new AlertDialog.Builder(MainActivity.this)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle("100%?")
                            .setMessage("Are you sure?")
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    switch (item.getItemId()) {
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
        });

        //gameControllerDevicesIds = Controller.getGameControllerIds();
        //Log.d("Controller IDs:", String.valueOf(gameControllerDevicesIds));

        device = Device.getInstance(this);
        signal = (ProgressBar) findViewById(R.id.signal);
        final Handler mHandler = new Handler();
        final Vibrator v = (Vibrator) this.getSystemService(this.VIBRATOR_SERVICE);
        // Vibrate for 500 milliseconds

        Runnable runnable = new Runnable() {
            int deviceSignal = 0;
            @Override
            public void run() {
                if (!device.isWifiOn() || !device.getWifiSSID().equals("XDRONE")) return;
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
                    v.vibrate(100);
                }

                signal.setProgress(device.getWifiSignalLevel());
                Log.d("SIGNAL:", String.valueOf(device.getWifiSignalLevel()));
                mHandler.postDelayed(this, 2000);
            }
        };
        //mHandler.post(runnable);
        if (!device.isWifiOn()) {
            Toast toast = Toast.makeText(this, "Wifi is OFF!!", Toast.LENGTH_SHORT);
            toast.show();
        } else if (!device.getWifiSSID().equals("XDRONE")) {
            Toast toast = Toast.makeText(this, "Please connect to XDRONE wifi!!", Toast.LENGTH_SHORT);
            toast.show();
        }
        runnable.run();

        // UDP SEND THREAD
        new Thread(new Runnable() {
            public void run() {
                try {
                    DatagramSocket s = new DatagramSocket();
                    // Raspberry Pi Hotspot Address
                    InetAddress local = InetAddress.getByName(localPiIP);
                    byte[] message;
                    DatagramPacket p;
                    while (sendData) {
                        if (useController) {
                            messageStr = String.valueOf(Controller.roll + "" + Controller.pitch +
                                        Controller.yaw + Controller.throttle  + cmd);
                        } else {
                            messageStr = String.valueOf((roll.getProgress() + 1000) + "" + (pitch.getProgress() + 1000) +
                                    (yaw.getProgress() + 1000) + (throttle.getProgress() + 1000) + cmd);
                        }
                        //Log.d("SENT:", messageStr);
                        message = messageStr.getBytes();
                        p = new DatagramPacket(message, messageStr.length(), local, SERVER_PORT);
                        s.send(p);
                        Thread.sleep(10);
//                        if (cmd != CMD_FLY && System.currentTimeMillis() - lastCmdTimestamp > 500) {
//                            cmd = CMD_FLY; // ONLY SEND COMMANDS ONCE
//                            lastCmdTimestamp = System.currentTimeMillis();
//                            Log.d("PASS", String.valueOf(cmd));
//                        }
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


    public void showPopup(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.menu, popup.getMenu());
        popup.show();
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

}
