package com.hahn.googlenowaddon;

import com.hahn.googlenowaddon.speech.SpeechRecognitionService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		Toast.makeText(getApplicationContext(), "Plugin installed", Toast.LENGTH_SHORT).show();
		
		getPackageManager().setComponentEnabledSetting(new ComponentName(this,
				getPackageName() + ".MainActivity-Alias"),
				PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
		
		startService(new Intent(getApplicationContext(), SpeechRecognitionService.class));
	}
	
	@Override
	protected void onStart() {
	    super.onStart();
	    
	    EditText text = (EditText) findViewById(R.id.key_phrase);
        text.setText(SpeechRecognitionService.KEY_PHRASE);
	}
	
	public void setKeyPhrase(View view) {
	    EditText text = (EditText) findViewById(R.id.key_phrase);
	    SpeechRecognitionService.KEY_PHRASE = text.getText().toString();
	}
}
