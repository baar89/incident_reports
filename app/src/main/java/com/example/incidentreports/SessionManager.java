package com.example.incidentreports;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores authenticated admin session details for PocketBase calls.
 */
public class SessionManager {
    private static final String PREF_NAME = "fire_app_session";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_RESPONDER_ID = "responder_id";

    private final SharedPreferences sharedPreferences;

    public SessionManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String token, String userId, String fullName, String responderId) {
        sharedPreferences.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_FULL_NAME, fullName)
                .putString(KEY_RESPONDER_ID, responderId)
                .apply();
    }

    public String getToken() {
        return sharedPreferences.getString(KEY_TOKEN, "");
    }

    public String getUserId() {
        return sharedPreferences.getString(KEY_USER_ID, "");
    }

    public String getFullName() {
        return sharedPreferences.getString(KEY_FULL_NAME, "");
    }

    public String getResponderId() {
        return sharedPreferences.getString(KEY_RESPONDER_ID, "");
    }

    public boolean isLoggedIn() {
        // Token is the reliable indicator of an authenticated admin session.
        // user_id may be empty in some schema flows where extension/responder link is not set yet.
        return !getToken().isEmpty();
    }

    public void clearSession() {
        sharedPreferences.edit().clear().apply();
    }
}
