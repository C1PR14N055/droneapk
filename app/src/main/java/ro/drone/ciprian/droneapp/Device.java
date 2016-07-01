package ro.drone.ciprian.droneapp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

/**
 * Created by ciprian on 4/9/16.
 */
public class Device {

    private static WifiManager wifiManager = null;
    private static ConnectivityManager connectivityManager = null;
    NetworkInfo networkInfo;
    private static final int WIFI_SIGNAL_LEVELS = 100;
    private static Device device = null;

    private Device(Context context) {
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        networkInfo = connectivityManager.getActiveNetworkInfo();
    }

    public static synchronized Device getInstance(Context context){
        if (device == null) {
            device = new Device(context);
        }
        return device;
    }

    public boolean isWifiOn(){
        return wifiManager.isWifiEnabled();
    }

    public String getWifiSSID() {
        return wifiManager.getConnectionInfo().getSSID().replace("\"", "");
    }

    public boolean networkIsWifi() {
        return networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    public boolean isInternetAvailable() {
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }

    public int getWifiSignalLevel() {
        return wifiManager.calculateSignalLevel(wifiManager.getConnectionInfo().getRssi(), WIFI_SIGNAL_LEVELS);
    }

}
