package com.example.aipodcast.service;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Patterns;
import com.example.aipodcast.database.DatabaseHelper;
import com.example.aipodcast.database.dao.SqliteUserDao;
import com.example.aipodcast.database.dao.UserDao;
import com.example.aipodcast.model.User;
public class AuthService {
    private static final String TAG = "AuthService";
    private static final String PREF_NAME = "auth_prefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private final UserDao userDao;
    private final SharedPreferences preferences;
    private static AuthService instance;
    private AuthService(Context context) {
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
        this.userDao = new SqliteUserDao(dbHelper);
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    public static synchronized AuthService getInstance(Context context) {
        if (instance == null) {
            instance = new AuthService(context.getApplicationContext());
        }
        return instance;
    }
    public AuthResult register(String username, String password, String email) {
        if (username == null || username.trim().isEmpty()) {
            return new AuthResult(false, "Username is required");
        }
        if (password == null || password.length() < 6) {
            return new AuthResult(false, "Password must be at least 6 characters");
        }
        if (email == null || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return new AuthResult(false, "Valid email is required");
        }
        if (userDao.findByUsername(username) != null) {
            return new AuthResult(false, "Username already exists");
        }
        if (userDao.findByEmail(email) != null) {
            return new AuthResult(false, "Email already registered");
        }
        User user = new User(username, password, email);
        long userId = userDao.createUser(user);
        if (userId != -1) {
            saveUserSession(userId, username);
            return new AuthResult(true, "Registration successful");
        } else {
            return new AuthResult(false, "Registration failed");
        }
    }
    public AuthResult login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            return new AuthResult(false, "Username is required");
        }
        if (password == null || password.trim().isEmpty()) {
            return new AuthResult(false, "Password is required");
        }
        User user = userDao.authenticate(username, password);
        if (user != null) {
            saveUserSession(user.getId(), user.getUsername());
            return new AuthResult(true, "Login successful");
        } else {
            return new AuthResult(false, "Invalid username or password");
        }
    }
    public void logout() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
    }
    public boolean isLoggedIn() {
        return preferences.contains(KEY_USER_ID);
    }
    public long getCurrentUserId() {
        return preferences.getLong(KEY_USER_ID, -1);
    }
    public String getCurrentUsername() {
        return preferences.getString(KEY_USERNAME, null);
    }
    private void saveUserSession(long userId, String username) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(KEY_USER_ID, userId);
        editor.putString(KEY_USERNAME, username);
        editor.apply();
    }
    public static class AuthResult {
        private final boolean success;
        private final String message;
        public AuthResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        public boolean isSuccess() {
            return success;
        }
        public String getMessage() {
            return message;
        }
    }
}