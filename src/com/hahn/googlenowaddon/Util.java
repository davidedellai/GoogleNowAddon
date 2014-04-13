package com.hahn.googlenowaddon;

import java.util.List;

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
}
