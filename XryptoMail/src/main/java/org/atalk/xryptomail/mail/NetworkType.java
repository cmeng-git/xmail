package org.atalk.xryptomail.mail;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

/**
 * Enum for some of
 * https://developer.android.com/reference/android/net/ConnectivityManager.html#TYPE_MOBILE etc.
 */
public enum NetworkType {
    WIFI,
    MOBILE,
    OTHER;

    public static NetworkType fromConnectivityManagerType(int type){
        switch (type) {
            case NetworkCapabilities.TRANSPORT_CELLULAR:
                return MOBILE;
            case NetworkCapabilities.TRANSPORT_WIFI:
                return WIFI;
            default:
                return OTHER;
        }
    }
}
