package com.hahn.googlenowaddon;

public class Constants {
    public static class Preferences {
        public static final String KEY = "com.hahn.googlenowaddon",
                                   KEY_PHRASE_KEY = "com.hahn.googlenowaddon.key_phrase",
                                   DEFAULT_KEY_PHRASE = "okay google",
                                   KEY_REQUIRE_CHARGER = "com.hahn.googlenowaddon.require_charger";
    }
    
    public static class SpeechRecognitionServiceExtras {
        public static final String TOGGLE_PAUSED = "togglePaused",
                                   START_CHARING = "startCharging",
                                   STOP_CHARING = "stopCharging";
        
    }
    
	public enum Enum_Key {
		Success, 
		Resume, Pause, Stop,
		Next, Previous,
		Up, Down, Max, Min,
		On, Off, Toggle
	}
}
