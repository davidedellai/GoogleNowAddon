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
import android.app.Service;
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
    public static final int SERVICE_ID = 1524;
    public static final String LAUNCH_TARGET = "com.google.android.googlequicksearchbox";

    public static String LAST_PACKAGE = LAUNCH_TARGET;

    /* Management */
    private ActivityManager activityManager;
    private PowerManager powerManager;
    private final Binder binder = new MyBinder();

    /* Text to speech */
    private static TextToSpeech tts;
    private static AudioManager audioManager;
    private static OnAudioFocusChangeListener audioFocusListener;

    /* Battery */
    public boolean require_charge = true;
    public boolean isCharging;
    private WakeLock partialWakeLock, fullWakeLock;

    /* Timeout */
    private Timer timeoutTimer;
    private Handler mainThreadHandler;

    /* Speech recognition */
    public String key_phrase = "okay google";
    private SpeechRecognizer sr;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate() {
        super.onCreate();

        Log.e("Speech", "Create service");

        mainThreadHandler = new Handler();

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioFocusListener = this;

        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        isCharging = Util.isCharging(getApplicationContext());
        
        tts = new TextToSpeech(this, this);
        tts.setSpeechRate(0.9f);

        partialWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SpeechRecognitionWakeLock");
        fullWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "FullSpeechRecognitionWakeLock");

        File appDir;
        try {
            appDir = syncAssets(getApplicationContext());
        } catch (IOException e) {
            throw new RuntimeException("failed to synchronize assets", e);
        }

        sr = defaultSetup().setAcousticModel(new File(appDir, "models/hmm/en-us-semi")).setDictionary(new File(appDir, "models/lm/cmu07a.dic")).setRawLogDir(appDir).setKeywordThreshold(1e-5f).getRecognizer();
        sr.addListener(new SpeechRecognitionListener());
        sr.addKeywordSearch("wakeup", key_phrase);

        Notification note = new Notification.Builder(getApplicationContext()).setPriority(Notification.PRIORITY_MIN).build();
        startForeground(SERVICE_ID, note);

        startListening();
    }

    @SuppressLint("Wakelock")
    protected void startListening() {
        cancelTimeout();

        // Check battery
        if (require_charge && !isCharging) {    
            if (fullWakeLock.isHeld()) fullWakeLock.release();
            if (partialWakeLock.isHeld()) partialWakeLock.release();
            
            return;
        }

        // Start listening
        if (!getForegroundPackage().equals(LAUNCH_TARGET)) {
            if (fullWakeLock.isHeld()) fullWakeLock.release();

            timeoutTimer(5000);

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

    protected String getForegroundPackage() {
        return activityManager.getRunningTasks(1).get(0).baseActivity.getPackageName();
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
        if (intent.getBooleanExtra(PowerStateReceiver.START_CHARING, false)) {
            if (require_charge && !isCharging) {
                Toast.makeText(getApplicationContext(), R.string.str_resume_now, Toast.LENGTH_LONG).show();
            }
            
            isCharging = true;
            restartListening();
        } else if (intent.getBooleanExtra(PowerStateReceiver.STOP_CHARING, false)) {
            if (require_charge && isCharging) {
                Toast.makeText(getApplicationContext(), R.string.str_pause_now, Toast.LENGTH_LONG).show();
            }
            
            isCharging = false;
            restartListening();
        }
        
        return Service.START_STICKY;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
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
            if (audioManager != null && audioFocusListener != null) {
                Log.e("Speak", "Request audio focus");
                audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }

            tts.speak(str, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {

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

    class SpeechRecognitionListener implements RecognitionListener {
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
