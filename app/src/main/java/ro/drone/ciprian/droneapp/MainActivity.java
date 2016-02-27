package ro.drone.ciprian.droneapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.SeekBar;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MainActivity extends AppCompatActivity {

    VerticalSeekBar throttle;
    VerticalSeekBar pitch;
    SeekBar roll;
    SeekBar yaw;

    int SERVER_PORT = 12345;
    String messageStr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        DatagramSocket s = new DatagramSocket();
                        InetAddress local = InetAddress.getByName("192.168.1.143");
                        messageStr = String.valueOf((roll.getProgress() + 1000) + "" + (pitch.getProgress() + 1000) +
                                (yaw.getProgress() + 1000) + (throttle.getProgress() + 1000));
                        byte[] message = messageStr.getBytes();
                        DatagramPacket p = new DatagramPacket(message, messageStr.length(), local, SERVER_PORT);
                        s.send(p);
                        SystemClock.sleep(30);

                    } catch (Exception ex) {
                        Log.e("err",ex.getMessage());
                    }

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

}
