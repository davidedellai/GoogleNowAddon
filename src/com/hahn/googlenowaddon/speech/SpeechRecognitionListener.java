package com.hahn.googlenowaddon.speech;

import java.util.ArrayList;
import java.util.Locale;

import android.content.Context;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

public class SpeechRecognitionListener implements RecognitionListener {

	private Context context;
	
	public SpeechRecognitionListener(Context context) {
		this.context = context;
	}
	
    @Override
    public void onBeginningOfSpeech() {
        Log.d("Speech", "onBeginningOfSpeech");
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.d("Speech", "onBufferReceived");
    }

    @Override
    public void onEndOfSpeech() {
        Log.d("Speech", "onEndOfSpeech");
    }

    @Override
    public void onError(int error) {
        Log.d("Speech", "onError " + error);
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
        Log.d("Speech", "onEvent");
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        Log.d("Speech", "onPartialResults");
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        Log.d("Speech", "onReadyForSpeech");
    }

    @Override
    public void onResults(Bundle results) {
        Log.d("Speech", "onResults");
        
        ArrayList<String> strlist = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);        
        if (strlist.size() > 0 && strlist.get(0).toLowerCase(Locale.ENGLISH).equals("okay google")) {
        	Toast.makeText(context, "Loading...", Toast.LENGTH_LONG).show();
        }
        
        
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        // Log.d("Speech", "onRmsChanged");
    }

}