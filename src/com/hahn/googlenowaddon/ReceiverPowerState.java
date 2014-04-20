package com.hahn.googlenowaddon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.hahn.googlenowaddon.util.Constants.SpeechRecognitionServiceActions;

public class ReceiverPowerState extends BroadcastReceiver {    
    @Override
    public void onReceive(Context context, Intent intent) { 
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
            Intent service = new Intent(context, ServiceSpeechRecognition.class);
            service.setAction(SpeechRecognitionServiceActions.START_CHARING);
            context.startService(service);
        } else if(action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            Intent service = new Intent(context, ServiceSpeechRecognition.class);
            service.setAction(SpeechRecognitionServiceActions.STOP_CHARGING);
            context.startService(service);
        }
    }
}
