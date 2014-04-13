package com.hahn.googlenowaddon.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hahn.googlenowaddon.Util;
import com.hahn.googlenowaddon.Constants.Enum_Key;

public class QueryMatcher {
	private final static Pattern KEY_REGEX = Pattern.compile("(start)?\\s*key\\s*\\[?([_a-zA-Z]+)\\]?", Pattern.CASE_INSENSITIVE);
	
	private final List<String> contains_all, contains_one, not_contains, not_start_with, start_with_one;
	private final HashMap<String, Enum_Key> keys, start_keys;
	private int max_length;
	
	public QueryMatcher(String[] defs) {
		contains_all = new ArrayList<String>();
		contains_one = new ArrayList<String>();
		not_contains = new ArrayList<String>();
		not_start_with = new ArrayList<String>();
		start_with_one = new ArrayList<String>();
		
		keys = new HashMap<String, Enum_Key>();
		start_keys = new HashMap<String, Enum_Key>();
		
		Logger log = Logger.getLogger("Now Addon");
		log.info("Create new query matcher");
		
		for (String def: defs) {
			String[] type_params = def.split(":");
			
			String type = Util.trim(type_params[0]);
			if (type_params.length == 2) {
				String[] params = Util.trim(type_params[1].split(","));
				
				if (type.matches("(?i)contains all")) {
					Util.addAll(contains_all, params);
				} else if (type.matches("(?i)contains (one|any)")) {
					Util.addAll(contains_one, params);
				} else if (type.matches("(?i)contains") && params.length == 1) {
				    contains_all.add(params[0]);
				} else if (type.matches("(?i)[not|does not|doesn't] contain")) {
					Util.addAll(not_contains, params);
				} else if (type.matches("(?i)[not|does not|doesn't] start with")) {
					Util.addAll(not_start_with, params);
				} else if (type.matches("(?i)starts? with( one of| a| one)?")) {
					Util.addAll(start_with_one, params);
				} else if (type.matches("(?i)max length") && params.length == 1 && params[0].matches("\\d+")) {
				    max_length = Integer.valueOf(params[0]);
				} else {
					boolean isKey = checkForKey(type, params);
					if (!isKey) {
						log.log(Level.SEVERE, "Corrup definition '" + def + "'");
					}
				}
			} else {
				boolean isKey = checkForKey(type, null);
				if (!isKey) {
					log.log(Level.SEVERE, "Corrup definition '" + def + "'");
				}
			}
		}
	}
	
	private boolean checkForKey(String type, String[] params) {
		Matcher m = KEY_REGEX.matcher(type);
		if (m.matches()) {
			boolean start_key = (m.group(1) != null);
			
			String key = m.group(2);
			if (params != null) {
				for (String p: params) {
					if (start_key) start_keys.put(p, Enum_Key.valueOf(key));
					else keys.put(p, Enum_Key.valueOf(key));
				}
			} else {
				keys.put(key.toLowerCase(Locale.ENGLISH), Enum_Key.valueOf(key));
			}
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * Checks if the given query matches this
	 * @param queryText The query to check
	 * @return `null` failed, `KEY.Success` general success, anything else is a set key 
	 */
	public Enum_Key match(String queryText) {
	    // Check for longer than max length
	    if (max_length > 0) {
	        if (queryText.split(" ").length > max_length) {
	            return null;
	        }
	    }
	    
		// Check contains all of the given
		for (String contain_str: contains_all) {
			if (!queryText.contains(contain_str)) {
				return null;
			}
		}
		
		// Check does not contain any of the given
		for (String not_contain_str: not_contains) {
			if (queryText.contains(not_contain_str)) {
				return null;
			}
		}
		
		// Check does not start with any of the given
		for (String not_start_with_str: not_start_with) {
			if (queryText.startsWith(not_start_with_str)) {
				return null;
			}
		}
		
		// Check starts with at least one of the given
		if (start_with_one.size() > 0) {
			boolean start_match = false;
			for (String start_with_one_str: start_with_one) {
				if (queryText.startsWith(start_with_one_str)) {
					start_match = true;
					break;
				}
			}
			if (!start_match) return null;
		}
		
		// Check contains at least one of the given
		if (contains_one.size() > 0) {
			boolean contain_match = false;
			for (String contains_one_str: contains_one) {
				if (queryText.contains(contains_one_str)) {
					contain_match = true;
					break;
				}
			}
			if (!contain_match) return null;
		}
		
		// Check for keys
		for (Entry<String, Enum_Key> key: keys.entrySet()) {
			if (queryText.contains(key.getKey())) {
				return key.getValue();
			}
		}
		
		return Enum_Key.Default;
	}
}
