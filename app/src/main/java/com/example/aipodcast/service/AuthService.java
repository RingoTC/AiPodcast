package com.example.aipodcast.service;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;
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
    private final Context context;
    private static AuthService instance;
    private AuthService(Context context) {
        this.context = context.getApplicationContext();
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
        this.userDao = new SqliteUserDao(dbHelper);
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        if (userDao.findByUsername("test123") == null) {
            User defaultUser = new User("test123", "test123", "test123@example.com");
            userDao.createUser(defaultUser);
            Log.d(TAG, "Default user created successfully");
        }
    }
    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
    public static synchronized AuthService getInstance(Context context) {
        if (instance == null) {
            instance = new AuthService(context.getApplicationContext());
        }
        return instance;
    }
    public boolean register(String username, String password, String email) {
        if (username == null || username.trim().isEmpty()) {
            showToast("Username is required");
            return false;
        }
        if (password == null || password.length() < 6) {
            showToast("Password must be at least 6 characters");
            return false;
        }
        if (email == null || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast("Valid email is required");
            return false;
        }
        if (userDao.findByUsername(username) != null) {
            showToast("Username already exists");
            return false;
        }
        if (userDao.findByEmail(email) != null) {
            showToast("Email already registered");
            return false;
        }
        User user = new User(username, password, email);
        long userId = userDao.createUser(user);
        if (userId != -1) {
            saveUserSession(userId, username);
            showToast("Registration successful");
            return true;
        } else {
            showToast("Registration failed");
            return false;
        }
    }
    public boolean login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            showToast("Username is required");
            return false;
        }
        if (password == null || password.trim().isEmpty()) {
            showToast("Password is required");
            return false;
        }
        User user = userDao.authenticate(username, password);
        if (user != null) {
            saveUserSession(user.getId(), user.getUsername());
            showToast("Login successful");
            return true;
        } else {
            showToast("Invalid username or password");
            return false;
        }
    }
    public void logout() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
        showToast("Logged out successfully");
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