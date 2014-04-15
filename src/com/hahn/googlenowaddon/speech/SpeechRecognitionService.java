package com.hahn.googlenowaddon.speech;

import static edu.cmu.pocketsphinx.Assets.syncAssets;
import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.widget.Toast;

import com.hahn.googlenowaddon.PowerStateReceiver;
import com.hahn.googlenowaddon.R;
import com.hahn.googlenowaddon.Util;

import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

public class SpeechRecognitionService extends Service implements
        OnInitListener, OnAudioFocusChangeListener {
    public static final String TAG = "Speech";
    public static final String TOGGLE_PAUSED = "togglePaused";
    public static final String OPEN_UI = "openUI";
    
    public static final int SERVICE_ID = 1524;
    public static final String LAUNCH_TARGET = "com.google.android.googlequicksearchbox";

    public static String LAST_PACKAGE = LAUNCH_TARGET;

    /* Management */    
    private ActivityManager activityManager;
    private PowerManager powerManager;
    
    private final Binder binder = new MyBinder();

    /* Text to speech */
    private static TextToSpeech tts;
    private AudioManager audioManager;

    /* Battery */
    public boolean require_charge = true;
    private boolean wasRunning = true;
    public boolean charging;
    private WakeLock partialWakeLock, fullWakeLock;

    /* Timeout */
    private Timer timeoutTimer;
    private Handler mainThreadHandler;

    /* Speech recognition */
    public String key_phrase = "okay google";
    private boolean paused;
    private SpeechRecognizer sr;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate() {
        super.onCreate();

        Context context = getApplicationContext();
        
        Log.e("Speech", "Create service");

        mainThreadHandler = new Handler();

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        charging = Util.isCharging(context);
        partialWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SpeechRecognitionWakeLock");
        fullWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "FullSpeechRecognitionWakeLock");

        tts = new TextToSpeech(this, this);
        tts.setSpeechRate(0.9f);
        
        // Create speech recognition
        File appDir;
        try {
            appDir = syncAssets(context);
            
            sr = defaultSetup().setAcousticModel(new File(appDir, "models/hmm/en-us-semi")).setDictionary(new File(appDir, "models/lm/cmu07a.dic")).setRawLogDir(appDir).setKeywordThreshold(1e-5f).getRecognizer();
            sr.addListener(new SpeechRecognitionListener());
            sr.addKeywordSearch("wakeup", key_phrase);
        } catch (IOException e) {
            throw new RuntimeException("failed to synchronize assets", e);
        }

        // Create notification
        updateNotification();
        
        Toast.makeText(getApplicationContext(), R.string.str_start_now, Toast.LENGTH_SHORT).show();

        // Start speech recognition
        startListening();
    }
    
    private void updateNotification() {
        Log.e(TAG, "Update notification");
        
        String text = (waitingForCharge() ? "Waiting for Charger" : (paused ? "Paused" : "Running"));
        
        Context context = getApplicationContext();
        Intent clickIntent = new Intent(context, SpeechRecognitionService.class);
        clickIntent.putExtra(TOGGLE_PAUSED, true);
        
        PendingIntent pendingClickIntent = PendingIntent.getService(context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        
        Notification note = new Notification.Builder(getApplicationContext())
                .setPriority(Notification.PRIORITY_MIN)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Google Now Addon")
                .setContentText(text)
                .setContentIntent(pendingClickIntent)
                .build();

        startForeground(SERVICE_ID, note);
    }
    
    public boolean isPaused() {
        return paused || waitingForCharge();
    }
    
    public boolean waitingForCharge() {
        return require_charge && !charging;
    }

    @SuppressLint("Wakelock")
    protected void startListening() {
        cancelTimeout();

        // Check paused
        if (isPaused()) {    
            if (fullWakeLock.isHeld()) fullWakeLock.release();
            if (partialWakeLock.isHeld()) partialWakeLock.release();
            
            if (wasRunning) {
                wasRunning = false;
                
                Toast.makeText(getApplicationContext(), R.string.str_pause_now, Toast.LENGTH_SHORT).show();
            }
            
            return;
        }
        
        // Check if was unpaused
        if (!wasRunning) {
            wasRunning = true;
            
            Toast.makeText(getApplicationContext(), R.string.str_resume_now, Toast.LENGTH_SHORT).show();
        }

        // Start listening
        if (!powerManager.isScreenOn() || !getForegroundPackage().equals(LAUNCH_TARGET)) {
            if (fullWakeLock.isHeld()) fullWakeLock.release();

            sr.stop();

            sr.addKeywordSearch("wakeup", key_phrase);
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

    public void restartListening() {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                sr.cancel();
                startListening();
            }

        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand");
        
        if (intent.getBooleanExtra(PowerStateReceiver.START_CHARING, false)) {
            charging = true;
        } else if (intent.getBooleanExtra(PowerStateReceiver.STOP_CHARING, false)) {
            charging = false;
        } else if (intent.getBooleanExtra(TOGGLE_PAUSED, false)) {
            paused = !paused;
        } else if (intent.getBooleanExtra(OPEN_UI, false)) {
            // TODO
        } else {
            return Service.START_STICKY;
        }
        
        updateNotification();
        restartListening();
        
        return Service.START_STICKY;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.v("TTS", "Language is not available.");
            }
        } else {
            Log.v("TTS", "Could not initialize TextToSpeech.");
        }
    }

    public static void speak(String str) {
        if (tts != null) {
            tts.speak(str, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    @Override
    public void onAudioFocusChange(int focus) {
        if (AudioManager.AUDIOFOCUS_LOSS == focus) {
            Log.e(TAG, "Lost audio focus");
        } else if (AudioManager.AUDIOFOCUS_GAIN == focus) {
            Log.e(TAG, "Got audio focus");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
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
    }
    
    protected String getForegroundPackage() {
        return activityManager.getRunningTasks(1).get(0).baseActivity.getPackageName();
    }

    class SpeechRecognitionListener implements RecognitionListener {
        @Override
        public void onBeginningOfSpeech() {
            // Log.e("Speech", "onBeginningOfSpeech");

            cancelTimeout();
        }

        @Override
        public void onEndOfSpeech() {
            // Log.e("Speech", "onEndOfSpeech");
        }

        @Override
        public void onPartialResult(Hypothesis hypot) {
            String phrase = hypot.getHypstr().toLowerCase(Locale.ENGLISH);

            Log.e("Speech", "onPartialResult = " + phrase);

            if (phrase.equals(key_phrase)) {
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
        public void onResult(Hypothesis hypot) {}
    }

    public class MyBinder extends Binder {
        public SpeechRecognitionService getService() {
            return SpeechRecognitionService.this;
        }
    }
}
