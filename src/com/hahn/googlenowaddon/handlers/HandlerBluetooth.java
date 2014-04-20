package com.hahn.googlenowaddon.handlers;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.widget.Toast;

import com.hahn.googlenowaddon.ServiceSpeechRecognition;

public class HandlerBluetooth {
    public static void handleStateChange(Context context, String newState) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;

        boolean enabled = adapter.isEnabled();
        
        String speakText = null;
        
        if ("Off".equals(newState)) {
            speakText = enabled ? "Turning Bluetooth off" : "Bluetooth already off";
            Toast.makeText(context, speakText, Toast.LENGTH_SHORT).show();
            
            if (enabled) adapter.disable();           
        } else if ("On".equals(newState)) {
            speakText = enabled ? "Bluetooth already on" : "Turning Bluetooth on";
            Toast.makeText(context, speakText, Toast.LENGTH_SHORT).show();
            
            if (!enabled) adapter.enable();
        } else if ("Toggle".equals(newState)) {
            speakText = String.format("Turning Bluetooth %s", enabled ? "off" : "on");
            Toast.makeText(context, speakText, Toast.LENGTH_SHORT).show();
            
            if (enabled)  adapter.disable();
            else adapter.enable();
        }

        ServiceSpeechRecognition.speak(context, speakText);
    }
}
