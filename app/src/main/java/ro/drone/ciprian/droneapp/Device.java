package ro.drone.ciprian.droneapp;

import android.content.Context;
import android.net.wifi.WifiManager;

/**
 * Created by ciprian on 4/9/16.
 */
public class Device {

    private static WifiManager wifiManager = null;
    private static final int WIFI_SIGNAL_LEVELS = 100;

    public Device(Context context) {
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public static boolean isWifiOn(Context context){
        return wifiManager.isWifiEnabled();
    }

    public static String getWifiSSID(Context context) {
        return wifiManager.getConnectionInfo().getSSID();
    }

    public int getWifiSignalLevel() {
        return wifiManager.calculateSignalLevel(wifiManager.getConnectionInfo().getRssi(), WIFI_SIGNAL_LEVELS);
    }

}
