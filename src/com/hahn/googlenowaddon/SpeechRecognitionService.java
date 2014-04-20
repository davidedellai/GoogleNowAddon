package com.hahn.googlenowaddon;

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
import android.content.SharedPreferences;
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

import com.hahn.googlenowaddon.Constants.Preferences;
import com.hahn.googlenowaddon.Constants.SpeechRecognitionServiceActions;
import com.mohammadag.googlesearchapi.hahn.GoogleSearchApi;

import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

public class SpeechRecognitionService extends Service implements
        OnInitListener, OnAudioFocusChangeListener {
    public static final String TAG = "SpeechRecognitionService";
    
    public static final int SERVICE_ID = 1524;
    public static final String LAUNCH_TARGET = "com.google.android.googlequicksearchbox";

    public static String LAST_PACKAGE = LAUNCH_TARGET;

    /* Management */    
    private PowerManager powerManager;
    private ActivityManager activityManager;
    private final Binder binder = new MyBinder();

    /* Text to speech */
    private static TextToSpeech tts;
    private AudioManager audioManager;

    /* Timeout */
    private Timer timeoutTimer;
    private Handler mainThreadHandler;
    
    /* Battery */
    private boolean require_charge;
    private WakeLock partialWakeLock, fullWakeLock;

    /* Speech recognition */
    private File appDir;
    private String key_phrase;
    
    private boolean isPaused;
    private boolean isStopRequested;
    private boolean hasStarted;
    private boolean ranLastLoop;
    private SpeechRecognizer sr;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate() {
        super.onCreate();

        Log.e(TAG, "onCreate");
        
        GoogleSearchApi.registerQueryGroup(this, GoogleSearchReceiver.group);
        
        mainThreadHandler = new Handler();

        // Get managers
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        // Create wake locks
        partialWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SpeechRecognitionWakeLock");
        fullWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "FullSpeechRecognitionWakeLock");

        // Create tts
        tts = new TextToSpeech(this, this);
        tts.setSpeechRate(0.9f);
        
        // Refresh prefs
        SharedPreferences prefs = MainActivity.getPrefs(this);
        key_phrase = prefs.getString(Preferences.KEY_PHRASE_KEY, Preferences.DEFAULT_KEY_PHRASE);
        require_charge = prefs.getBoolean(Preferences.KEY_REQUIRE_CHARGER, true);
        
        // Load speech recognition
        try {
            appDir = syncAssets(this);
            
            sr = defaultSetup().setAcousticModel(new File(appDir, "models/hmm/en-us-semi"))
                    .setDictionary(new File(appDir, "models/lm/cmu07a.dic"))
                    .setRawLogDir(appDir)
                    .setKeywordThreshold(1e-5f)
                    .getRecognizer();
            sr.addListener(new SpeechRecognitionListener());
            sr.addKeywordSearch("wakeup", key_phrase);
        } catch (IOException e) {
            throw new RuntimeException("failed to synchronize assets", e);
        }
    }
    
    public void setRequiresCharge(boolean state) {
        if (state != require_charge) {
            Log.e(TAG, "setRequiresCharge");
            
            require_charge = state;
            
            if (shouldStop()) {
                requestStop();
            } else {
                requestStart();
            }
        }
    }
    
    public void setKeyPhrase(String phrase) {
        key_phrase = phrase;
        restartListening();
    }
    
    protected boolean shouldStop() {
        return isStopRequested || (require_charge && !Util.isCharging(this));
    }
    
    private void requestStop() {
        Log.e(TAG, "Requesting stop");
        
        isStopRequested = true;
        startTimeoutTimer(250);
    }
    
    private synchronized void requestStart() {
        if (shouldStop()) return;
        
        hasStarted = true;
        
        updateNotification();
        restartListening();
    }
    
    protected void updateNotification() {
        Log.e(TAG, "Update notification");
        
        Intent clickIntent = new Intent(this, SpeechRecognitionService.class);
        clickIntent.setAction(SpeechRecognitionServiceActions.TOGGLE_PAUSED);
        PendingIntent pendingClickIntent = PendingIntent.getService(this, SERVICE_ID, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        
        int icon = (isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
        String mss = (isPaused ? "Paused" : "Running");
        
        Notification note = new Notification.Builder(this)
                .setPriority(Notification.PRIORITY_MIN)
                .setContentTitle("Google Now+")
                .setSmallIcon(icon)
                .setContentText(mss)
                .setContentIntent(pendingClickIntent)
                .addAction(icon, mss, pendingClickIntent)
                .build();

        startForeground(SERVICE_ID, note);
    }

    @SuppressLint("Wakelock")
    protected void startListening() {       
        cancelTimeout();
        
        // Check if stop requested
        if (isStopRequested) {
            Log.e(TAG, "Performing requested stop");
            
            if (hasStarted) {
                Toast.makeText(this, R.string.str_stop_now, Toast.LENGTH_SHORT).show();
            }
            
            // Update flags
            isPaused = false;
            hasStarted = false;
            ranLastLoop = false;
            isStopRequested = false;
            
            stopForeground(true);
            stopSelf();
            
            return;
        } else if (!hasStarted) {
            return;
        }
        
        sr.stop();

        // Check paused
        if (isPaused) {    
            if (fullWakeLock.isHeld()) fullWakeLock.release();
            if (partialWakeLock.isHeld()) partialWakeLock.release();
            
            if (ranLastLoop) {
                ranLastLoop = false;
                
                Toast.makeText(this, R.string.str_pause_now, Toast.LENGTH_SHORT).show();
            }
            
            return;
        }
        
        // Check if was unpaused
        if (!ranLastLoop) {
            ranLastLoop = true;
            
            Toast.makeText(this, R.string.str_start_now, Toast.LENGTH_SHORT).show();
        }

        // Start listening
        if (!powerManager.isScreenOn() || !getForegroundPackage().equals(LAUNCH_TARGET)) {
            if (fullWakeLock.isHeld()) fullWakeLock.release();

            sr.addKeywordSearch("wakeup", key_phrase);
            sr.startListening("wakeup");
            
            startTimeoutTimer(5000);
        } else {
            Log.e(TAG, "Waiting to close app");
            startTimeoutTimer(2000);
        }
    }

    protected void cancelTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }

    public void startTimeoutTimer(int delay) {
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
                startListening();
            }

        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand");
        
        String action = intent.getAction();
        if (action == null) action = "";
        
        if (action.equals(SpeechRecognitionServiceActions.STOP_CHARGING)) {
            // If stopped charging and needed stop service
            if (require_charge && !Util.isCharging(this)) {
                requestStop();
            }
        } else if (action.equals(SpeechRecognitionServiceActions.TOGGLE_PAUSED)) {
            // Toggle paused
            isPaused = !isPaused;
            
            if (hasStarted) {
                updateNotification();
                restartListening();
            }
        } else {
            // Show message if first start and should keep running
            if (shouldStop()) {
                requestStop();
            } else if (!hasStarted) {
                requestStart();
            }
        }
        
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

    public static void speak(Context context, String str) {
        if (tts != null) {
            tts.speak(str, TextToSpeech.QUEUE_FLUSH, null);
        } else {
            GoogleSearchApi.speak(context, str);
        }
    }

    @Override
    public void onAudioFocusChange(int focus) {
        if (AudioManager.AUDIOFOCUS_LOSS == focus) {
            Log.e(TAG, "Lost audio focus");
        } else if (AudioManager.AUDIOFOCUS_GAIN == focus) {
            Log.e(TAG, "Got audio focus");
        } else {
            Log.e(TAG, "Audio focus changed?");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "Destroy");

        stopForeground(true);
        
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
            cancelTimeout();
        }

        @Override
        public void onEndOfSpeech() { }

        @Override
        public void onPartialResult(Hypothesis hypot) {
            String phrase = hypot.getHypstr().toLowerCase(Locale.ENGLISH);

            Log.e(TAG, "onPartialResult = " + phrase);

            if (phrase.equals(key_phrase)) {
                sr.stop();

                LAST_PACKAGE = getForegroundPackage();
                if (!fullWakeLock.isHeld()) fullWakeLock.acquire();

                Intent googleNow = getPackageManager().getLaunchIntentForPackage(LAUNCH_TARGET);
                googleNow.setAction("android.intent.action.VOICE_ASSIST");
                startActivity(googleNow);

                startTimeoutTimer(4000);
            } else {
                startListening();
            }
        }

        @Override
        public void onResult(Hypothesis hypot) {
            String phrase = hypot.getHypstr().toLowerCase(Locale.ENGLISH);

            Log.e(TAG, "onResult = " + phrase);
        }
    }

    public class MyBinder extends Binder {
        public SpeechRecognitionService getService() {
            return SpeechRecognitionService.this;
        }
    }
}
