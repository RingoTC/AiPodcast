package com.example.aipodcast.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * SQLite database helper class for the AiPodcast application.
 * Manages database creation, version management, and provides access to the database.
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    
    // Database Version
    private static final int DATABASE_VERSION = 2;
    
    // Database Name
    private static final String DATABASE_NAME = "news_db";
    
    // Table Names
    public static final String TABLE_NEWS = "news_articles";
    
    // Common column names
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_CREATED_AT = "created_at";
    public static final String COLUMN_UPDATED_AT = "updated_at";
    
    // NEWS_ARTICLES Table - column names
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_ABSTRACT = "abstract";
    public static final String COLUMN_URL = "url";
    public static final String COLUMN_SECTION = "section";
    public static final String COLUMN_PUBLISHED_DATE = "published_date";
    public static final String COLUMN_KEYWORD = "keyword";
    
    // Create Table Statements
    // News table create statement
    private static final String CREATE_TABLE_NEWS = "CREATE TABLE " + TABLE_NEWS + "("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_TITLE + " TEXT NOT NULL,"
            + COLUMN_ABSTRACT + " TEXT,"
            + COLUMN_URL + " TEXT UNIQUE NOT NULL,"
            + COLUMN_SECTION + " TEXT,"
            + COLUMN_PUBLISHED_DATE + " TEXT,"
            + COLUMN_KEYWORD + " TEXT,"
            + COLUMN_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
            + COLUMN_UPDATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP"
            + ")";
            
    // Create Index for keyword search
    private static final String CREATE_INDEX_KEYWORDS = "CREATE INDEX idx_keywords ON " 
            + TABLE_NEWS + "(" + COLUMN_KEYWORD + ")";
    
    // Singleton instance
    private static DatabaseHelper sInstance;
    
    /**
     * Get singleton instance of DatabaseHelper
     * 
     * @param context Application context
     * @return DatabaseHelper singleton instance
     */
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DatabaseHelper(context.getApplicationContext());
        }
        return sInstance;
    }
    
    /**
     * Private constructor to prevent direct instantiation.
     * Use getInstance() instead.
     */
    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Creating required tables and indexes
        db.execSQL(CREATE_TABLE_NEWS);
        db.execSQL(CREATE_INDEX_KEYWORDS);
        Log.i(TAG, "Database tables and indexes created");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        
        if (oldVersion < 2) {
            // Add keyword column and index
            db.execSQL("ALTER TABLE " + TABLE_NEWS + " ADD COLUMN " + COLUMN_KEYWORD + " TEXT");
            db.execSQL(CREATE_INDEX_KEYWORDS);
        }
    }
    
    /**
     * Close the database connection
     */
    public void closeDB() {
        SQLiteDatabase db = getReadableDatabase();
        if (db != null && db.isOpen()) {
            db.close();
        }
    }
    
    /**
     * Delete all tables and recreate them
     */
    public void resetDatabase() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NEWS);
        onCreate(db);
    }
} 