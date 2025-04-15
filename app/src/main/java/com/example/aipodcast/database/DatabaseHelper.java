package com.example.aipodcast.database;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    private static final int DATABASE_VERSION = 3;
    private static final String DATABASE_NAME = "news_db";
    public static final String TABLE_NEWS = "news_articles";
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_CREATED_AT = "created_at";
    public static final String COLUMN_UPDATED_AT = "updated_at";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_ABSTRACT = "abstract";
    public static final String COLUMN_URL = "url";
    public static final String COLUMN_SECTION = "section";
    public static final String COLUMN_PUBLISHED_DATE = "published_date";
    public static final String COLUMN_KEYWORD = "keyword";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_PASSWORD = "password";
    public static final String COLUMN_EMAIL = "email";
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
    private static final String CREATE_INDEX_KEYWORDS = "CREATE INDEX idx_keywords ON " 
            + TABLE_NEWS + "(" + COLUMN_KEYWORD + ")";
    private static final String CREATE_TABLE_USERS = "CREATE TABLE " + TABLE_USERS + "("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_USERNAME + " TEXT UNIQUE NOT NULL,"
            + COLUMN_PASSWORD + " TEXT NOT NULL,"
            + COLUMN_EMAIL + " TEXT UNIQUE NOT NULL,"
            + COLUMN_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP"
            + ")";
    private static DatabaseHelper sInstance;
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DatabaseHelper(context.getApplicationContext());
        }
        return sInstance;
    }
    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_NEWS);
        db.execSQL(CREATE_INDEX_KEYWORDS);
        db.execSQL(CREATE_TABLE_USERS);
        Log.i(TAG, "Database tables and indexes created");
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NEWS + " ADD COLUMN " + COLUMN_KEYWORD + " TEXT");
            db.execSQL(CREATE_INDEX_KEYWORDS);
        }
        if (oldVersion < 3) {
            db.execSQL(CREATE_TABLE_USERS);
        }
    }
    public void closeDB() {
        SQLiteDatabase db = getReadableDatabase();
        if (db != null && db.isOpen()) {
            db.close();
        }
    }
    public void resetDatabase() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NEWS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }
}