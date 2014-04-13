package com.hahn.googlenowaddon.handlers;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.widget.Toast;

import com.hahn.googlenowaddon.GoogleSearchApi;
import com.hahn.googlenowaddon.handlers.Constants.Enum_Key;

public class BluetoothHandler {
    public static void handleStateChange(Context context, Enum_Key newState) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;

        boolean enabled = adapter.isEnabled();
        
        String speakText;
        
        switch (newState) {
        case Off:
            if (enabled) adapter.disable();
            
            speakText = enabled ? "Bluetooth off" : "Bluetooth already off";
            break;
        case On:
            if (!enabled) adapter.enable();

            speakText = enabled ? "Bluetooth already on" : "Bluetooth on";
            break;
        case Toggle:
            if (enabled)  adapter.disable();
            else adapter.enable();

            speakText = String.format("Bluetooth %s", enabled ? "off" : "on");
            break;
        default:
            return;
        }

        Toast.makeText(context, speakText, Toast.LENGTH_SHORT).show();
        GoogleSearchApi.speak(context, speakText);
    }
}
