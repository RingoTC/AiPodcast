package com.example.aipodcast.database.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.aipodcast.database.DatabaseHelper;
import com.example.aipodcast.model.NewsArticle;
import com.example.aipodcast.model.NewsCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of the NewsDao interface
 * Provides concrete database operations for news articles
 */
public class SqliteNewsDao implements NewsDao {
    private static final String TAG = "SqliteNewsDao";
    
    private final DatabaseHelper dbHelper;
    
    /**
     * Constructor
     * 
     * @param context Application context
     */
    public SqliteNewsDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }
    
    @Override
    public long insertArticle(NewsArticle article, NewsCategory category) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long id = -1;
        
        try {
            ContentValues values = createContentValues(article, category);
            id = db.insertWithOnConflict(
                    DatabaseHelper.TABLE_NEWS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            Log.e(TAG, "Error inserting article: " + e.getMessage());
        }
        
        return id;
    }
    
    @Override
    public int insertArticles(List<NewsArticle> articles, NewsCategory category) {
        if (articles == null || articles.isEmpty()) {
            return 0;
        }
        
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count = 0;
        
        try {
            db.beginTransaction();
            
            for (NewsArticle article : articles) {
                ContentValues values = createContentValues(article, category);
                long id = db.insertWithOnConflict(
                        DatabaseHelper.TABLE_NEWS,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE);
                
                if (id != -1) {
                    count++;
                }
            }
            
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error batch inserting articles: " + e.getMessage());
        } finally {
            db.endTransaction();
        }
        
        return count;
    }
    
    @Override
    public List<NewsArticle> getArticlesByCategory(NewsCategory category) {
        List<NewsArticle> articles = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        String selection = DatabaseHelper.COLUMN_CATEGORY + " = ?";
        String[] selectionArgs = {category.getValue()};
        String orderBy = DatabaseHelper.COLUMN_PUBLISHED_DATE + " DESC";
        
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_NEWS,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    orderBy);
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    NewsArticle article = cursorToArticle(cursor);
                    articles.add(article);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting articles by category: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return articles;
    }
    
    @Override
    public NewsArticle getArticleByUrl(String url) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        NewsArticle article = null;
        
        String selection = DatabaseHelper.COLUMN_URL + " = ?";
        String[] selectionArgs = {url};
        
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseHelper.TABLE_NEWS,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null,
                    "1");
            
            if (cursor != null && cursor.moveToFirst()) {
                article = cursorToArticle(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting article by URL: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return article;
    }
    
    @Override
    public int updateArticle(NewsArticle article) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rowsAffected = 0;
        
        try {
            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COLUMN_TITLE, article.getTitle());
            values.put(DatabaseHelper.COLUMN_ABSTRACT, article.getAbstract());
            values.put(DatabaseHelper.COLUMN_SECTION, article.getSection());
            values.put(DatabaseHelper.COLUMN_PUBLISHED_DATE, article.getPublishedDate());
            values.put(DatabaseHelper.COLUMN_UPDATED_AT, System.currentTimeMillis());
            
            String whereClause = DatabaseHelper.COLUMN_URL + " = ?";
            String[] whereArgs = {article.getUrl()};
            
            rowsAffected = db.update(
                    DatabaseHelper.TABLE_NEWS,
                    values,
                    whereClause,
                    whereArgs);
        } catch (Exception e) {
            Log.e(TAG, "Error updating article: " + e.getMessage());
        }
        
        return rowsAffected;
    }
    
    @Override
    public int deleteOldArticles(NewsCategory category, int keepLatestCount) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long rowsDeleted = 0;
        
        try {
            // Select articles to keep (LIMIT won't work in DELETE query in SQLite)
            String keepQuery = "SELECT " + DatabaseHelper.COLUMN_ID + 
                    " FROM " + DatabaseHelper.TABLE_NEWS + 
                    " WHERE " + DatabaseHelper.COLUMN_CATEGORY + " = ?" +
                    " ORDER BY " + DatabaseHelper.COLUMN_PUBLISHED_DATE + " DESC" +
                    " LIMIT " + keepLatestCount;
            
            // Delete articles not in the keep list
            String deleteQuery = "DELETE FROM " + DatabaseHelper.TABLE_NEWS + 
                    " WHERE " + DatabaseHelper.COLUMN_CATEGORY + " = ?" +
                    " AND " + DatabaseHelper.COLUMN_ID + " NOT IN (" + keepQuery + ")";
            
            String[] args = {category.getValue(), category.getValue()};
            db.execSQL(deleteQuery, args);
            
            // Get count of deleted rows (SQLite change count is only for the last statement)
            rowsDeleted = db.compileStatement("SELECT changes()").simpleQueryForLong();
        } catch (Exception e) {
            Log.e(TAG, "Error deleting old articles: " + e.getMessage());
        }
        
        return (int) rowsDeleted;
    }
    
    @Override
    public int deleteArticlesByCategory(NewsCategory category) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rowsDeleted = 0;
        
        try {
            String whereClause = DatabaseHelper.COLUMN_CATEGORY + " = ?";
            String[] whereArgs = {category.getValue()};
            
            rowsDeleted = db.delete(
                    DatabaseHelper.TABLE_NEWS,
                    whereClause,
                    whereArgs);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting articles by category: " + e.getMessage());
        }
        
        return rowsDeleted;
    }
    
    @Override
    public boolean hasCachedArticles(NewsCategory category) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        boolean hasArticles = false;
        
        String query = "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_NEWS + 
                " WHERE " + DatabaseHelper.COLUMN_CATEGORY + " = ?";
        String[] args = {category.getValue()};
        
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, args);
            if (cursor != null && cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                hasArticles = count > 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking cached articles: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return hasArticles;
    }
    
    @Override
    public long getLastUpdateTime(NewsCategory category) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        long timestamp = 0;
        
        String query = "SELECT MAX(" + DatabaseHelper.COLUMN_UPDATED_AT + ") FROM " + 
                DatabaseHelper.TABLE_NEWS + " WHERE " + DatabaseHelper.COLUMN_CATEGORY + " = ?";
        String[] args = {category.getValue()};
        
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, args);
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                timestamp = cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting last update time: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return timestamp;
    }
    
    /**
     * Helper method to create ContentValues from a NewsArticle
     */
    private ContentValues createContentValues(NewsArticle article, NewsCategory category) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_TITLE, article.getTitle());
        values.put(DatabaseHelper.COLUMN_ABSTRACT, article.getAbstract());
        values.put(DatabaseHelper.COLUMN_URL, article.getUrl());
        values.put(DatabaseHelper.COLUMN_SECTION, article.getSection());
        values.put(DatabaseHelper.COLUMN_PUBLISHED_DATE, article.getPublishedDate());
        values.put(DatabaseHelper.COLUMN_CATEGORY, category.getValue());
        values.put(DatabaseHelper.COLUMN_UPDATED_AT, System.currentTimeMillis());
        return values;
    }
    
    /**
     * Helper method to convert a cursor to a NewsArticle
     */
    private NewsArticle cursorToArticle(Cursor cursor) {
        String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TITLE));
        String abstract_ = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ABSTRACT));
        String url = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_URL));
        String section = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_SECTION));
        String publishedDate = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PUBLISHED_DATE));
        
        return new NewsArticle(title, abstract_, url, section, publishedDate);
    }
} 