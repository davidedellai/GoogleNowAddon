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
            speakText = enabled ? "Turning Bluetooth off" : "Bluetooth already off";
            Toast.makeText(context, speakText, Toast.LENGTH_SHORT).show();
            
            if (enabled) adapter.disable();           
            
            break;
        case On:
            speakText = enabled ? "Bluetooth already on" : "Turning Bluetooth on";
            Toast.makeText(context, speakText, Toast.LENGTH_SHORT).show();
            
            if (!enabled) adapter.enable();

            break;
        case Toggle:
            speakText = String.format("Turning Bluetooth %s", enabled ? "off" : "on");
            Toast.makeText(context, speakText, Toast.LENGTH_SHORT).show();
            
            if (enabled)  adapter.disable();
            else adapter.enable();

            break;
        default:
            return;
        }

        GoogleSearchApi.speak(context, speakText);
    }
}
