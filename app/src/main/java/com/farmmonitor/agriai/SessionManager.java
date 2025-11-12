package com.farmmonitor.agriai;

import android.content.Context;
import android.content.SharedPreferences;


public class SessionManager {

    private static final String PREF_NAME = "AgroAiUserSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_CITY = "city";
    private static final String KEY_PASSWORD = "password";

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    // ✅ Constructor — takes Context (Activity or App context)
    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    // ✅ Save user data after successful login or registration
    public void saveUser(User user) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putInt(KEY_ID, user.getId());
        editor.putString(KEY_NAME, user.getName());
        editor.putString(KEY_EMAIL, user.getEmail());
        editor.putString(KEY_CITY, user.getCity());
        editor.putString(KEY_PASSWORD, user.getPassword());
        editor.apply();
    }

    // ✅ Check if a user is logged in
    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    // ✅ Retrieve user data
    public User getUser() {
        User user = new User();
        user.setId(pref.getInt(KEY_ID, 0));
        user.setName(pref.getString(KEY_NAME, ""));
        user.setEmail(pref.getString(KEY_EMAIL, ""));
        user.setCity(pref.getString(KEY_CITY, ""));
        user.setPassword(pref.getString(KEY_PASSWORD, ""));
        return user;
    }

    // ✅ Clear all session data (for logout)
    public void logout() {
        editor.clear();
        editor.apply();
    }
    // Add this at the end of SessionManager class
    public void createLoginSession(User user) {
        saveUser(user);
    }

}
