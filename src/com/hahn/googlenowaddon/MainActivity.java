package com.hahn.googlenowaddon;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.hahn.googlenowaddon.Constants.Preferences;
import com.mohammadag.googlesearchapi.hahn.GoogleSearchApi;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    
    private SpeechRecognitionService service;
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            SpeechRecognitionService.MyBinder b = (SpeechRecognitionService.MyBinder) binder;
            service = b.getService();
            
            Log.e(TAG, "Service connected");
        }

        public void onServiceDisconnected(ComponentName className) {
            savePrefs();
            
            service = null;
            
            Log.e(TAG, "Service disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.e(TAG, "onCreate");
        
        setContentView(R.layout.activity_ui_main);

        getPackageManager().setComponentEnabledSetting(new ComponentName(this, getPackageName() + ".MainActivity-Alias"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        Log.e(TAG, "onResume");
        GoogleSearchApi.registerQueryGroup(this, GoogleSearchReceiver.group);
        
        // Load prefs
        SharedPreferences prefs = MainActivity.getPrefs(this);
        String key_phrase = prefs.getString(Preferences.KEY_PHRASE_KEY, Preferences.DEFAULT_KEY_PHRASE);
        boolean require_charge = prefs.getBoolean(Preferences.KEY_REQUIRE_CHARGER, true);
        
        // Update Ui
        EditText text = (EditText) findViewById(R.id.key_phrase);
        text.setText(key_phrase);
        
        CheckBox checkbox = (CheckBox) findViewById(R.id.require_battery);
        checkbox.setChecked(require_charge);
        
        // If should, start intent
        if (!require_charge || Util.isCharging(this)) {
            bindIntent();
        }
    }

    @Override
    protected void onPause() {        
        if (service != null) {
            Log.e(TAG, "Unbind");
            
            unbindService(mConnection);
        }
        
        super.onPause();
    }
    
    private void bindIntent() {
        Log.e(TAG, "Bind intent");
        
        Intent intent = new Intent(this, SpeechRecognitionService.class);
        startService(intent);
        bindService(intent, mConnection, 0);
    }

    public void setKeyPhrase(View view) {
        savePrefs();
            
        if (service != null) {
            EditText text = (EditText) findViewById(R.id.key_phrase);
            String key_phrase = text.getText().toString();
            
            service.setKeyPhrase(key_phrase);
        }
        
        Toast.makeText(this, R.string.str_key_phrase_updated, Toast.LENGTH_SHORT).show();
    }

    public void setRequireCharge(View view) {
        savePrefs();
        
        CheckBox checkbox = (CheckBox) view;
        boolean require_charge = checkbox.isChecked();
        
        if (service != null) {
            service.setRequiresCharge(require_charge);
        } else if (!require_charge || Util.isCharging(this)) {
            bindIntent();
        }
    }

    public void savePrefs() {
        EditText text = (EditText) findViewById(R.id.key_phrase);
        String key_phrase = text.getText().toString();
        
        CheckBox checkbox = (CheckBox) findViewById(R.id.require_battery);
        boolean require_charge = checkbox.isChecked();
        
        SharedPreferences.Editor prefs = MainActivity.getPrefs(this).edit();
        
        prefs.putString(Preferences.KEY_PHRASE_KEY, key_phrase);
        prefs.putBoolean(Preferences.KEY_REQUIRE_CHARGER, require_charge);
        
        prefs.commit();
    }
    
    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(Preferences.KEY, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);   
    }
}
