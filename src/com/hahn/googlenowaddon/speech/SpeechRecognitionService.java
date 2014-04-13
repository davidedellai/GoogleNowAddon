package com.hahn.googlenowaddon.speech;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

public class SpeechRecognitionService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.e("Speech", "Create service");
        SpeechRecognizer sr = SpeechRecognizer.createSpeechRecognizer(this);
        SpeechRecognitionListener listener = new SpeechRecognitionListener(getApplicationContext());
        sr.setRecognitionListener(listener);
        
        Intent srIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        srIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        
        sr.startListening(srIntent);
    }
    
    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        super.onStartCommand(i, flags, startId);
        
        Log.e("Speech", "onStartCommand");
        
        return Service.START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        Log.e("Speech", "Bind");
        
        return null;
    }
}
