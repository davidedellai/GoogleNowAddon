package com.hahn.googlenowaddon.speech;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import com.hahn.googlenowaddon.Util;

import edu.cmu.pocketsphinx.*;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;

import static edu.cmu.pocketsphinx.Assets.syncAssets;
import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

public class SpeechRecognitionService extends Service implements OnInitListener {
    public static final int SERVICE_ID = 1524;
    public static final String LAUNCH_TARGET = "com.google.android.googlequicksearchbox";
    public static final String SAVE_FILE_NAME = "key_phrase";
    
    public static String KEY_PHRASE = "okay google";
    public static String LAST_PACKAGE = LAUNCH_TARGET;
    
    private WakeLock partialWakeLock, fullWakeLock;
    private boolean isCharging;
    private int batteryCheckDelay;
    
    private Timer timeoutTimer;
    
    /** A handler linked to the main thread */
    private Handler mainThreadHandler;
    
    private static TextToSpeech tts;
    private SpeechRecognizer sr;
    
    private ActivityManager activityManager;
    private PowerManager powerManager;
    private static AudioManager audioManager;
    
    @SuppressWarnings("deprecation")
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.e("Speech", "Create service");
        
        recoverKeyPhrase();
        
        mainThreadHandler = new Handler();
        
        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE); 
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        tts = new TextToSpeech(this, this);
        tts.setSpeechRate(0.9f);
        
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
    
    @SuppressLint("Wakelock")
	private void do_startListening() {                
        if (!getForegroundPackage().equals(LAUNCH_TARGET)) {
            if (fullWakeLock.isHeld()) fullWakeLock.release();
            
            timeoutTimer(5000);
            
            sr.stop();
            
            sr.addKeywordSearch("wakeup", KEY_PHRASE);
            sr.startListening("wakeup");
        } else {
            if (!fullWakeLock.isHeld()) fullWakeLock.acquire();
            
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
        return Service.START_STICKY;
    }
    
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.v("TTS", "Language is not available.");
            } else {
                speak("Google Now Add On started");
            }
        } else {
            Log.v("TTS", "Could not initialize TextToSpeech.");
        }
    }
    
    public static void speak(String str) {
        if (tts != null) {
            if (audioManager != null) {
                audioManager.requestAudioFocus(new OnAudioFocusChangeListener() {
                            @Override
                            public void onAudioFocusChange(int focusChange) { }
                        },
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN
                    );
            }
            
            tts.speak(str, TextToSpeech.QUEUE_FLUSH, null);
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        Log.e("Speech", "Destroy");
        
        if (sr != null) {
            sr.stop();
        }
        
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        
        if (partialWakeLock != null && partialWakeLock.isHeld()) {
            partialWakeLock.release();
        }
        
        if (fullWakeLock != null && fullWakeLock.isHeld()) {
            fullWakeLock.release();
        }
        
        saveKeyPharse();
    }
    
    private void saveKeyPharse() {
        FileOutputStream fos = null;
        try {
            fos = openFileOutput(SAVE_FILE_NAME, Context.MODE_PRIVATE);
            fos.write(KEY_PHRASE.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void recoverKeyPhrase() {
        File file = new File(SAVE_FILE_NAME);
        if (file.exists()) {
            Scanner scanner = null;
            try {
                scanner = new Scanner(file);
                if (scanner.hasNext()) {
                    KEY_PHRASE = scanner.nextLine();
                }                
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (scanner != null) {
                    scanner.close();
                }
            }
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
        public void onResult(Hypothesis hypot) {  }
    }

}
