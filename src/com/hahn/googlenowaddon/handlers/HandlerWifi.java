package com.hahn.googlenowaddon.handlers;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.widget.Toast;

import com.hahn.googlenowaddon.ServiceSpeechRecognition;

public class HandlerWifi {

    public static void handleStateChange(Context context, String newState) {
        WifiManager mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (mWifiManager == null) return;

        boolean wifiEnabled = mWifiManager.isWifiEnabled();

        String speakText = null,
               visualText = null;

        if ("Off".equals(newState)) {
            if (wifiEnabled) mWifiManager.setWifiEnabled(false);
            
            speakText = wifiEnabled ? "Why Fi off" : "Why Fi already off";
            visualText = wifiEnabled ? "Wi-Fi off" : "Wi-Fi already off";
        } else if ("On".equals(newState)) {
            if (!wifiEnabled) mWifiManager.setWifiEnabled(true);

            speakText = wifiEnabled ? "Why Fi already on" : "Why Fi on";
            visualText = wifiEnabled ? "Wi-Fi already on" : "Wi-Fi on";
        } else if ("Toggle".equals(newState)) {
            mWifiManager.setWifiEnabled(!wifiEnabled);
            
            visualText = String.format("Wi-Fi %s", wifiEnabled ? "off" : "on");
            speakText = String.format("Why Fi %s", wifiEnabled ? "off" : "on");
        }

        Toast.makeText(context, visualText, Toast.LENGTH_SHORT).show();
        ServiceSpeechRecognition.speak(context, speakText);
    }
}