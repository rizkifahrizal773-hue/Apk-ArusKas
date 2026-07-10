package com.aruskas.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.aruskas.app.model.User;

public class SessionManager {
    private static final String PREF_NAME = "ArusKasSessionPref";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_USER_EMAIL = "userEmail";
    private static final String KEY_USER_TOKEN = "userToken";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";

    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void saveSession(User user) {
        editor.putInt(KEY_USER_ID, user.getId());
        editor.putString(KEY_USER_NAME, user.getName());
        editor.putString(KEY_USER_EMAIL, user.getEmail());
        editor.putString(KEY_USER_TOKEN, user.getToken());
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getToken() {
        return pref.getString(KEY_USER_TOKEN, "");
    }

    public User getUserDetails() {
        if (!isLoggedIn()) return null;
        
        User user = new User();
        user.setId(pref.getInt(KEY_USER_ID, -1));
        user.setName(pref.getString(KEY_USER_NAME, ""));
        user.setEmail(pref.getString(KEY_USER_EMAIL, ""));
        user.setToken(pref.getString(KEY_USER_TOKEN, ""));
        return user;
    }

    public void clearSessionAndLogout() {
        editor.clear();
        editor.apply();
    }
}
