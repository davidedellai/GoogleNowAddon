package com.hahn.googlenowaddon;

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public class Util {

	public static String trim(String str) {
		return str.trim();
	}
	
	public static String[] trim(String[] strs) {
		for (int i = 0; i < strs.length; i++) {
			strs[i] = strs[i].trim();
		}
		
		return strs;
	}
	
	public static <T> void addAll(List<T> list, T[] vals) {
		for (T val: vals) {
			list.add(val);
		}
	}
	
	public static boolean isCharging(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }
}
