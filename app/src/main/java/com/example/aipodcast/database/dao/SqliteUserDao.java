package com.example.aipodcast.database.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.example.aipodcast.database.DatabaseHelper;
import com.example.aipodcast.model.User;

/**
 * SQLite implementation of UserDao interface.
 */
public class SqliteUserDao implements UserDao {
    private static final String TAG = "SqliteUserDao";
    private final DatabaseHelper dbHelper;

    public SqliteUserDao(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    @Override
    public long createUser(User user) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        try {
            values.put(DatabaseHelper.COLUMN_USERNAME, user.getUsername());
            values.put(DatabaseHelper.COLUMN_PASSWORD, hashPassword(user.getPassword()));
            values.put(DatabaseHelper.COLUMN_EMAIL, user.getEmail());

            long id = db.insertOrThrow(DatabaseHelper.TABLE_USERS, null, values);
            if (id != -1) {
                user.setId(id);
            }
            return id;
        } catch (Exception e) {
            Log.e(TAG, "Error creating user: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public User findByUsername(String username) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        User user = null;

        try (Cursor cursor = db.query(DatabaseHelper.TABLE_USERS,
                null,
                DatabaseHelper.COLUMN_USERNAME + "=?",
                new String[]{username},
                null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                user = cursorToUser(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding user by username: " + e.getMessage());
        }

        return user;
    }

    @Override
    public User findByEmail(String email) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        User user = null;

        try (Cursor cursor = db.query(DatabaseHelper.TABLE_USERS,
                null,
                DatabaseHelper.COLUMN_EMAIL + "=?",
                new String[]{email},
                null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                user = cursorToUser(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding user by email: " + e.getMessage());
        }

        return user;
    }

    @Override
    public User authenticate(String username, String password) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        User user = null;

        try (Cursor cursor = db.query(DatabaseHelper.TABLE_USERS,
                null,
                DatabaseHelper.COLUMN_USERNAME + "=? AND " + DatabaseHelper.COLUMN_PASSWORD + "=?",
                new String[]{username, hashPassword(password)},
                null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                user = cursorToUser(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error authenticating user: " + e.getMessage());
        }

        return user;
    }

    @Override
    public boolean updateUser(User user) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        try {
            values.put(DatabaseHelper.COLUMN_USERNAME, user.getUsername());
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                values.put(DatabaseHelper.COLUMN_PASSWORD, hashPassword(user.getPassword()));
            }
            values.put(DatabaseHelper.COLUMN_EMAIL, user.getEmail());

            int rowsAffected = db.update(DatabaseHelper.TABLE_USERS,
                    values,
                    DatabaseHelper.COLUMN_ID + "=?",
                    new String[]{String.valueOf(user.getId())});

            return rowsAffected > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error updating user: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteUser(long userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        try {
            int rowsAffected = db.delete(DatabaseHelper.TABLE_USERS,
                    DatabaseHelper.COLUMN_ID + "=?",
                    new String[]{String.valueOf(userId)});

            return rowsAffected > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting user: " + e.getMessage());
            return false;
        }
    }

    private User cursorToUser(Cursor cursor) {
        User user = new User();
        user.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID)));
        user.setUsername(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USERNAME)));
        user.setEmail(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_EMAIL)));
        user.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CREATED_AT)));
        // Don't set password for security reasons
        return user;
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error hashing password: " + e.getMessage());
            throw new RuntimeException("Failed to hash password", e);
        }
    }
}
