package ro.drone.ciprian.droneapp;

import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;

/**
 * Created by ciprian on 3/6/16.
 */
public class Controller {

    public static int roll = 1500;
    public static int pitch = 1500;
    public static int yaw = 1500;
    public static int throttle = 1000;

    final static int DPAD_UP       = 0;
    final static int DPAD_LEFT     = 1;
    final static int DPAD_RIGHT    = 2;
    final static int DPAD_DOWN     = 3;
    final static int DPAD_CENTER   = 4;

    static int directionPressed = -1; // initialized to -1

    public static int getDirectionPressed(InputEvent event) {
        if (!isDpadDevice(event)) {
            return -1;
        }

        // If the input event is a MotionEvent, check its hat axis values.
        if (event instanceof MotionEvent) {

            // Use the hat axis value to find the D-pad direction
            MotionEvent motionEvent = (MotionEvent) event;
            float xaxis = motionEvent.getAxisValue(MotionEvent.AXIS_HAT_X);
            float yaxis = motionEvent.getAxisValue(MotionEvent.AXIS_HAT_Y);

            // Check if the AXIS_HAT_X value is -1 or 1, and set the D-pad
            // LEFT and RIGHT direction accordingly.
            if (Float.compare(xaxis, -1.0f) == 0) {
                directionPressed =  DPAD_LEFT;
            } else if (Float.compare(xaxis, 1.0f) == 0) {
                directionPressed =  DPAD_RIGHT;
            }
            // Check if the AXIS_HAT_Y value is -1 or 1, and set the D-pad
            // UP and DOWN direction accordingly.
            else if (Float.compare(yaxis, -1.0f) == 0) {
                directionPressed =  DPAD_UP;
            } else if (Float.compare(yaxis, 1.0f) == 0) {
                directionPressed =  DPAD_DOWN;
            }
        }

        // If the input event is a KeyEvent, check its key code.
        else if (event instanceof KeyEvent) {

            // Use the key code to find the D-pad direction.
            KeyEvent keyEvent = (KeyEvent) event;
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                directionPressed = DPAD_LEFT;
            } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                directionPressed = DPAD_RIGHT;
            } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
                directionPressed = DPAD_UP;
            } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                directionPressed = DPAD_DOWN;
            } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER) {
                directionPressed = DPAD_CENTER;
            }
        }
        return directionPressed;
    }

    public static boolean isDpadDevice(InputEvent event) {
        // Check that input comes from a device with directional pads.
        return (event.getSource() & InputDevice.SOURCE_DPAD) != InputDevice.SOURCE_DPAD;
    }

    private static float getCenteredAxis(MotionEvent event,
                                         InputDevice device, int axis, int historyPos) {
        final InputDevice.MotionRange range =
                device.getMotionRange(axis, event.getSource());

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            final float flat = range.getFlat();
            final float value =
                    historyPos < 0 ? event.getAxisValue(axis):
                            event.getHistoricalAxisValue(axis, historyPos);

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }

    public static void processJoystickInput(MotionEvent event,
                                      int historyPos) {

        InputDevice mInputDevice = event.getDevice();

        // Left stick
        float x = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_X, historyPos);
        if (x == 0) { // x axis
            x = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_HAT_X, historyPos);
        }

        float y = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Y, historyPos);
        if (y == 0) { // y axis
            y = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_HAT_Y, historyPos);
        }

        // Right stick
        float z = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Z, historyPos);
        if (z == 0) { // x axis
            z = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_Z, historyPos);
        }

        float rz = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_RZ, historyPos);
        if (rz == 0) { // y axis
            rz = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_RZ, historyPos);
        }

        // Right trigger
        float rt = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_RY, historyPos);
        if (rt == 0) { // x axis
            rt = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_RY, historyPos);
        }

        // Left trigger
        float lt = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_RX, historyPos);
        if (lt == 0) { // x axis
            lt = getCenteredAxis(event, mInputDevice,
                    MotionEvent.AXIS_RX, historyPos);
        }

        /*
        if (x != 0) Log.d("X", String.valueOf(x));
        if (y != 0) Log.d("Y", String.valueOf(y));
        if (z != 0)  Log.d("Z", String.valueOf(z));
        if (rz != 0) Log.d("RZ", String.valueOf(rz));
        if (rt != 0) Log.d("RT", String.valueOf(rt));
        if (lt != 0) Log.d("LT", String.valueOf(lt));
        */

        roll = convertToRCdata(z, false);
        pitch = convertToRCdata(rz, true);
        yaw = convertToRCdata(x, false);
        throttle = convertToRCdata(rt, false);

        Log.d("RPYT", String.valueOf(roll) + String.valueOf(pitch) +
                String.valueOf(yaw) + String.valueOf(throttle));
    }

    public static ArrayList getGameControllerIds() {
        ArrayList gameControllerDeviceIds = new ArrayList();
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int deviceId : deviceIds) {
            Log.d("DEVICE IDS:", String.valueOf(deviceId));
            InputDevice dev = InputDevice.getDevice(deviceId);
            int sources = dev.getSources();

            // Verify that the device has gamepad buttons, control sticks, or both.
            if (((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                    || ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) {
                // This device is a game controller. Store its device ID.
                if (!gameControllerDeviceIds.contains(deviceId)) {
                    gameControllerDeviceIds.add(deviceId);
                }
            }
        }
        return gameControllerDeviceIds;
    }

    private static int convertToRCdata(float x, boolean invert) {
        if (invert) x *= -1;
        return Math.round(x * 500 + 1500); // Thanks buby
    }

}
