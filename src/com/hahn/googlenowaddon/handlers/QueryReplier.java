package com.hahn.googlenowaddon.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;

import com.hahn.googlenowaddon.Constants.Enum_Key;
import com.hahn.googlenowaddon.speech.SpeechRecognitionService;

public class QueryReplier extends QueryMatcher {
    public final static String TAG = "QueryReplier"; 
    private final static Pattern REPLY_KEY_REGEX = Pattern.compile("(?:output|reply|response)( to key ?\\[?(\\w+)\\]?)?", Pattern.CASE_INSENSITIVE);
    
    protected HashMap<Enum_Key, List<String>> replies;
    private Random rand;
    
    public QueryReplier(String[] defs) {
        super(defs);
        
        rand = new Random();
    }
    
    private HashMap<Enum_Key, List<String>> getReplies() {
        if (replies == null) {
            replies = new HashMap<Enum_Key, List<String>>();
        }
        
        return replies;
    }
    
    protected boolean checkOtherMatches(String type, String[] params) {
        if (super.checkOtherMatches(type, params)) {
            return true;
        } else {
            Matcher m = REPLY_KEY_REGEX.matcher(type);
            if (m.matches()) {
                Enum_Key key = Enum_Key.Success;
                boolean hasKey = (m.group(1) != null);
                if (hasKey) {
                    key = Enum_Key.valueOf(m.group(2));
                }
                
                HashMap<Enum_Key, List<String>> replies = getReplies();
                
                List<String> list;
                if (!replies.containsKey(key)) {
                    list = new ArrayList<String>();
                    replies.put(key, list);
                } else {
                    list = replies.get(key);
                }
                
                for (String p: params) {
                    list.add(p);
                }
                return true;
            }
        }
        
        return false;
    }

    @Override
    public Enum_Key match(Context context, String queryText) {
        Enum_Key key = super.match(context, queryText);
        
        if (key != null) {
            // Try with given key
            List<String> list = replies.get(key);
            if (list != null) {
                String mss = list.get(rand.nextInt(list.size()));
                SpeechRecognitionService.speak(context, mss);
            } else {
                // Try with default key
                list = replies.get(Enum_Key.Success);
                if (list != null) {
                    String mss = list.get(rand.nextInt(list.size()));
                    SpeechRecognitionService.speak(context, mss);
                }
            }
        }
        
        return key;
    }

}
