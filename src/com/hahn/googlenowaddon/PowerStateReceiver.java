package com.hahn.googlenowaddon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.hahn.googlenowaddon.Constants.SpeechRecognitionServiceExtras;

public class PowerStateReceiver extends BroadcastReceiver {    
    @Override
    public void onReceive(Context context, Intent intent) { 
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
            Intent service = new Intent(context, SpeechRecognitionService.class);
            service.putExtra(SpeechRecognitionServiceExtras.START_CHARING, true);
            context.startService(service);
        } else if(action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            Intent service = new Intent(context, SpeechRecognitionService.class);
            service.putExtra(SpeechRecognitionServiceExtras.STOP_CHARING, true);
            context.startService(service);
        }
    }
}
