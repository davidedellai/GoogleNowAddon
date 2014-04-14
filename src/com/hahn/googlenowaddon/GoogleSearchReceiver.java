package com.hahn.googlenowaddon;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hahn.googlenowaddon.Constants.Enum_Key;
import com.hahn.googlenowaddon.handlers.BluetoothHandler;
import com.hahn.googlenowaddon.handlers.MobileDataHandler;
import com.hahn.googlenowaddon.handlers.QueryMatcher;
import com.hahn.googlenowaddon.handlers.QueryReplier;
import com.hahn.googlenowaddon.handlers.WifiHandler;
import com.hahn.googlenowaddon.speech.SpeechRecognitionService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

public class GoogleSearchReceiver extends BroadcastReceiver {
	private static final Pattern SET_VOLUME_TO = Pattern.compile("(?:set )?volume (?:to )?(\\d{1,2})", Pattern.CASE_INSENSITIVE);
	
	private static final QueryMatcher		
		MEDIA_CONTROL = new QueryMatcher(new String[] {
				"CONTAINS ONE : playback, music, song",
				
				"START KEY Resume",
				"START KEY Pause",
				"START KEY Stop"
		}),
			
		TRACK_CONTROL = new QueryMatcher(new String[] {
				"CONTAINS ONE : track, song",
				
				"START KEY Next",
				"START KEY Previous"
		}),
		
		VOLUME_CONTROL = new QueryMatcher(new String[] {
				"STARTS WITH : volume, set volume",
				
				"KEY Up    : up",
				"KEY Down  : down",
				"KEY Max   : max, maximum",
				"KEY Min   : min, low, minimum"
		}),
		
		WIFI_CONTROL = new QueryMatcher(new String[] {
		        "CONTAINS : wifi",
		        
		        "KEY On     : on, enable",
                "KEY Off    : off, disable",
                "KEY Toggle : toggle"
		}),
		
		BLUETOOTH_CONTROL = new QueryMatcher(new String[] {
                "CONTAINS : bluetooth",
                
                "KEY On     : on, enable",
                "KEY Off    : off, disable",
                "KEY Toggle : toggle"
        }),
        
        DATA_CONTROL  = new QueryMatcher(new String[] {
                "CONTAINS : data",
                
                "KEY On     : on, enable",
                "KEY Off    : off, disable",
                "KEY Toggle : toggle"
        }),
        
        MINIMIZE = new QueryMatcher(new String[] {
                "CONTAINS ONE : minimize, exit",
                
                "MAX LENGTH   : 1"
        }),
        
        THANK_YOU = new QueryReplier(new String[] {
                "STARTS WITH : thank you, thanks",
                "MAX LENGTH  : 3",
                
                "REPLY : sure thing, no problem, you're welcome"
        });
		
	
	@Override
	@SuppressWarnings("incomplete-switch")
	public void onReceive(Context context, Intent intent) {
		Enum_Key key;
		
		String queryText = intent.getStringExtra(GoogleSearchApi.KEY_QUERY_TEXT);
		queryText = queryText.toLowerCase(Locale.ENGLISH);

		// Replies
		THANK_YOU.match(queryText);
		
		// Handers
		key = MEDIA_CONTROL.match(queryText);
		if (key != null) {
		    switch (key) {
		    case Resume:
				sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY);
				Toast.makeText(context, "Music Resumed", Toast.LENGTH_SHORT).show();
				return;
		    case Pause:
				sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE);
				Toast.makeText(context, "Music Paused", Toast.LENGTH_SHORT).show();
				return;
		    case Stop:
				sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_STOP);
				Toast.makeText(context, "Music Stopped", Toast.LENGTH_SHORT).show();
				return;
			}
		}

		key = TRACK_CONTROL.match(queryText);
		if (key != null) {
			switch (key) {
			case Next:
				sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT);
				Toast.makeText(context, "Next Song", Toast.LENGTH_SHORT).show();
				return;
			case Previous:
				sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
				Toast.makeText(context, "Previous Song", Toast.LENGTH_SHORT).show();
				return;
			}
		}

		key = VOLUME_CONTROL.match(queryText);
		if (key != null && key != Enum_Key.Success) {
			AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			switch(key) {
			case Up:
				audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
				return;
			case Down:
				audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
				return;
			case Max:
				audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI);
				return;
			case Min:
				audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 1, AudioManager.FLAG_SHOW_UI);
				return;
			}
		}
		
		key = WIFI_CONTROL.match(queryText);
		if (key != null) {
		    WifiHandler.handleStateChange(context, key);
		    return;
		}
		
		key = BLUETOOTH_CONTROL.match(queryText);
		if (key != null) {
		    BluetoothHandler.handleStateChange(context, key);
		}
		
		key = DATA_CONTROL.match(queryText);
		if (key != null) {
            MobileDataHandler.handleStateChange(context, key);
        }
		
		key = MINIMIZE.match(queryText);
		if (key != null) {
		    Log.e("Reciever", SpeechRecognitionService.LAST_PACKAGE);
		    
		    // Try to relaunch last package
		    if (!SpeechRecognitionService.LAST_PACKAGE.equals(SpeechRecognitionService.LAUNCH_TARGET)) {
    		    Intent lastPackage = context.getPackageManager().getLaunchIntentForPackage(SpeechRecognitionService.LAST_PACKAGE);
                if (lastPackage != null) {
                    lastPackage.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(lastPackage);
                    return;
                }
		    }
                
            // If failed to open last package, go to home screen
		    Intent home = new Intent(Intent.ACTION_MAIN);
		    home.addCategory(Intent.CATEGORY_HOME);
		    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	        context.startActivity(home);
		    return;
		}
		
		Matcher m = SET_VOLUME_TO.matcher(queryText);
		if (m.matches()) {
			AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			
			int level = Integer.valueOf(m.group(1));
			if (level >= 0 && level <= audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) {
				audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, AudioManager.FLAG_SHOW_UI);
				SpeechRecognitionService.speak("Set volume to " + level);
			}
			
			return;
		}
	}

	private static void sendMediaKey(Context context, int key) {
		Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
		synchronized (context) {
			i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, key));
			context.sendOrderedBroadcast(i, null);

			i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, key));
			context.sendOrderedBroadcast(i, null);
		}
	}
}