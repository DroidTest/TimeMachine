/*
 * HashBuilder.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.util;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import net.exclaimindustries.tools.DateTools;
import net.exclaimindustries.tools.HexFraction;
import net.exclaimindustries.tools.MD5Tools;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.security.InvalidParameterException;
import java.util.Calendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClients;

/**
 * <p>
 * The <code>HashBuilder</code> class encompasses a whole bunch of static
 * methods to grab and store the day's DJIA and calculate the hash, given a
 * <code>Graticule</code> object.
 * </p>
 * 
 * <p>
 * This also encompasses <code>StockRunner</code>, which goes out to the web
 * to get the current stock data.  <code>HashBuilder</code> itself, though,
 * does the hash calculations.
 * </p>
 * 
 * <p>
 * This implementation uses the Crox site to get the DJIA, falling back to
 * the peeron.com site if Crox can't figure it out (upstream faults, server
 * failure, etc).
 * </p>
 * 
 * @author Nicholas Killewald
 */
public class HashBuilder {
    
    // This is used as the lock to prevent multiple requests from happening at
    // once.  This really shouldn't ever happen, but just in case.
    private static final Object locker = new Object();
    
    private static final String DEBUG_TAG = "HashBuilder";
    
    private static StockStoreDatabase mStore;
    // This set allows for quick reloading of the most recent stock and hash in
    // a given instance of the program, bypassing the SQLite database, as well
    // as allow for a small cache even if the SQLite database is turned off by
    // preferences.
    private static Info mLastInfo;
    private static Info mTwoInfosAgo;

    /**
     * <code>StockRunner</code> is what fetches the stocks.  It spawns off
     * threads to fetch data, and once {@link #runStock()} returns, you'll be
     * able to pull the data and act on it.  Once it has the data, it'll go back
     * to the static methods of HashBuilder to make the Info bundle and put it
     * in the cache.
     */
    public static class StockRunner {
        private static final String DEBUG_TAG = "StockRunner";

        // In milliseconds, remember.
        private static final int CONNECTION_TIMEOUT_SEC = 10;
        private static final int CONNECTION_TIMEOUT_MS = CONNECTION_TIMEOUT_SEC * 1000;

        /**
         * This is busy, either with getting the stock price or working out
         * the hash.
         */
        public static final int BUSY = 0;
        /**
         * This hasn't been started yet and has no Info object handy.
         */
        public static final int IDLE = 1;
        /**
         * This is done, and its last action was successful, in that it got
         * stock data and calculated a new hash.  If this is returned from
         * getStatus, you can get a fresh Info object.
         */
        public static final int ALL_OKAY = 2;
        /**
         * The last request couldn't be met because the stock value wasn't
         * posted for the given day yet.
         */
        public static final int ERROR_NOT_POSTED = 3;
        /**
         * The last request couldn't be met because of some server error.
         */
        public static final int ERROR_SERVER = 4;

        private Context mContext;
        private Calendar mCal;
        private Graticule mGrat;
        private HttpGet mRequest;
        private int mStatus;
        private Info mLastObject;

        // This may be expanded later to allow a user-definable list, hence why
        // it doesn't follow the usual naming conventions I use.  Of course, in
        // THAT case, we'd need to make it not be a raw array.  The general form
        // is that %Y is the four-digit year, %m is the zero-padded month, and
        // %d is the zero-padded date.
        private final static String[] mServers = { "http://irc.peeron.com/xkcd/map/data/%Y/%m/%d",
                "http://geo.crox.net/djia/%Y/%m/%d" };

        private StockRunner(@NonNull Context con, @NonNull Calendar c, @Nullable Graticule g) {
            mContext = con;
            mCal = c;
            mGrat = g;
            mStatus = IDLE;
        }

        /**
          <p>
         * Runs the stock fetch in the current thread.  And by "current thread",
         * I mean don't use this if you're in the main thread.  It's stupid and
         * wrong to put network I/O on the main thread.
         * </p>
         *
         * <p>
         * When this method returns, the cache will have been updated, if
         * appropriate.  You can retrieve the status and data from
         * {@link #getStatus()} and {@link #getLastResultObject()}.
         * </p>
         */
        public void runStock() {
            Log.d(DEBUG_TAG, "Now starting a StockRunner for " + DateTools.getHyphenatedDateString(mCal) + " at " + mGrat.getLatitudeString(false) + " " + mGrat.getLongitudeString(false) + "...");
            Info toReturn;
            String stock;
            
            mStatus = BUSY;
            
            // First, we need to adjust the calendar in the event we're in the
            // range of the 30W rule.  To that end, sCal is for stock calendar.
            Calendar sCal = Info.makeAdjustedCalendar(mCal, mGrat);
            
            // Grab a lock on our lock object.
            synchronized(locker) {
                // First, if this exists in the cache, use it instead of going
                // off to the internet.  This method uses the ACTUAL date, so
                // we can ignore sCal for now.
                toReturn = getStoredInfo(mContext, mCal, mGrat);
                if(toReturn != null) {
                    // Hey, whadya know, we've got something!  Send this data
                    // back to the Handler and return!
                    Log.d(DEBUG_TAG, "Found it in the cache!");
                    mStatus = ALL_OKAY;
                    sendMessage(toReturn);
                    return;
                }
                
                // If that failed, we need a stock price.  First, check to see
                // if it's in the database.  
                stock = getStoredStock(mContext, sCal);
                
                // If we found something, great!  Let's move on!
                if(stock == null) {
                    // Otherwise, we need to start heading off to the net.
                    mStatus = BUSY;
                    try {
                        stock = fetchStock(sCal);
                        // If this didn't throw an exception AND it's not blank,
                        // stash it in the database.
                        if(stock.trim().length() != 0)
                            storeStock(mContext, sCal, stock);
                    } catch (FileNotFoundException fnfe) {
                        // If we got a 404, assume it's not posted yet.
                        mStatus = ERROR_NOT_POSTED;
                        sendMessage(createInvalidInfo(mCal, mGrat));
                        return;
                    } catch (IOException ioe) {
                        // If we got anything else, assume a problem.
                        mStatus = ERROR_SERVER;
                        sendMessage(createInvalidInfo(mCal, mGrat));
                        return;
                    }
                }
            }

            // We assemble an Info object and get ready to return it.  This uses
            // the REAL date so we display the right thing on the detail screen
            // (or anywhere else; the point is, we can report to the user if
            // they're in the influence of the 30W Rule).
            toReturn = createInfo(mCal, stock, mGrat);
                
            // Good!  Now, we can stash this away in the database for later.
            storeInfo(mContext, toReturn);
            
            // And we're done!
            mStatus = ALL_OKAY;
            sendMessage(toReturn);
        }
        
        private void sendMessage(@NonNull Info toReturn) {
            mLastObject = toReturn;
        }
        
        /**
         * Returns the last result Info created from this StockRunner.  This is
         * used when the result comes when no handler is defined and it needs to
         * be pulled out.  This may be null.  Always remember to check the 
         * status first and ONLY do this if an ALL_OKAY is returned.
         * 
         * @return the last Info created from this StockRunner (may be null)
         */
        @Nullable
        public Info getLastResultObject() {
            return mLastObject;
        }

        @NonNull
        private String fetchStock(@NonNull Calendar sCal) throws IOException {
            // Now, generate a string for the URL.
            String sMonthStr = String.format(Locale.US, "%02d", sCal.get(Calendar.MONTH) + 1);
            String sDayStr = String.format(Locale.US, "%02d", sCal.get(Calendar.DAY_OF_MONTH));

            // Good, good! Now, to the web!  Go through our list of sites in
            // order until we find an answer, we bottom out, or we abort.  In
            // terms of what we report to the user, "Server error" is lowest-
            // priority, with "Stock not posted" rating above it.  That is to
            // say, if one server reports and error but another one explicitly
            // tells us the stock wasn't found, the latter is what we use.  Of
            // course, if we get an abort request, that takes absolute
            // precedence.
            int curStatus = ERROR_SERVER;
            String result = "";

            for(String s : mServers) {
                // Do all our substitutions...
                String location = s.replaceAll("%Y", Integer.toString(sCal.get(Calendar.YEAR)));
                location = location.replaceAll("%m", sMonthStr);
                location = location.replaceAll("%d", sDayStr);
                Log.v(DEBUG_TAG, "Trying " + location + "...");
                
                // And go fetch!
                CloseableHttpClient client = HttpClients.createDefault();
                mRequest = new HttpGet(location);

                HttpResponse response;

                // Get ready to time out if need be.  You never know.
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        Log.i(DEBUG_TAG, "Stock fetch connection timed out, aborting now.");
                        try {
                            mRequest.abort();
                        } catch (NullPointerException npe) {
                            // It COULD be null at that point.  If it is, we can
                            // just safely ignore it.
                        }
                    }
                };

                // Timer goes now!  We'll start the client immediately in the
                // upcoming try block.
                new Timer(true).schedule(task, CONNECTION_TIMEOUT_MS);

                try {
                    response = client.execute(mRequest);
                    task.cancel();

                    // If that came out aborted, it was a timeout, so move on.
                    if(mRequest.isAborted()) continue;
                } catch (IOException e) {
                    // If there was an exception, there was some issue with the
                    // server.  It might've been aborted by timeout, but still,
                    // move on to the next server.
                    Log.d(DEBUG_TAG, "IOException!", e);
                    continue;
                }

                if (response.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    // If the server gives us a 404, that's saying it can't find
                    // the stock for the day, which in turn implies it hasn't
                    // been posted yet.  Log as such and try the next server.
                    // Maybe they're just not in sync.
                    Log.d(DEBUG_TAG, "Server said there was no stock for " + DateTools.getHyphenatedDateString(sCal));
                    curStatus = ERROR_NOT_POSTED;
                    continue;
                } else if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
                    // A non-okay response that isn't a 404 is bad.  Count this
                    // one as ERROR_SERVER and just continue.
                    continue;
                }
                
                // Well, we got this far!  Let's read!
                result = getStringFromStream(response.getEntity().getContent());
                
                // With that done, we try to convert the output to the float.
                // If this fails, we got bogus data and should roll on.
                try {
                    Float.parseFloat(result);
                } catch (NumberFormatException nfe) {
                    result = "";
                    continue;
                }
                
                // We survived!  Set the status flag and keep going!
                Log.d(DEBUG_TAG, "Success!  Stock found!  It's " + result + "!");
                curStatus = ALL_OKAY;
                client.close();
                break;
            }
            
            // If we got this far and we still had an ERROR_SERVER or
            // ERROR_NOT_POSTED, throw 'em.  We failed.
            if(curStatus == ERROR_NOT_POSTED)
                throw new FileNotFoundException();
            else if(curStatus == ERROR_SERVER)
                throw new IOException();

            // If we finally, FINALLY got this far, we've got a successful stock!
            return result;
        }
        
        /**
         * Takes the given stream and makes a String out of whatever data it has. Be
         * really careful with this, as it will just attempt to read whatever's in
         * the stream until it stops, meaning it'll spin endlessly if this isn't the
         * sort of stream that ends.
         * 
         * @param stream
         *            InputStream to read from
         * @return a String consisting of the data from the stream
         */
        @NonNull
        private static String getStringFromStream(@NonNull InputStream stream)
                throws IOException {
            BufferedReader buff = new BufferedReader(new InputStreamReader(stream));

            // Load it up...
            StringBuilder tempstring = new StringBuilder();
            char[] bean = new char[1024];
            int read;
            while ((read = buff.read(bean)) != -1) {
                tempstring.append(bean, 0, read);
            }

            return tempstring.toString();
        }

        /**
         * Returns whatever the current status is.  This is returned as a part
         * of the Handler callback, but if, for instance, the Activity was
         * destroyed between the call to get the stock value and the time it
         * actually got it, the new caller will need to come here for the status.
         *
         * @return the current status
         */
        public int getStatus() {
            return mStatus;
        }
    }

    // You don't construct a HashBuilder!  You gotta EARN it!
    private HashBuilder() { }

    /**
     * Initializes and returns a StockStoreDatabase object.  This should be used
     * in ALL cases the mStore is needed to ensure it actually exists.  It can,
     * for instance, stop existing if the app is destroyed to reclaim memory.
     * 
     * @param c Context with which StockStoreDatabase will be initialized.
     * @return a new StockStoreDatabase object
     */
    @NonNull
    private static synchronized StockStoreDatabase getStore(@NonNull Context c) {
        if(mStore == null) {
            mStore = new StockStoreDatabase().init(c);
        }
        
        return mStore;
    }
    
    /**
     * Requests a <code>StockRunner</code> object to perform a stock-fetching
     * operation.
     * 
     * @param con Context for databasey stuff
     * @param c Calendar object with the adventure date requested (this will
     *          account for the 30W Rule, so don't put it in) 
     * @param g Graticule to use
     */
    @NonNull
    public static StockRunner requestStockRunner(@NonNull Context con, @NonNull Calendar c, @Nullable Graticule g) {
        return new StockRunner(con, c, g);
    }

    /**
     * Attempt to construct an Info object from stored info and return it,
     * explicitly without going to the internet.  If this can't be done, this
     * will return null.
     *
     * @param con Context used to retrieve the database, if needed
     * @param c Calendar object with the adventure date requested (this will
     *          account for the 30W Rule, so don't put it in) 
     * @param g Graticule to use
     * @return the Info object for the given data, or null if can't be built
     *         without going to the internet.
     */
    @Nullable
    public static Info getStoredInfo(@NonNull Context con, @NonNull Calendar c, @Nullable Graticule g) {
        // First, check the quick cache.  If it's in the quick cache, use it.
        Log.v(DEBUG_TAG, "Checking caches for " + DateTools.getDateString(c)
                + ((g == null || g.uses30WRule()) ? " with 30W rule" : " without 30W rule"));
        Info result = getQuickCache(c, g);
        if(result != null) {
            Log.v(DEBUG_TAG, "Data found in quickcache!");
            if(result.isGlobalHash()) return result;
            else return cloneInfo(result, g);
        }
        
        // Otherwise, check the stock cache.
        Info i = getStore(con).getInfo(c, g);
        
        if(i == null)
            return null;
            
        Log.v(DEBUG_TAG, "Data found in database!  Quickcaching...");
        // If it was in the main cache but not the quick cache, quick cache it.
        quickCache(i);
        return i;
    }
    
    /**
     * Attempt to get the stock value stored in the database for the given
     * already-adjusted date.  This won't go to the internet; that's the
     * responsibility of a StockRunner.
     * 
     * @param con Context used to retrieve the database, if needed 
     * @param c already-adjusted date to check
     * @return the String representation of the stock, or null if it's not there
     */
    @Nullable
    public static String getStoredStock(@NonNull Context con, @NonNull Calendar c) {
        // We don't quickcache the stock values.
        Log.v(DEBUG_TAG, "Going to the database for a stock for " + DateTools.getDateString(c));
        
        return getStore(con).getStock(c);
    }
    
    /**
     * Puts the given data into the quick cache.  Note that the Calendar object
     * is the date of the stock, not the date of the expedition.
     * 
     * @param i Info to store
     */
    private static void quickCache(@NonNull Info i) {
        // Slide over!
        mTwoInfosAgo = mLastInfo;
        mLastInfo = i;
    }
    
    /**
     * Stores Info data away in the database.  This won't do anything if the
     * day's Info already exists therein.
     * 
     * @param con Context used to retrieve the database, if needed
     * @param i an Info bundle with everything we need
     */
    private synchronized static void storeInfo(@NonNull Context con, @NonNull Info i) {
        // First, replace the last-known results.
        quickCache(i);
        
        StockStoreDatabase store = getStore(con);
        
        // Then, write it to the database.
        store.storeInfo(i);
        store.cleanup(con);
    }
    
    private synchronized static void storeStock(@NonNull Context con, @NonNull Calendar cal, @NonNull String stock) {
        StockStoreDatabase store = getStore(con);
        
        store.storeStock(cal, stock);
        store.cleanup(con);
    }

    /**
     * Wipes out the entire stock cache.  No, seriously.
     * 
     * @param con Context used to retrieve the database
     * @return true on success, false on failure
     */
    public synchronized static boolean deleteCache(@NonNull Context con) {
        return getStore(con).deleteCache();
    }
    
    /**
     * Build an Info object.  Since this assumes we already have a stock price
     * AND the Graticule can tell us if we need to use the 30W rule, use the
     * REAL date on the Calendar object.
     * 
     * @param c date from which this hash comes
     * @param stockPrice effective stock price (already adjusted for the 30W Rule)
     * @param g the graticule in question
     * @return a new Info object
     */
    @NonNull
    private static Info createInfo(@NonNull Calendar c, @NonNull String stockPrice, @Nullable Graticule g) {
        // This creates the Info object that'll go right back to whatever was
        // calling it.  In general, this is the Handler in StockRunner.
        
        // So to that end, we first build up the hash.
        String hash = makeHash(c, stockPrice);
        
        // Then, get the latitude and longitude from that.
        double lat = getLatitude(g, hash);
        double lon = getLongitude(g, hash);
        
        // And finally...
        return new Info(lat, lon, g, c);
    }
    
    /**
     * Build an Info object marked as invalid.  This is for error-reporting.
     * 
     * @param c date from which this hash should've come
     * @param g the graticule in question
     * @return an Info object marked invalid
     */
    @NonNull
    private static Info createInvalidInfo(@NonNull Calendar c, @Nullable Graticule g) {
        return new Info(g, c);
    }
    
    /**
     * Builds a new Info object by applying a new Graticule to an existing Info
     * object.  That is to say, change the destination of an Info object to
     * somewhere else, as if it were the same day and same stock value (and
     * thus the same hash).  Note that this will throw an exception if the 
     * existing Info's 30W-alignment isn't the same as the new Graticule's,
     * because that might require a trip back to the internet, and by this
     * point, we should know that we don't need to do so.
     * 
     * Also note that you can't do any cloning actions on a globalhash, since
     * that doesn't make any sense.
     * 
     * @param i old Info object to clone
     * @param g new Graticule to apply
     * @throws InvalidParameterException the Info and Graticule do not lie on
     *                                   the same side of the 30W line, or one
     *                                   of the Graticules in question
     *                                   represents a globalhash.
     * @return a new, improved Info object
     */
    @NonNull
    private static Info cloneInfo(@NonNull Info i, @Nullable Graticule g) {
        if(i.isGlobalHash() || g == null)
            throw new InvalidParameterException("You can't clone a globalhash point, since that doesn't make any sense.");

        Graticule source = i.getGraticule();

        if(source == null)
            throw new InvalidParameterException("You can't clone a globalhash point, since that doesn't make any sense.");
        
        // This sort of requires the 30W-itude of both to match.
        if(source.uses30WRule() != g.uses30WRule())
            throw new InvalidParameterException("The given Info and Graticule do not lie on the same side of the 30W line; this should not have happened.");
        
        // Get the destination set...
        double lat = (g.getLatitude() + i.getLatitudeHash()) * (g.isSouth() ? -1 : 1);
        double lon = (g.getLongitude() + i.getLongitudeHash()) * (g.isWest() ? -1 : 1);
        
        // Then...
        return new Info(lat, lon, g, i.getCalendar());
    }
    
    /**
     * Generate the hash string from the date and stock price.  The REAL date,
     * that is.  Not a 30W Rule-adjusted date.
     * 
     * @param c date to use
     * @param stockPrice stock price to use
     * @return the hash you're looking for
     */
    @NonNull
    private static String makeHash(@NonNull Calendar c, @NonNull String stockPrice) {
        // Just reset the hash. This can be handy alone if the graticule has
        // changed.  Remember, c is the REAL date, not the STOCK date!
        return MD5Tools.MD5hash(String.format(Locale.US,
                "%4d-%02d-%02d-%s",
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH),
                stockPrice));
    }

    @Nullable
    private static Info getQuickCache(@NonNull Calendar sCal, @Nullable Graticule g) {
        // We don't use Calendar.equals here, as that checks all properties,
        // including potentially some we don't really care about.
        boolean is30W = (g == null || g.uses30WRule());
        
        // At any rate, first off, the most recent date/30W combo.  Then, the
        // second-most.  Failing THAT, return null.
        Log.v(DEBUG_TAG, "Checking quickcache for data...");
        if(mLastInfo != null) {
            Calendar stored = mLastInfo.getCalendar();
            
            if(stored.get(Calendar.MONTH) ==  sCal.get(Calendar.MONTH)
                    && stored.get(Calendar.DAY_OF_MONTH) ==  sCal.get(Calendar.DAY_OF_MONTH)
                    && stored.get(Calendar.YEAR) ==  sCal.get(Calendar.YEAR)
                    && ((mLastInfo.getGraticule() == null && g == null)
                            || (mLastInfo.getGraticule() != null && g != null))
                    && mLastInfo.uses30WRule() == is30W) {
                Log.v(DEBUG_TAG, "Hash data is in quick cache (mLastInfo): " + mLastInfo.getLatitudeHash() + ", " + mLastInfo.getLongitudeHash());
                return mLastInfo;
            }
        }
        
        if(mTwoInfosAgo != null) {
            Calendar stored = mTwoInfosAgo.getCalendar();
            
            if(stored.get(Calendar.MONTH) ==  sCal.get(Calendar.MONTH)
                    && stored.get(Calendar.DAY_OF_MONTH) ==  sCal.get(Calendar.DAY_OF_MONTH)
                    && stored.get(Calendar.YEAR) ==  sCal.get(Calendar.YEAR)
                    && ((mTwoInfosAgo.getGraticule() == null && g == null)
                            || (mTwoInfosAgo.getGraticule() != null && g != null))
                    && mTwoInfosAgo.uses30WRule() == is30W) {
                Log.v(DEBUG_TAG, "Hash data is in quick cache (mTwoInfosAgo): " + mTwoInfosAgo.getLatitudeHash() + ", " + mTwoInfosAgo.getLongitudeHash());
                return mTwoInfosAgo;
            }
        }
        
        Log.v(DEBUG_TAG, "Data wasn't in quickcache.");
        
        return null;
    }
    
    /**
     * Gets the latitude value of the location for the current date. This is
     * attached to the current graticule integer value to produce the longitude.
     * 
     * @return the fractional latitude value
     */
    private static double getLatitudeHash(@NonNull String hash) {
        String chunk = hash.substring(0, 16);
        return HexFraction.calculate(chunk);
    }

    /**
     * Gets the longitude value of the location for the current date. This is
     * attached to the current graticule integer value to produce the latitude.
     * 
     * @return the fractional longitude value
     */
    private static double getLongitudeHash(@NonNull String hash) {
        String chunk = hash.substring(16, 32);
        return HexFraction.calculate(chunk);
    }

    private static double getLatitude(@Nullable Graticule g, @NonNull String hash) {
        // If the Graticule's not null, this is a normal hash.  If it is, it's a
        // globalhash, and has to be treated differently.
        if(g != null) {
            int lat = g.getLatitude();
            if (g.isSouth()) {
                return (lat + getLatitudeHash(hash)) * -1;
            } else {
                return lat + getLatitudeHash(hash);
            }
        } else {
            return getLatitudeHash(hash);
        }

    }

    private static double getLongitude(@Nullable Graticule g, @NonNull String hash) {
        // Same deal as with getLatitude.
        if(g != null) {
            int lon = g.getLongitude();
            if (g.isWest()) {
                return (lon + getLongitudeHash(hash)) * -1;
            } else {
                return lon + getLongitudeHash(hash);
            }
        } else {
            return getLongitudeHash(hash);
        }
    }

}
