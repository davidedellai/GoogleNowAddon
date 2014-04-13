package com.hahn.googlenowaddon.speech;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import com.hahn.googlenowaddon.Util;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

public class SpeechRecognitionService extends Service {
    public static final int SERVICE_ID = 1524;
    public static final String KEY_PHRASE = "google";
    
    public static final int MUTE_STREAM = AudioManager.STREAM_MUSIC;
    
    private WakeLock wakelock;
    private boolean isCharging;
    private int batteryCheckDelay;
    
    private Timer timeoutTimer;
    
    /** A handler linked to the main thread */
    private Handler mainThreadHandler;
    
    private SpeechRecognizer sr;
    private AudioManager audioManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.e("Speech", "Create service");
        
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE); 
        
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SpeechRecognitionWakeLock");
        wakelock.acquire();
        
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new SpeechRecognitionListener());
        
        Notification note = new Notification.Builder(getApplicationContext())
                .setContentTitle("Google Now Launcher")
                .setPriority(Notification.PRIORITY_MIN)
                .build();
        
        this.startForeground(SERVICE_ID, note);
        
        this.mainThreadHandler = new Handler();
        
        startListening();
    }
    
    @SuppressLint("Wakelock")
	protected void startListening() {
        Log.e("Speech", "Start listening");
        
        cancelTimeout();
        
        if (!isCharging || --batteryCheckDelay < 0) {
            Log.e("Speech", "Check battery");
            
            isCharging = Util.isCharging(getApplicationContext());
            if (isCharging) {
                batteryCheckDelay = 3;
                
                wakelock.acquire();
                do_startListening();
            } else {
                Log.e("Speech", "Wait for battery");
                
                wakelock.release();
                timeoutTimer(10000);
            }
        } else {
            do_startListening();
        }
    }
    
    private void do_startListening() {        
        ActivityManager am = (ActivityManager) SpeechRecognitionService.this.getSystemService(ACTIVITY_SERVICE);
        RunningTaskInfo info = am.getRunningTasks(1).get(0);
        
        if (!info.baseActivity.getPackageName().equals("com.google.android.googlequicksearchbox")) {
            timeoutTimer(5000);
            
            audioManager.setStreamMute(MUTE_STREAM, true);
            
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            
            sr.startListening(intent);
        } else {
            Log.e("Speech", "Waiting to close google now");
            
            timeoutTimer(2000);
        }
    }
    
    protected void cancelTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }
    
    protected void timeoutTimer(int delay) {
        cancelTimeout();
        
        timeoutTimer = new Timer();
        timeoutTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                restartListening();
            }
        }, delay);
    }
    
    protected void restartListening() {
        // Associate with main thread
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                sr.cancel();
                startListening();
            }
            
        });
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
    
    @Override
    public void onDestroy() {
        Log.e("Speech", "Destroy");
        
        if (sr != null) {
            sr.destroy();
        }
        
        if (wakelock != null) {
            wakelock.release();
        }
    }
    
    protected class SpeechRecognitionListener implements RecognitionListener {        
        @Override
        public void onBeginningOfSpeech() {
            Log.d("Speech", "onBeginningOfSpeech");
            
            cancelTimeout();
        }
    
        @Override
        public void onBufferReceived(byte[] buffer) { }
    
        @Override
        public void onEndOfSpeech() {
            Log.d("Speech", "onEndOfSpeech");
        }
    
        @Override
        public void onError(int error) {
            Log.d("Speech", "onError " + error);
            
            /*
            if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                Toast.makeText(getApplicationContext(), "Sorry, I didn't catch that.", Toast.LENGTH_SHORT).show();
            }
            */
            
            restartListening();
        }
    
        @Override
        public void onEvent(int eventType, Bundle params) { }
    
        @Override
        public void onPartialResults(Bundle partialResults) { }
    
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d("Speech", "onReadyForSpeech");
            
            audioManager.setStreamMute(MUTE_STREAM, false);
        }
    
        @Override
        public void onResults(Bundle results) {
            Log.d("Speech", "onResults");
            
            ArrayList<String> strlist = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (strlist.size() > 0) {
                String phrase = strlist.get(0).toLowerCase(Locale.ENGLISH); 
                if (phrase.equals(SpeechRecognitionService.KEY_PHRASE)) {
                    Toast.makeText(getApplicationContext(), "Loading...", Toast.LENGTH_SHORT).show();
                    
                    Intent googleNow = getPackageManager().getLaunchIntentForPackage("com.google.android.googlequicksearchbox");
                    googleNow.setAction("android.intent.action.VOICE_ASSIST");
                    startActivity(googleNow);
                    
                    timeoutTimer(4000);
                    return;
                } else {
                    for (String str: strlist) {
                        Log.d("Speech", str);
                    }
                }
            }
            
            startListening();
        }
    
        @Override
        public void onRmsChanged(float rmsdB) {
            // Log.d("Speech", "onRmsChanged");
        }
    }
}
