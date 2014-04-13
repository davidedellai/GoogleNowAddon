package com.hahn.googlenowaddon.speech;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import com.hahn.googlenowaddon.Util;

import edu.cmu.pocketsphinx.*;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import static edu.cmu.pocketsphinx.Assets.syncAssets;
import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

public class SpeechRecognitionService extends Service {
    public static final int SERVICE_ID = 1524;
    public static final String LAUNCH_TARGET = "com.google.android.googlequicksearchbox";
    
    public static final String KEY_PHRASE = "start";
    public static String LAST_PACKAGE = LAUNCH_TARGET;
    
    private WakeLock partialWakeLock, fullWakeLock;
    private boolean isCharging;
    private int batteryCheckDelay;
    
    private Timer timeoutTimer;
    
    /** A handler linked to the main thread */
    private Handler mainThreadHandler;
    
    private SpeechRecognizer sr;
    
    private ActivityManager activityManager;
    private PowerManager powerManager;
    
    @SuppressWarnings("deprecation")
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.e("Speech", "Create service");
        
        mainThreadHandler = new Handler();
        
        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE); 
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        
        partialWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SpeechRecognitionWakeLock");
        fullWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | 
                PowerManager.FULL_WAKE_LOCK | 
                PowerManager.ACQUIRE_CAUSES_WAKEUP, "FullSpeechRecognitionWakeLock");
        
        File appDir;
        try {
            appDir = syncAssets(getApplicationContext());
        } catch (IOException e) {
            throw new RuntimeException("failed to synchronize assets", e);
        }
        
        sr = defaultSetup()
                .setAcousticModel(new File(appDir, "models/hmm/en-us-semi"))
                .setDictionary(new File(appDir, "models/lm/cmu07a.dic"))
                .setRawLogDir(appDir)
                .setKeywordThreshold(1e-5f)
                .getRecognizer();
        sr.addListener(new SpeechRecognitionListener());
        sr.addKeywordSearch("wakeup", KEY_PHRASE);
        
        Notification note = new Notification.Builder(getApplicationContext()).setPriority(Notification.PRIORITY_MIN).build();        
        startForeground(SERVICE_ID, note);
        
        startListening();
    }
    
    @SuppressLint("Wakelock")
	protected void startListening() {        
        cancelTimeout();
        
        if (!isCharging || --batteryCheckDelay < 0) {
            Log.e("Speech", "Check battery");
            
            isCharging = Util.isCharging(getApplicationContext());
            if (isCharging) {
                batteryCheckDelay = 3;
                
                if (!partialWakeLock.isHeld()) partialWakeLock.acquire();
                do_startListening();
            } else {
                Log.e("Speech", "Wait for battery");
                
                if (partialWakeLock.isHeld()) partialWakeLock.release();
                timeoutTimer(10000);
            }
        } else {
            do_startListening();
        }
    }
    
    private void do_startListening() {                
        if (!getForegroundPackage().equals(LAUNCH_TARGET)) {
            if (fullWakeLock.isHeld()) fullWakeLock.release();
            
            timeoutTimer(5000);
            
            sr.stop();
            sr.startListening("wakeup");
        } else {
            Log.e("Speech", "Waiting to close app");
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
    
    protected String getForegroundPackage() {
        return activityManager.getRunningTasks(1).get(0).baseActivity.getPackageName();
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
            sr.stop();
        }
        
        if (partialWakeLock != null && partialWakeLock.isHeld()) {
            partialWakeLock.release();
        }
        
        if (fullWakeLock != null && fullWakeLock.isHeld()) {
            fullWakeLock.release();
        }
    }
    
    protected class SpeechRecognitionListener implements RecognitionListener {        
        @Override
        public void onBeginningOfSpeech() {
            Log.e("Speech", "onBeginningOfSpeech");
            
            cancelTimeout();
        }
    
        @Override
        public void onEndOfSpeech() {
            Log.e("Speech", "onEndOfSpeech");
        }

        @Override
        public void onPartialResult(Hypothesis hypot) {            
            String phrase = hypot.getHypstr().toLowerCase(Locale.ENGLISH);
            
            Log.e("Speech", "onPartialResult = " + phrase);
            
            if (phrase.equals(KEY_PHRASE)) {
                sr.stop();
                
                LAST_PACKAGE = getForegroundPackage();
                if (!fullWakeLock.isHeld()) fullWakeLock.acquire();
                
                Intent googleNow = getPackageManager().getLaunchIntentForPackage(LAUNCH_TARGET);
                googleNow.setAction("android.intent.action.VOICE_ASSIST");
                startActivity(googleNow);
                
                timeoutTimer(4000);
            } else {            
                startListening();
            }
        }

        @Override
        public void onResult(Hypothesis hypot) {  
            String phrase = hypot.getHypstr().toLowerCase(Locale.ENGLISH);
            
            Log.e("Speech", "onResults = " + phrase);
            
            restartListening();
        }
    }
}
