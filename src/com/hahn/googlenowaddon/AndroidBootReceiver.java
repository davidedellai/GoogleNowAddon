package com.hahn.googlenowaddon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


public class AndroidBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            context.startService(new Intent(context, SpeechRecognitionService.class));
        }
    }
}
