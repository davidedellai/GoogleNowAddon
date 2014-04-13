package com.hahn.googlenowaddon.handlers;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.widget.Toast;

import com.hahn.googlenowaddon.GoogleSearchApi;
import com.hahn.googlenowaddon.handlers.Constants.Enum_Key;

public class WifiHandler {

    public static void handleStateChange(Context context, Enum_Key newState) {
        WifiManager mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (mWifiManager == null) return;

        boolean wifiEnabled = mWifiManager.isWifiEnabled();

        String speakText, visualText;

        switch (newState) {
        case Off:
            if (wifiEnabled) mWifiManager.setWifiEnabled(false);
            
            speakText = wifiEnabled ? "Why Fi off" : "Why Fi already off";
            visualText = wifiEnabled ? "Wi-Fi off" : "Wi-Fi already off";
            break;
        case On:
            if (!wifiEnabled) mWifiManager.setWifiEnabled(true);

            speakText = wifiEnabled ? "Why Fi already on" : "Why Fi on";
            visualText = wifiEnabled ? "Wi-Fi already on" : "Wi-Fi on";
            break;
        case Toggle:
            mWifiManager.setWifiEnabled(!wifiEnabled);
            
            visualText = String.format("Wi-Fi %s", wifiEnabled ? "off" : "on");
            speakText = String.format("Why Fi %s", wifiEnabled ? "off" : "on");
            break;
        default:
            return;
        }

        Toast.makeText(context, visualText, Toast.LENGTH_SHORT).show();
        GoogleSearchApi.speak(context, speakText);
    }
}