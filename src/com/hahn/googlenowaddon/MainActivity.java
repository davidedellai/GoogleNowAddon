package com.hahn.googlenowaddon;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Scanner;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.hahn.googlenowaddon.speech.SpeechRecognitionService;

public class MainActivity extends Activity {
    public static final String SAVE_FILE_NAME = "key_phrase";

    private SpeechRecognitionService service;
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            SpeechRecognitionService.MyBinder b = (SpeechRecognitionService.MyBinder) binder;
            service = b.getService();
            
            // Log.e("Main", "Service bound");
            recoverKeyPhrase();
        }

        public void onServiceDisconnected(ComponentName className) {
            saveKeyPharse();
            
            // Log.e("Main", "Service disconnected");
            service = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ui_main);

        getPackageManager().setComponentEnabledSetting(new ComponentName(this, getPackageName() + ".MainActivity-Alias"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        startService(new Intent(getApplicationContext(), SpeechRecognitionService.class));
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        Intent intent = new Intent(this, SpeechRecognitionService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        unbindService(mConnection);
    }

    public void setKeyPhrase(View view) {
        if (service != null) {
            EditText text = (EditText) findViewById(R.id.key_phrase);
            service.key_phrase = text.getText().toString();
            
            Toast.makeText(this, "Key Phrase Set", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Failed to update Key Phrase", Toast.LENGTH_SHORT).show();
        }
    }

    public void setRequireBattery(View view) {
        if (service != null) {
            CheckBox checkbox = (CheckBox) view;
            boolean require_charge = checkbox.isChecked();
            
            if (require_charge && !service.isCharging) {
                Toast.makeText(this, R.string.str_pause_now, Toast.LENGTH_SHORT).show();
            } else if (!require_charge && !service.isCharging) {
                Toast.makeText(this, R.string.str_resume_now, Toast.LENGTH_SHORT).show();
            }
            
            service.require_charge = require_charge;
            service.restartListening();
        } else {
            Toast.makeText(this, "Failed to update Require Charge", Toast.LENGTH_SHORT).show();
        }
    }

    public void saveKeyPharse() {
        if (service != null) {
            FileOutputStream fos = null;
            try {
                fos = openFileOutput(SAVE_FILE_NAME, Context.MODE_PRIVATE);
                fos.write(String.valueOf(service.key_phrase).getBytes());
                fos.write('\n');
                fos.write(String.valueOf(service.require_charge).getBytes());
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
        } else {
            Toast.makeText(this, "Failed to save changes", Toast.LENGTH_SHORT).show();
        }
    }

    public void recoverKeyPhrase() {
        if (service != null) {
            // Load
            File file = new File(SAVE_FILE_NAME);
            if (file.exists()) {
                Scanner scanner = null;
                try {
                    scanner = new Scanner(file);
                    
                    service.key_phrase = scanner.nextLine();                    
                    service.require_charge = scanner.nextBoolean();                   
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (scanner != null) {
                        scanner.close();
                    }
                }
            }
            
            // Update Ui
            EditText text = (EditText) findViewById(R.id.key_phrase);
            text.setText(service.key_phrase);
            
            CheckBox checkbox = (CheckBox) findViewById(R.id.require_battery);
            checkbox.setChecked(service.require_charge);
        }
    }
}
