package com.hahn.googlenowaddon;

import com.hahn.googlenowaddon.speech.SpeechRecognitionService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PowerStateReceiver extends BroadcastReceiver {
    public static final String START_CHARING = "startCharging";
    public static final String STOP_CHARING = "stopCharging";
    
    @Override
    public void onReceive(Context context, Intent intent) { 
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
            Intent service = new Intent(context, SpeechRecognitionService.class);
            service.putExtra(START_CHARING, true);
            context.startService(service);
        } else if(action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            Intent service = new Intent(context, SpeechRecognitionService.class);
            service.putExtra(STOP_CHARING, true);
            context.startService(service);
        }
    }
}
