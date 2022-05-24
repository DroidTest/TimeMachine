/*
 * StockStoreDatabase.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.util;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import android.util.Log;

import net.exclaimindustries.tools.DateTools;

import java.util.Calendar;

/**
 * <p>
 * A <code>StockStoreDatabase</code> object talks to the database to store and
 * retrieve stock prices to and from (respectively) the cache.  It does this via
 * <code>Info</code> bundles, so it will account for the 30W Rule as need be,
 * assuming it was created properly from <code>HashBuilder</code>.
 * </p>
 * 
 * @author Nicholas Killewald
 */
public class StockStoreDatabase {
    private DatabaseHelper mHelper;
    private SQLiteDatabase mDatabase;
    
    private static final String DEBUG_TAG = "StockStoreDatabase";
    
    /** The name of the column for the row's ID. */
    private static final String KEY_STOCKS_ROWID = "_id";
    /** The name of the date column. */
    private static final String KEY_STOCKS_DATE = "date";
    /** The name of the stock value column. */
    private static final String KEY_STOCKS_STOCK = "stock";
    
    /** The name of the column for the row's IDs for hashes. */
    private static final String KEY_HASHES_ROWID = "_id";
    /** The name of the date column for hashes. */
    private static final String KEY_HASHES_DATE = "date";
    /** The name of the column flagging if the 30W rule was in effect here. */
    private static final String KEY_HASHES_30W = "uses30w";
    /** The name of the latitude hashpart column. */
    private static final String KEY_HASHES_LATHASH = "lathash";
    /** The name of the longitude hashpart column. */
    private static final String KEY_HASHES_LONHASH = "lonhash";
    
    private static final String TABLE_STOCKS = "stocks";
    private static final String TABLE_HASHES = "hashes";
    
    /**
     * Implements SQLiteOpenHelper.  Much like Hamburger Helper, this can take
     * a pound of database and turn it into a meal.
     * 
     * @author Nicholas Killewald
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "stockstore";
        private static final int DATABASE_VERSION = 3;

        private static final String CREATE_STOCKS_TABLE =
                "CREATE TABLE " + TABLE_STOCKS
                        + " (" + KEY_STOCKS_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + KEY_STOCKS_DATE + " INTEGER NOT NULL, "
                        + KEY_STOCKS_STOCK + " TEXT NOT NULL);";

        private static final String CREATE_HASHES_TABLE =
                "CREATE TABLE " + TABLE_HASHES
                        + " (" + KEY_HASHES_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + KEY_HASHES_DATE + " INTEGER NOT NULL, "
                        + KEY_HASHES_30W + " INTEGER NOT NULL, "
                        + KEY_HASHES_LATHASH + " REAL NOT NULL, "
                        + KEY_HASHES_LONHASH + " REAL NOT NULL);";

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_STOCKS_TABLE);
            db.execSQL(CREATE_HASHES_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if(oldVersion == 1 || oldVersion == 2) {
                // Versions 1 and 2 only had one table, named "stocks".
                db.execSQL("DROP TABLE IF EXISTS stocks");
                db.execSQL(CREATE_STOCKS_TABLE);
                db.execSQL(CREATE_HASHES_TABLE);
            }
        }
    }

    /**
     * Initializes the store.  That is to say, opens the database for action.
     * Or creates it and THEN opens it.  Or just gives up and throws an
     * exception.
     *
     * @param c the Context to use to make the database helper
     * @return this (self reference, allowing this to be chained in an
     *         initialization call)
     * @throws SQLException if the database could be neither opened or created
     */
    public StockStoreDatabase init(@NonNull Context c) throws SQLException {
        mHelper = new DatabaseHelper(c);
        mDatabase = mHelper.getWritableDatabase();
        return this;
    }
    
    /**
     * Finishes up.  In this case, closes the database.
     */
    public void finish() {
        mHelper.close();
    }
    
    /**
     * Stores a bundle of Info into the database.  That is, store a new entry in
     * the hashes table.  It is presumed this has nothing to do with the actual
     * stock value.  When retrieved later, this will preserve the fractional
     * parts of the coordinates (that is, the hash part).
     * 
     * @param i the aforementioned bundle of Info to be stored into the database
     */
    public void storeInfo(Info i) {
        synchronized(this) {
            // Fortunately, there's a handy ContentValues object for this sort
            // of thing.  I mean, we COULD do manual SQLite calls, but why
            // bother?
            
            // But first!  First we need to know if this already exists.  If it
            // does, return a -1.
            // TODO: No, wrong.  I need a better mechanism for that.
            if(getInfo(i.getCalendar(), i.getGraticule()) != null) {
                Log.v(DEBUG_TAG, "Info already exists for that data, ignoring...");
                return;
            }
            
            ContentValues toGo = new ContentValues();
            Calendar cal = i.getCalendar();
            toGo.put(KEY_HASHES_DATE, DateTools.getDateString(cal));
            toGo.put(KEY_HASHES_30W, i.uses30WRule());
            toGo.put(KEY_HASHES_LATHASH, i.getLatitudeHash());
            toGo.put(KEY_HASHES_LONHASH, i.getLongitudeHash());
            
            Log.v(DEBUG_TAG, "NOW STORING TO HASHES " + DateTools.getDateString(cal)
                    + (i.uses30WRule() ? " (30W)" : "") + " : "
                    + i.getLatitudeHash() + "," + i.getLongitudeHash());

            mDatabase.insert(TABLE_HASHES, null, toGo);
        }
    }
    
    /**
     * Stores a stock value in the stock table.  Presumably, the given calendar
     * value is already adjusted for weekends and 30W (that is, this is the raw
     * stock value for that date).
     * 
     * @param cal the date of the stock
     * @param stock the stock itself, as a string
     */
    public void storeStock(Calendar cal, String stock) {
        synchronized(this) {
            // First, check over the database to make sure it doesn't already
            // exist.
            if(getStock(cal) != null) {
                Log.v(DEBUG_TAG, "Stock price already exists in database for " + DateTools.getDateString(cal) + ", ignoring...");
                return;
            }
            
            // Otherwise, store away!
            ContentValues toGo = new ContentValues();
            toGo.put(KEY_STOCKS_DATE, DateTools.getDateString(cal));
            toGo.put(KEY_STOCKS_STOCK, stock);
            
            Log.v(DEBUG_TAG, "NOW STORING TO STOCKS " + DateTools.getDateString(cal)
                    + " : " + stock);

            mDatabase.insert(TABLE_STOCKS, null, toGo);
        }
    }
    
    /**
     * Retrieves enough data from the database to construct an Info bundle, if
     * such data exists.  If not, returns null instead.
     * 
     * @param c Calendar containing the date to retrieve (this should NOT be
     *          adjusted for the 30W Rule)
     * @param g Graticule to use to determine if the 30W Rule is in effect and
     *          to create the new Info bundle with
     * @return Info bundle you're looking for, or null if the database doesn't
     *         have the data you want
     */
    public Info getInfo(Calendar c, Graticule g) {
        synchronized(this) {
            Log.v(DEBUG_TAG, "Querying the hashes database...");
            // First, adjust the calendar if we need to.
            Info toReturn = null;
            
            // Now, to the database!
            Cursor cursor = mDatabase.query(TABLE_HASHES, new String[] {KEY_HASHES_LATHASH, KEY_HASHES_LONHASH},
                    KEY_HASHES_DATE + " = " + DateTools.getDateString(c) + " AND " + KEY_HASHES_30W + " = "
                    + ((g == null || g.uses30WRule()) ? "1" : "0"),
                    null, null, null, null);
            
            if(cursor == null) {
                // If a problem happens, assume there's no stock to get.
                Log.w(DEBUG_TAG, "HEY!  The cursor returned from the query was null!");
                return null;
            } else if(cursor.getCount() == 0) {
                // If nothing resulted from this, the stock doesn't exist in the
                // cache.
                Log.v(DEBUG_TAG, "Info doesn't exist in database");
            } else {
                // Otherwise, grab the first one we come across.
                if(!cursor.moveToFirst()) return null;
                
                double latHash = cursor.getDouble(0);
                double lonHash = cursor.getDouble(1);
                Log.v(DEBUG_TAG, "Info found -- Today's lucky numbers are " + latHash + "," + lonHash);
                
                // Get the destination set...
                if(g != null) {
                    double lat = (g.getLatitude() + latHash) * (g.isSouth() ? -1 : 1);
                    double lon = (g.getLongitude() + lonHash) * (g.isWest() ? -1 : 1);
                    
                    toReturn = new Info(lat, lon, g, c);
                } else {
                    toReturn = new Info(latHash, lonHash, null, c);
                }
            }
            
            cursor.close();
            return toReturn;
        }
    }
    
    /**
     * Retrieves a stock value from the database for the given date.  This date
     * should already be adjusted for weekends and such.
     * 
     * @param cal already-adjusted date for which to get a stock
     * @return the String representation of the stock, or null if none is stored 
     */
    public String getStock(Calendar cal) {
        synchronized(this) {
            Log.v(DEBUG_TAG, "Querying the stock database...");
            
            String toReturn = null;
            
            // Go!
            Cursor cursor = mDatabase.query(TABLE_STOCKS, new String[] {KEY_STOCKS_STOCK},
                    KEY_STOCKS_DATE + " = " + DateTools.getDateString(cal),
                    null, null, null, null);
            
            // And now the check...
            if(cursor == null) {
                // If a problem happens, assume there's no stock to get.
                Log.w(DEBUG_TAG, "HEY!  The cursor returned from the query was null!");
                return null;
            } else if(cursor.getCount() == 0) {
                // If nothing resulted from this, the stock doesn't exist in the
                // cache.
                Log.v(DEBUG_TAG, "Stock doesn't exist in database");
            } else {
                // Otherwise, grab the first one we come across.
                if(!cursor.moveToFirst()) return null;
                
                toReturn = cursor.getString(0);
                Log.v(DEBUG_TAG, "Stock found -- Today's lucky number is " + toReturn);
            }
            
            cursor.close();
            return toReturn;
        }
    }
    
    /**
     * Performs cache cleanup.  This involves pruning the cache down to however
     * many entries should be the max.
     *
     * @param c Context to use to get preferences and such
     */
    public void cleanup(@NonNull Context c) {
        synchronized(this) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
            
            Log.v(DEBUG_TAG, "Pruning database...");
            try {
                // Presumably, initPrefs was already run from the GeohashDroid
                // class.  Thus, if the pref doesn't exist at this point or
                // isn't parseable into an int, we can quite justifiably spaz
                // out.
                int max = Integer.parseInt(prefs.getString(GHDConstants.PREF_STOCK_CACHE_SIZE, "15"));
                
                // Step one: Get the highest row ID.  I could probably ram this
                // all into one big monolithic SQL statement, but that would get
                // more than a bit unreadable.  Also note very carefully, this
                // entire method depends on there being no holes in the rowids.
                // "SELECT _rowid FROM stocks ORDER BY _rowid DESC LIMIT 1;"
                Cursor cursor = mDatabase.query(TABLE_STOCKS, new String[] {KEY_STOCKS_ROWID},
                        null, null, null, null, KEY_STOCKS_ROWID + " DESC", "1");
                
                cursor.moveToFirst();
                int highest = cursor.getInt(0);
                cursor.close();
                
                // Step two: Delete anything in the database older than the
                // highest minus the max.
                // "DELETE FROM stocks WHERE _rowid < (highest - max);"
                int deleted = mDatabase.delete(TABLE_STOCKS, KEY_STOCKS_ROWID + " <= " + (highest - max), null);

                Log.v(DEBUG_TAG, "Stock rows deleted: " + deleted);
                
                // Now, do all that again, but for hashes.
                
                cursor = mDatabase.query(TABLE_HASHES, new String[] {KEY_HASHES_ROWID},
                        null, null, null, null, KEY_HASHES_ROWID + " DESC", "1");
                
                cursor.moveToFirst();
                highest = cursor.getInt(0);
                cursor.close();
                
                deleted = mDatabase.delete(TABLE_HASHES, KEY_HASHES_ROWID + " <= " + (highest - max), null);
                
                Log.v(DEBUG_TAG, "Info rows deleted: " + deleted);
            } catch (Exception e) {
                // If something went wrong, let it go.
                Log.w(DEBUG_TAG, "HEY!  Couldn't prune the stock cache database: " + e.toString());
            }
        }
    }
    
    /**
     * Erases everything from the stock cache database.  This is really only to
     * be used if something's gone horribly wrong.
     */
    public boolean deleteCache() {
        synchronized(this) {
            try {
                Log.v(DEBUG_TAG, "Emptying the stock cache...");
                // KABOOM!
                mDatabase.delete(TABLE_STOCKS, null, null);
                mDatabase.delete(TABLE_HASHES, null, null);
                return true;
            } catch (Exception e) {
                // If something went wrong, let it go.
                Log.w(DEBUG_TAG, "HEY!  Couldn't erase the entire stock cache database: " + e.toString());
                return false;
            }
        }
    }
}
