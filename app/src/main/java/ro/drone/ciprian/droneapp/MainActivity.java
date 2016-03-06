package ro.drone.ciprian.droneapp;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.SeekBar;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    VerticalSeekBar throttle;
    VerticalSeekBar pitch;
    SeekBar roll;
    SeekBar yaw;

    int SERVER_PORT = 12345;
    String messageStr;
    boolean sendStuff = true;

    ArrayList gameControllerDevicesIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        gameControllerDevicesIds = Controller.getGameControllerIds();

        new Thread(new Runnable() {
            public void run() {
                try {
                    DatagramSocket s = new DatagramSocket();
                    InetAddress local = InetAddress.getByName("192.168.1.87");
                    while (sendStuff) {
                        messageStr = String.valueOf((roll.getProgress() + 1000) + "" + (pitch.getProgress() + 1000) +
                                (yaw.getProgress() + 1000) + (throttle.getProgress() + 1000));
                        byte[] message = messageStr.getBytes();
                        DatagramPacket p = new DatagramPacket(message, messageStr.length(), local, SERVER_PORT);
                        s.send(p);
                        Thread.sleep(10);
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            Log.d("INPUT KEYCODE", String.valueOf(keyCode) + " FROM: " + event.getDeviceId());
            return true; // if handled
        }
        return super.onKeyDown(keyCode, event);
    }

}
