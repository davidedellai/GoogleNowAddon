package com.hahn.googlenowaddon.handlers;

import android.content.Context;

import com.hahn.googlenowaddon.Constants.Enum_Key;

public interface IMatcher {
    /**
     * Checks if the given query matches this
     * @param context A context to use to display any actions
     * @param queryText The query to check
     * @return `null` failed, `KEY.Success` general success, anything else is a set key 
     */
    public Enum_Key match(Context context, String queryText);
}
