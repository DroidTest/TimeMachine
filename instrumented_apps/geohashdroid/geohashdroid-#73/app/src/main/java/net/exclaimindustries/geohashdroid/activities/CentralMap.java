/*
 * CentralMap.java
 * Copyright (C)2015 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.activities;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.backup.BackupManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.fragments.AboutDialogFragment;
import net.exclaimindustries.geohashdroid.fragments.GHDDatePickerDialogFragment;
import net.exclaimindustries.geohashdroid.fragments.MapTypeDialogFragment;
import net.exclaimindustries.geohashdroid.fragments.PermissionDeniedDialogFragment;
import net.exclaimindustries.geohashdroid.fragments.VersionHistoryDialogFragment;
import net.exclaimindustries.geohashdroid.services.AlarmService;
import net.exclaimindustries.geohashdroid.services.StockService;
import net.exclaimindustries.geohashdroid.util.ExpeditionMode;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.util.KnownLocation;
import net.exclaimindustries.geohashdroid.util.PermissionsDeniedListener;
import net.exclaimindustries.geohashdroid.util.SelectAGraticuleMode;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.geohashdroid.util.VersionHistoryParser;
import net.exclaimindustries.geohashdroid.widgets.ErrorBanner;
import net.exclaimindustries.tools.DateTools;
import net.exclaimindustries.tools.LocationUtil;

import org.xmlpull.v1.XmlPullParserException;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * CentralMap replaces MainMap as the map display.  Unlike MainMap, it also
 * serves as the entry point for the entire app.  These comments are going to
 * make so much sense later when MainMap is little more than a class that only
 * exists on the legacy branch.
 */
public class CentralMap
        extends BaseMapActivity
        implements GHDDatePickerDialogFragment.GHDDatePickerCallback {
    private static final String DEBUG_TAG = "CentralMap";

    private static final String DATE_PICKER_DIALOG = "datePicker";
    private static final String MAP_TYPE_DIALOG = "mapType";
    private static final String VERSION_HISTORY_DIALOG = "versionHistory";
    private static final String ABOUT_DIALOG = "about";

    private static final String STATE_WAS_ALREADY_ZOOMED = "alreadyZoomed";
    private static final String STATE_WAS_SELECT_A_GRATICULE = "selectAGraticule";
    private static final String STATE_WAS_GLOBALHASH = "globalhash";
    private static final String STATE_WAS_RESOLVING_CONNECTION_ERROR = "resolvingError";
    private static final String STATE_WERE_PERMISSIONS_DENIED = "permissionsDenied";
    private static final String STATE_LAST_GRATICULE = "lastGraticule";
    private static final String STATE_LAST_CALENDAR = "lastCalendar";
    private static final String STATE_LAST_MODE_BUNDLE = "lastModeBundle";

    private static final int LOCATION_PERMISSION_REQUEST = 1;

    public static final String ACTION_START_CLOSEST_HASHPOINT = "net.exclaimindustries.geohashdroid.START_CLOSEST_HASHPOINT";
    public static final String ACTION_START_LAST_USED = "net.exclaimindustries.geohashdroid.START_LAST_USED";
    public static final String ACTION_START_GRATICULE_PICKER = "net.exclaimindustries.geohashdroid.START_GRATICULE_PICKER";

    // If we're in Select-A-Graticule mode (as opposed to expedition mode).
    private boolean mSelectAGraticule = false;
    // If we already did the initial zoom for this expedition.
    private boolean mAlreadyDidInitialZoom = false;
    // If the map's ready.
    private boolean mMapIsReady = false;
    // If the map's layout is ready.
    private boolean mLayoutComplete = false;
    // If the ViewTreeObserver's already done.
    private boolean mAlreadyLaidOut = false;
    // If we're already listening for locations.
    private boolean mAlreadyListening = false;

    private FusedLocationProviderClient mFusedLocationClient;
    private Location mLastKnownLocation;

    // This is either the current expedition Graticule (same as in mCurrentInfo)
    // or the last-selected Graticule in Select-A-Graticule mode (needed if we
    // need to reconstruct from an onDestroy()).
    private Graticule mLastGraticule;
    private Calendar mLastCalendar;

    // Because a null Graticule is considered to be the Globalhash indicator, we
    // need a boolean to keep track of whether we're actually in a Globalhash or
    // if we just don't have a Graticule yet.
    private boolean mGlobalhash;

    private ErrorBanner mBanner;
    private View mProgress;
    private Bundle mLastModeBundle;
    private CentralMapMode mCurrentMode;

    private float mProgressHeight = 0.0f;

    private List<Marker> mKnownLocationMarkers;

    /**
     * <p>
     * A <code>CentralMapMode</code> is a set of behaviors that happen whenever
     * some corresponding event occurs in {@link CentralMap}.
     * </p>
     *
     * <p>
     * Note the {@link #pause()} and {@link #resume()} methods.  While those
     * correspond to {@link CentralMap}'s onPause and onResume methods, there is
     * NOT a similar lifecycle in <code>CentralMapMode</code>.  That is,
     * {@link #pause()} and {@link #resume()} are NOT guaranteed to be called in
     * any relation to {@link #init(Bundle)} or {@link #cleanUp()}.  If there's
     * never an onPause or onResume in the life of a CentralMapMode, it will NOT
     * receive the corresponding calls, and will instead just get the
     * {@link #init(Bundle)} and {@link #cleanUp()} calls.
     * </p>
     */
    public abstract static class CentralMapMode implements LocationListener,
                                                           PermissionsDeniedListener {
        protected boolean mInitComplete = false;
        private boolean mCleanedUp = false;

        /** Flag indicating a handleInfo call comes from a notification. */
        protected final static int FLAG_FROM_NOTIFICATION = 0x1000000;

        /** Bundle key for the current Graticule. */
        public final static String GRATICULE = "graticule";
        /** Bundle key for the current date, as a Calendar. */
        public final static String CALENDAR = "calendar";
        /**
         * Bundle key for a boolean indicating that, if the Graticule is null,
         * this was actually a Globalhash, not just a request with an empty
         * Graticule.
         */
        public final static String GLOBALHASH = "globalhash";
        /**
         * Bundle key for the current Info.  In cases where this can be given,
         * the Graticule, Calendar, and boolean indicating a Globalhash can be
         * implied from it.
         */
        public final static String INFO = "info";

        /** The current GoogleMap object. */
        protected GoogleMap mMap;
        /** The calling CentralMap Activity. */
        protected CentralMap mCentralMap;

        /** The current destination Marker. */
        protected Marker mDestination;

        /**
         * Sets the {@link GoogleMap} this mode deals with.  When implementing
         * this, make sure to actually do something with it like subscribe to
         * events as the mode needs them if you're not doing so in
         * {@link #init(Bundle)}.
         *
         * @param map that map
         */
        public void setMap(@NonNull GoogleMap map) {
            mMap = map;
        }

        /**
         * Sets the {@link CentralMap} to which this will talk back.
         *
         * @param centralMap that CentralMap
         */
        public void setCentralMap(@NonNull CentralMap centralMap) {
            mCentralMap = centralMap;
        }

        /**
         * <p>
         * Does whatever init tomfoolery is needed for this class, using the
         * given Bundle of stuff.  You're probably best calling this AFTER
         * {@link #setMap(GoogleMap)} and {@link #setCentralMap(CentralMap)} are
         * called and when the GoogleApiClient object is ready for use.
         * </p>
         *
         * @param bundle a bunch of stuff, or null if there's no stuff to be had
         */
        public abstract void init(@Nullable Bundle bundle);

        /**
         * Does whatever cleanup rigmarole is needed for this class, such as
         * unsubscribing to all those subscriptions you set up in {@link #setMap(GoogleMap)}
         * or {@link #init(Bundle)}.
         */
        public void cleanUp() {
            // The marker always goes away, at the very least.
            removeDestinationPoint();

            if(mCentralMap != null) mCentralMap.getErrorBanner().animateBanner(false);

            // Set the cleaned up flag, too.
            mCleanedUp = true;
        }

        /**
         * Stores the state of this mode into yonder Bundle.  This is NOT
         * guaranteed to be followed by {@link #cleanUp()}, apparently.  Did not
         * know that at first.  This is also where you write out any data that
         * might be useful to other modes, such as the selected Graticule in
         * SelectAGraticuleMode.
         *
         * @param bundle the Bundle to which to write data.
         */
        public abstract void onSaveInstanceState(@NonNull Bundle bundle);

        /**
         * Called when the Activity gets onPause().  Remember, the mode object
         * might not ever get this call.  This is only if the Activity is
         * EXPLICITLY pausing AFTER this mode was created.
         */
        public abstract void pause();

        /**
         * Called when the Activity gets onResume().  Remember, the mode object
         * might not ever get this call.  This is only if the Activity is
         * EXPLICITLY resuming AFTER this mode was created.
         */
        public abstract void resume();

        /**
         * Convenience method to call {@link CentralMap#requestStock(Graticule, Calendar, int)}.
         *
         * @param g the Graticule (can be null for globalhashes)
         * @param c the Calendar
         * @param flags the {@link StockService} flags
         */
        protected void requestStock(@Nullable Graticule g, @NonNull Calendar c, int flags) {
            mCentralMap.getErrorBanner().animateBanner(false);
            mCentralMap.requestStock(g, c, flags);
        }

        /**
         * Called when a new Info has come in from StockService.
         *
         * @param info that Info
         * @param nearby any nearby Infos that may have been requested (can be null)
         * @param flags the request flags that were sent with it
         */
        public abstract void handleInfo(Info info, @Nullable Info[] nearby, int flags);

        /**
         * Called when a stock lookup fails for some reason.
         *
         * @param reqFlags the flags used in the request
         * @param responseCode the response code (won't be {@link StockService#RESPONSE_OKAY}, for obvious reasons)
         */
        public abstract void handleLookupFailure(int reqFlags, int responseCode);

        /**
         * Called when the menu needs to be built.
         *
         * @param c the current Context (the mode may not be fully up by the time this is needed, and thus may not have mCentralMap)
         * @param inflater a MenuInflater, for convenience
         * @param menu the Menu that needs inflating.
         */
        public abstract void onCreateOptionsMenu(Context c, MenuInflater inflater, Menu menu);

        /**
         * Called when a menu item is selected but CentralMap didn't handle it
         * itself.
         *
         * @param item the item that got selected
         * @return true if it was handled, false if not
         */
        public abstract boolean onOptionsItemSelected(MenuItem item);

        /**
         * Called when a new Calendar comes in.  The modes should update as need
         * be.  This should mean calling for a new Info from StockService, but
         * NOT updating its own Info or concept of the current Calendar if there
         * was a problem with the stock (i.e. it wasn't posted yet).
         *
         * @param newDate the new Calendar
         */
        public abstract void changeCalendar(@NonNull Calendar newDate);

        /**
         * Draws a final destination point on the map given the appropriate
         * Info.  This also removes any old point that might've been around.
         *
         * @param info the new Info
         */
        protected void addDestinationPoint(Info info) {
            // Clear any old destination marker first.
            removeDestinationPoint();

            if(info == null) return;

            // We need a marker!  And that marker needs a title.  And that title
            // depends on globalhashiness and retroness.
            String title;

            Calendar cal = Calendar.getInstance();
            Calendar infoCal = info.getCalendar();

            if(DateTools.isSameDate(infoCal, cal)) {
                // Non-retro hashes don't have today's date on them.  They just
                // have "today's [something]".
                if(info.isGlobalHash()) {
                    title = mCentralMap.getString(R.string.marker_title_today_globalpoint);
                } else {
                    title = mCentralMap.getString(R.string.marker_title_today_hashpoint);
                }
            } else if(DateTools.isTomorrow(infoCal, cal)) {
                // Same with tomorrow.
                if(info.isGlobalHash()) {
                    title = mCentralMap.getString(R.string.marker_title_tomorrow_globalpoint);
                } else {
                    title = mCentralMap.getString(R.string.marker_title_tomorrow_hashpoint);
                }
            } else if(DateTools.isDayAfterTomorrow(infoCal, cal)) {
                // And the day after tomorrow.
                if(info.isGlobalHash()) {
                    title = mCentralMap.getString(R.string.marker_title_doubletomorrow_globalpoint);
                } else {
                    title = mCentralMap.getString(R.string.marker_title_doubletomorrow_hashpoint);
                }
            } else {
                // Anything else, however, needs a date string.
                String date = DateFormat.getDateInstance(DateFormat.LONG).format(info.getDate());

                if(info.isGlobalHash()) {
                    title = mCentralMap.getString(R.string.marker_title_retro_globalpoint, date);
                } else {
                    title = mCentralMap.getString(R.string.marker_title_retro_hashpoint, date);
                }
            }

            // The snippet's just the coordinates in question.  Further details
            // will go in the infobox.
            String snippet = UnitConverter.makeFullCoordinateString(mCentralMap, info.getFinalLocation(), false, UnitConverter.OUTPUT_LONG);

            // Under the current marker image, the anchor is the very bottom,
            // halfway across.  Presumably, that's what the default icon also
            // uses, but we're not concerned with the default icon, now, are we?
            mDestination = mMap.addMarker(new MarkerOptions()
                    .position(info.getFinalDestinationLatLng())
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.final_destination))
                    .anchor(0.5f, 1.0f)
                    .title(title)
                    .snippet(snippet));
        }

        /**
         * Removes the destination point, if one exists.
         */
        protected void removeDestinationPoint() {
            if(mDestination != null) {
                mDestination.remove();
                mDestination = null;
            }
        }

        /**
         * Sets the title of the map Activity using a String.
         *
         * @param title the new title
         */
        protected final void setTitle(String title) {
            mCentralMap.setTitle(title);
        }

        /**
         * Sets the title of the map Activity using a resource ID.
         *
         * @param resid the new title's resource ID
         */
        protected final void setTitle(@StringRes int resid) {
            mCentralMap.setTitle(resid);
        }

        /**
         * Returns whether or not {@link #cleanUp()} has been called yet.  If
         * so, you should generally not call anything else.
         *
         * @return true if cleaned up, false if not
         */
        public final boolean isCleanedUp() {
            return mCleanedUp;
        }

        /**
         * Returns whether or not {@link #init(Bundle)} has finished.  If so,
         * you probably shouldn't call init again, and are probably looking to
         * call resume instead.
         *
         * @return true if init is complete, false if not
         */
        public final boolean isInitComplete() {
            return mInitComplete;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName();
        }

        /**
         * Gets the user's last known location as seen by CentralMap.  Note that
         * this may be null if the user's location isn't known yet (or if the
         * user refused location permissions).  Also, there's no guarantee that
         * this is at all recent.
         *
         * @return a Location, or null
         */
        @Nullable
        protected Location getLastKnownLocation() {
            if(mCentralMap != null)
                return mCentralMap.mLastKnownLocation;
            else
                return null;
        }

        /**
         * Gets the active Info the current mode is using, if any and if
         * applicable.
         *
         * @return an Info, or null
         */
        @Nullable
        protected abstract Info getActiveInfo();

        /**
         * <p>
         * Gets whether or not the user explicitly denied permissions during
         * this session.  Updates on this state will be sent via {@link #permissionsDenied(boolean)},
         * but this should be called during {@link #init(Bundle)} to get things
         * set up initially.
         * </p>
         *
         * <p>
         * Remember, just because this returns false does NOT mean permissions
         * have been GRANTED; it just means permissions weren't DENIED, and most
         * importantly, weren't denied YET.  There is a difference.  In the
         * false case, for instance, the user might still be being prompted for
         * permissions, in which case {@link #permissionsDenied(boolean)} might
         * eventually come up with true.
         * </p>
         *
         * @return true if permissions were denied, false if not
         */
        protected boolean arePermissionsDenied() {
            return mCentralMap != null && mCentralMap.mPermissionsDenied;
        }
    }

    private class StockReceiver extends BroadcastReceiver {
        private final static String DEBUG_TAG = "StockReceiver";

        // This allows us to NOT blast out responses if the current mode didn't
        // request it.
        private Set<Long> mWaitingList;

        public StockReceiver() {
            mWaitingList = new HashSet<>();
        }

        /**
         * Adds the given ID to the waiting list.  If an ID comes back and it
         * wasn't in the waiting list, it won't be dispatched to the modes.
         *
         * @param id the request ID
         */
        public void addToWaitingList(long id) {
            mWaitingList.add(id);
        }

        /**
         * Removes all waiting IDs from the list
         */
        public void clearWaitingList() {
            // Yes, since we can have multiple IDs pointing to the same mode, we
            // have to do it this way.
            mWaitingList.clear();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(DEBUG_TAG, "Stock has come in!");

            // Progress goes away!
            mProgress.animate().translationY(-mProgressHeight).alpha(0.0f);

            Bundle bun = intent.getBundleExtra(StockService.EXTRA_STUFF);
            bun.setClassLoader(getClassLoader());

            // A stock result arrives!  Let's get data!  That oughta tell us
            // whether or not we're even going to bother with it.
            int reqFlags = bun.getInt(StockService.EXTRA_REQUEST_FLAGS, 0);
            long reqId = bun.getLong(StockService.EXTRA_REQUEST_ID, -1);
            Calendar cal = (Calendar)bun.getSerializable(StockService.EXTRA_DATE);

            // Now, if the flags state this was from the alarm or somewhere else
            // we weren't expecting, give up now.  We don't want it.
            if((reqFlags & StockService.FLAG_ALARM) != 0) return;

            // Well, it's what we're looking for.  What was the result?  The
            // default is RESPONSE_NETWORK_ERROR, as not getting a response code
            // is a Bad Thing(tm).
            int responseCode = bun.getInt(StockService.EXTRA_RESPONSE_CODE, StockService.RESPONSE_NETWORK_ERROR);

            // Since the mode switchers wipe all requests from a given mode, all
            // we need for a mode match is whether or not the item exists in the
            // waiting list.
            boolean modeMatches = mWaitingList.remove(reqId);

            if(responseCode == StockService.RESPONSE_OKAY) {
                // Hey, would you look at that, it actually worked!  So, get
                // the Info out of it and fire it away to the corresponding
                // CentralMapMode, if applicable.
                if(modeMatches) {
                    Info received = bun.getParcelable(StockService.EXTRA_INFO);
                    Parcelable[] pArr = bun.getParcelableArray(StockService.EXTRA_NEARBY_POINTS);

                    Info[] nearby = null;
                    if(pArr != null)
                        nearby = Arrays.copyOf(pArr, pArr.length, Info[].class);
                    if(received != null) {
                        updateLastGraticule(received);
                        mCurrentMode.handleInfo(received, nearby, reqFlags);
                    }
                } else {
                    Log.w(DEBUG_TAG, "Request ID " + reqId + " was NOT expected by this mode, ignoring...");
                }
            } else {
                // Make sure the mode knows what's up first.
                if(modeMatches)
                    mCurrentMode.handleLookupFailure(reqFlags, responseCode);

                if((reqFlags & StockService.FLAG_USER_INITIATED) != 0) {
                    // ONLY notify the user of an error if they specifically
                    // requested this stock.
                    switch(responseCode) {
                        case StockService.RESPONSE_NOT_POSTED_YET:
                            // Just in case, change the text if it's today's
                            // date that was requested.  That's a bit clearer.
                            Calendar today = Calendar.getInstance();
                            boolean isActuallyToday = (cal != null
                                && today.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
                                && today.get(Calendar.MONTH) == cal.get(Calendar.MONTH)
                                && today.get(Calendar.DAY_OF_MONTH) == cal.get(Calendar.DAY_OF_MONTH));

                            mBanner.setText(getString(isActuallyToday ? R.string.error_not_yet_posted_today : R.string.error_not_yet_posted));
                            mBanner.setErrorStatus(ErrorBanner.Status.ERROR);
                            mBanner.animateBanner(true);
                            break;
                        case StockService.RESPONSE_NO_CONNECTION:
                            mBanner.setText(getString(R.string.error_no_connection));
                            mBanner.setErrorStatus(ErrorBanner.Status.ERROR);
                            mBanner.animateBanner(true);
                            break;
                        case StockService.RESPONSE_NETWORK_ERROR:
                            mBanner.setText(getString(R.string.error_server_failure));
                            mBanner.setErrorStatus(ErrorBanner.Status.ERROR);
                            mBanner.animateBanner(true);
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    private StockReceiver mStockReceiver = new StockReceiver();

    private LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            // New location!
            mLastKnownLocation = locationResult.getLastLocation();

            if(mCurrentMode != null) mCurrentMode.onLocationChanged(mLastKnownLocation);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        // Load up!
        if(savedInstanceState != null) {
            mAlreadyDidInitialZoom = savedInstanceState.getBoolean(STATE_WAS_ALREADY_ZOOMED, false);
            mSelectAGraticule = savedInstanceState.getBoolean(STATE_WAS_SELECT_A_GRATICULE, false);
            mGlobalhash = savedInstanceState.getBoolean(STATE_WAS_GLOBALHASH, false);
            mResolvingError = savedInstanceState.getBoolean(STATE_WAS_RESOLVING_CONNECTION_ERROR, false);
            mPermissionsDenied = savedInstanceState.getBoolean(STATE_WERE_PERMISSIONS_DENIED, false);

            mLastGraticule = savedInstanceState.getParcelable(STATE_LAST_GRATICULE);

            mLastCalendar = (Calendar)savedInstanceState.getSerializable(STATE_LAST_CALENDAR);

            // This will just get dropped right back into the mode wholesale.
            mLastModeBundle = savedInstanceState.getBundle(STATE_LAST_MODE_BUNDLE);
        } else if(intent != null && intent.getAction() != null && (intent.getAction().equals(AlarmService.START_INFO) || intent.getAction().equals(AlarmService.START_INFO_GLOBAL))) {
            // savedInstanceState should override the Intent.
            mLastModeBundle = new Bundle();
            mLastModeBundle.putParcelable(CentralMapMode.INFO, intent.getBundleExtra(StockService.EXTRA_STUFF).getParcelable(StockService.EXTRA_INFO));
            mSelectAGraticule = false;
        }

        setContentView(R.layout.centralmap);

        // Who wants a FusedLocationProviderClient?  We do!  Because we very
        // specifically want location updates, and this is better than dealing
        // with the GoogleApiClient interface of days of yore.
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mBanner = findViewById(R.id.error_banner);
        mProgress = findViewById(R.id.progress_container);

        // Apply nighttime mode to the progress background!  Only do that if
        // this is less than Lollipop, though.  We can apply the color of the
        // active theme directly in the resource files in Lollipop or later, but
        // anything beforehand, we need to fake it.
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if(isNightMode())
                mProgress.setBackground(ContextCompat.getDrawable(this, R.drawable.progress_background_dark));
            else
                mProgress.setBackground(ContextCompat.getDrawable(this, R.drawable.progress_background));
        }

        // The progress-o-matic needs to be off-screen.  And, we need to know
        // how much it should shift down to become back on-screen.
        mProgressHeight = getResources().getDimension(R.dimen.progress_spinner_size) + (2 * getResources().getDimension(R.dimen.double_padding));
        mProgress.setTranslationY(-mProgressHeight);
        mProgress.setAlpha(0.0f);

        // Get a map ready.  We'll know when we've got it.  Oh, we'll know.
        MapFragment mapFrag = (MapFragment)getFragmentManager().findFragmentById(R.id.map);
        mapFrag.getMapAsync(googleMap -> {
            mMap = googleMap;

            // I could swear you could do this in XML...
            UiSettings set = mMap.getUiSettings();

            // The My Location button has to go off, as we're going to have the
            // infobox right around there.
            set.setMyLocationButtonEnabled(false);

            // Go to preferences to figure out what map type we're using.
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(CentralMap.this);
            mapTypeSelected(prefs.getInt(GHDConstants.PREF_LAST_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL));

            // Now, set the flag that tells everything else (especially the
            // doReadyChecks method) we're ready.  Then, call doReadyChecks.
            mMapIsReady = true;
            doReadyChecks();

//                startListening();
        });

        // The map also needs to be laid out before we act on it.
        mapFrag.getView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            // Got a height!  Hopefully.
            if(!mAlreadyLaidOut) {
                mAlreadyLaidOut = true;

                // Flag!
                mLayoutComplete = true;
                doReadyChecks();
            }
        });

        // Perform startup and cleanup work before the modes arrive.
        doStartupStuff();

        // Figure out where the user needs to be when this starts.  Either of
        // these will alter mLastModeBundle if need be.  If it's a shortcut,
        // mLastModeBundle is overwritten.  If not, mLastModeBundle is only
        // overwritten if it's null.
        if(!startFromShortcut(intent)) {
            startInCorrectMode();
        }

        // Now, we get our initial mode set up based on mSelectAGraticule.  We
        // do NOT init it yet; we have to wait for both the map fragment and the
        // API to be ready first.
        if(mSelectAGraticule)
            mCurrentMode = new SelectAGraticuleMode();
        else
            mCurrentMode = new ExpeditionMode();
    }

    @Override
    protected void onPause() {
        // The modes should know what they need to do when pausing.
        if(mCurrentMode != null)
            mCurrentMode.pause();

        // Now stop.
        stopListening();

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Do a permissions check.  If it turns out we DO have permissions, we
        // can mark the denied flag as false.  This covers cases where the user
        // denies permission at one point, suspends the Activity, grants it some
        // other way, and returns.  I'm quite certain someone will make a really
        // big deal out of it and claim it's a common use case if I don't catch
        // this circumstance.
        if(checkLocationPermissions(0, true)) mPermissionsDenied = false;

        // Then, do the ready checks.
        doReadyChecks();

        // Listen up!
        startListening();

        // The mode will resume itself once the client comes back in from
        // onStart.
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        // If there's an active expedition AND it's not today, remind the user
        // of this.  It's not always obvious, especially given how Android won't
        // reset its concept of the app being "active" in a lot of cases.
        Info activeInfo = null;
        if(mCurrentMode != null) activeInfo = mCurrentMode.getActiveInfo();

        if(activeInfo != null && !DateTools.isSameDate(activeInfo.getCalendar(), Calendar.getInstance())) {
            Toast.makeText(this, R.string.toast_not_today_reminder, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // The receiver goes on during onStart, since the modes might need it
        // before onResume has a chance to kick in.  This might actually be
        // obsolete with the change away from GoogleApiClient.
        IntentFilter filt = new IntentFilter();
        filt.addAction(StockService.ACTION_STOCK_RESULT);
        registerReceiver(mStockReceiver, filt);
    }



    @Override
    protected void onStop() {
        // The receiver goes right off as soon as we stop.
        unregisterReceiver(mStockReceiver);

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // Make sure that mode's been cleaned up first.
        mCurrentMode.cleanUp();

        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // An Intent just came in!  That's telling us to go off to a new
        // Graticule.  Since onNewIntent is only called if the Activity's
        // already going, it just means we need to tell ExpeditionMode that it
        // has a new Info (or tell SelectAGraticuleMode to leave first).
        Bundle bun = intent.getBundleExtra(StockService.EXTRA_STUFF);
        if(bun == null) return;

        Info info = bun.getParcelable(StockService.EXTRA_INFO);
        if(info == null) return;

        // Presumably, we're ready.  If the user can somehow be in this Activity
        // and get the notification fired off before the ready checks commence,
        // I'll need to rethink this.  But for now, either switch modes or tell
        // ExpeditionMode to handle a new Info.
        if(mSelectAGraticule) {
            // It's like exiting Select-A-Graticule Mode, but without caring
            // what sort of data was in there.
            mSelectAGraticule = false;
            mCurrentMode.cleanUp();

            mLastModeBundle = new Bundle();
            mLastModeBundle.putParcelable(CentralMapMode.INFO, info);

            mStockReceiver.clearWaitingList();
            mCurrentMode = new ExpeditionMode();
            doReadyChecks();
        } else {
            // Otherwise, we tell the active mode (ExpeditionMode) to update
            // itself.
            mCurrentMode.handleInfo(info, null, CentralMapMode.FLAG_FROM_NOTIFICATION);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Keep the various flags.
        outState.putBoolean(STATE_WAS_ALREADY_ZOOMED, mAlreadyDidInitialZoom);
        outState.putBoolean(STATE_WAS_SELECT_A_GRATICULE, mSelectAGraticule);
        outState.putBoolean(STATE_WAS_GLOBALHASH, mGlobalhash);
        outState.putBoolean(STATE_WAS_RESOLVING_CONNECTION_ERROR, mResolvingError);
        outState.putBoolean(STATE_WERE_PERMISSIONS_DENIED, mPermissionsDenied);

        // And some additional data.
        outState.putParcelable(STATE_LAST_GRATICULE, mLastGraticule);
        outState.putSerializable(STATE_LAST_CALENDAR, mLastCalendar);

        // Also, shut down the current mode.  We'll rebuild it later.  Also, if
        // init isn't complete yet, don't update the state.
        if(mCurrentMode != null && mCurrentMode.isInitComplete()) {
            mLastModeBundle = new Bundle();
            mCurrentMode.onSaveInstanceState(mLastModeBundle);
        }

        outState.putBundle(STATE_LAST_MODE_BUNDLE, mLastModeBundle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        // Just hand it off to the current mode, it'll know what to do.
        mCurrentMode.onCreateOptionsMenu(this, inflater, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // CentralMap should just cover the items that can always be selected no
        // matter what mode we're in.
        switch(item.getItemId()) {
            case R.id.action_map_type: {
                // The map type can be changed at any time, so it has to be
                // common.  To the alert dialog!
                MapTypeDialogFragment frag = MapTypeDialogFragment.newInstance(this);
                frag.show(getFragmentManager(), MAP_TYPE_DIALOG);

                return true;
            }
            case R.id.action_versionhistory: {
                // The version history has no real actions at all.
                VersionHistoryDialogFragment frag = VersionHistoryDialogFragment.newInstance(this);
                frag.show(getFragmentManager(), VERSION_HISTORY_DIALOG);

                return true;
            }
            case R.id.action_about: {
                // About is just a dialog with a view.
                AboutDialogFragment frag = AboutDialogFragment.newInstance();
                frag.show(getFragmentManager(), ABOUT_DIALOG);

                return true;
            }
            case R.id.action_date: {
                // The date picker is common to all modes and is best handled by
                // the Activity itself.
                if(mLastCalendar == null) {
                    // Of course, we need a date to fill in.
                    mLastCalendar = Calendar.getInstance();
                }

                GHDDatePickerDialogFragment frag = GHDDatePickerDialogFragment.newInstance(mLastCalendar);
                frag.setCallback(this);
                frag.show(getFragmentManager(), DATE_PICKER_DIALOG);

                return true;
            }
            case R.id.action_whatisthis: {
                // The everfamous and much-beloved "What's Geohashing?" button,
                // because honestly, this IS sort of confusing if you're
                // expecting something for geocaching.
                Intent i = new Intent();
                i.setAction(Intent.ACTION_VIEW);
                i.setData(Uri.parse("https://geohashing.site/geohashing/How_it_works"));
                startActivity(i);
                return true;
            }
            case R.id.action_preferences: {
                // Preferences!  To the Preferencemobile!
                Intent i = new Intent(this, PreferencesScreen.class);
                startActivity(i);
                return true;
            }
            default:
                return mCurrentMode.onOptionsItemSelected(item);
        }
    }

    @SuppressLint("CommitPrefEdits")
    private void doStartupStuff() {
        // This handles all the oddities that need to be covered at startup
        // time, including cleaning up old preferences that have been replaced
        // or otherwise changed, starting the stock alarm service if it should
        // be up, and throwing up the version history dialog if it's a new
        // version.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = prefs.edit();

        // Let's start with the stock alarm service.
        Intent i = new Intent(this, AlarmService.class);

        if(prefs.getBoolean(GHDConstants.PREF_STOCK_ALARM, false)) {
            // Alarm gets set!  Fire it up!
            i.setAction(AlarmService.STOCK_ALARM_ON);
        } else {
            // No alarm!  Off it goes!
            i.setAction(AlarmService.STOCK_ALARM_OFF);
        }

        AlarmService.enqueueWork(this, i);

        // Now for preference cleanup.  Unfortunately, this section will only
        // get bigger with time, as I can't guarantee what version the user
        // might've come from.  The version from which the user might've come.

        // The Infobox is now controlled by a boolean, not a string.
        if(prefs.contains("InfoBoxSize")) {
            if(!prefs.contains(GHDConstants.PREF_INFOBOX)) {
                String size;
                try {
                    size = prefs.getString("InfoBoxSize", "None");
                } catch (ClassCastException cce) {
                    size = "Off";
                }

                edit.putBoolean(GHDConstants.PREF_INFOBOX, size.equals("None"));
            }

            edit.remove("InfoBoxSize");
        }

        // These prefs either don't exist any more or we found better ways to
        // deal with them.
        edit.remove("DefaultLatitude")
                .remove("DefaultLongitude")
                .remove("GlobalhashMode")
                .remove("RememberGraticule")
                .remove("ClosestOn")
                .remove("AlwaysToday")
                .remove("ClosenessReported");

        // Anything edit-worthy we just did needs to be committed.
        edit.commit();

        // We still have that prefs object.  Let's see if we've got a newer
        // version than what we last saw.
        int lastVersion = prefs.getInt(GHDConstants.PREF_LAST_SEEN_VERSION, 0);
        int curVersion = -1;
        try {
            curVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException nnfe) {
            // Since this is OUR OWN PACKAGE NAME, this better work.
        }

        Log.d(DEBUG_TAG, "We are version " + curVersion + ", we last reported version history on version " + lastVersion);

        if(lastVersion < curVersion) {
            // Aha!  We're newer!  Now, let's see if there's a new version to
            // display.  That is, if the first entry in version history is newer
            // than the last-seen version.
            ArrayList<VersionHistoryParser.VersionEntry> entries = new ArrayList<>();

            try {
                entries = VersionHistoryParser.parseVersionHistory(this);
            } catch(XmlPullParserException xppe) {
                // You get NOTHING!
            }

            if(entries.isEmpty()) {
                Log.w(DEBUG_TAG, "Couldn't parse version history, not displaying anything.");
            } else {
                Log.d(DEBUG_TAG, "Newest version with an entry is " + entries.get(0).versionCode);
                if(entries.get(0).versionCode > lastVersion) {
                    VersionHistoryDialogFragment frag = VersionHistoryDialogFragment.newInstance(entries);
                    frag.show(getFragmentManager(), VERSION_HISTORY_DIALOG);
                }
            }
        }

        // In any case, update the version.
        edit.putInt(GHDConstants.PREF_LAST_SEEN_VERSION, curVersion);
        edit.apply();

        // Then, tell the BackupManager to do its thing.
        BackupManager bm = new BackupManager(this);
        bm.dataChanged();

        // If we're at API 26 or higher, get the notification channels up, too.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            doStartupNotificationChannels();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void doStartupNotificationChannels()
    {
        // As it stands, we should have three channels: Wiki, Nearby Points, and
        // Stock Pre-Fetch.  Let's make them.
        NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager == null) return;

        // Wiki is of low importance.  It might include errors and also should
        // tell the user when it's doing something (or waiting to do something).
        NotificationChannel channel = new NotificationChannel(GHDConstants.CHANNEL_WIKI,
                getString(R.string.channel_wiki_title),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.channel_wiki_description));
        manager.createNotificationChannel(channel);

        // Nearby points, however, are of high importance.  They're sort of a
        // big deal when they happen.
        channel = new NotificationChannel(GHDConstants.CHANNEL_NEARBY_POINTS,
                getString(R.string.channel_nearby_points_title),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(getString(R.string.channel_nearby_points_description));
        channel.enableLights(true);
        channel.setLightColor(Color.WHITE);
        manager.createNotificationChannel(channel);

        // The stock prefetcher is of minimal importance.  It just sort of
        // happens in the background and should go away quickly in most cases.
        channel = new NotificationChannel(GHDConstants.CHANNEL_STOCK_PREFETCHER,
                getString(R.string.channel_stock_prefetch_title),
                NotificationManager.IMPORTANCE_MIN);
        channel.setDescription(getString(R.string.channel_stock_prefetch_description));
        manager.createNotificationChannel(channel);
    }

    /**
     * Requests a stock.  This'll come back and be handled appropriately by
     * CentralMap, which more or less amounts to handling the ErrorBanner and
     * sending the result off to the active CentralMapMode.
     *
     * @param g the Graticule (can be null for globalhashes)
     * @param cal the date
     * @param flags the {@link StockService} flags
     */
    private void requestStock(@Nullable Graticule g, @NonNull Calendar cal, int flags) {
        // Progress shows up!
        mProgress.animate().translationY(0.0f).alpha(1.0f);

        // As a request ID, we'll use the current date, because why not?
        long date = cal.getTimeInMillis();

        Intent i = new Intent(this, StockService.class)
                .putExtra(StockService.EXTRA_DATE, cal)
                .putExtra(StockService.EXTRA_GRATICULE, g)
                .putExtra(StockService.EXTRA_REQUEST_ID, date)
                .putExtra(StockService.EXTRA_REQUEST_FLAGS, flags);

        mStockReceiver.addToWaitingList(date);

        StockService.enqueueWork(this, i);
    }

    /**
     * Tells Select-A-Graticule to start.
     */
    public void enterSelectAGraticuleMode() {
        if(mSelectAGraticule) return;
        mSelectAGraticule = true;

        // We can at least get a starter Graticule for Select-A-Graticule, if
        // Expedition had one yet.
        mLastModeBundle = new Bundle();
        mCurrentMode.onSaveInstanceState(mLastModeBundle);
        mCurrentMode.cleanUp();
        mStockReceiver.clearWaitingList();
        mCurrentMode = new SelectAGraticuleMode();
        doReadyChecks();
    }

    /**
     * Tells Select-A-Graticule mode to exit, and does whatever's needed to make
     * that work.  I could sure use a better way to do this other than making
     * the method public...
     */
    public void exitSelectAGraticuleMode() {
        if(!mSelectAGraticule) return;
        mSelectAGraticule = false;

        // The result can be retrieved from the Bundle and shoved right into
        // ExpeditionMode via doReadyChecks.
        mLastModeBundle = new Bundle();
        mCurrentMode.onSaveInstanceState(mLastModeBundle);
        mCurrentMode.cleanUp();
        mStockReceiver.clearWaitingList();
        mCurrentMode = new ExpeditionMode();
        doReadyChecks();
    }

    @Override
    public void onBackPressed() {
        // If we're in Select-A-Graticule, pressing back will send us back to
        // expedition mode.  This seems obvious, especially when the default
        // implementation will close the graticule fragment anyway when the back
        // stack is popped, but we also need to do the other stuff like change
        // the menu back, stop the tap-the-map selections, etc.  Also, I really
        // wish there were a better way to do this that didn't require this
        // Activity keeping track of things.
        if(mCurrentMode instanceof SelectAGraticuleMode)
            exitSelectAGraticuleMode();
        else
            super.onBackPressed();
    }

    private boolean isReadyToGo()
    {
        return !mCurrentMode.isCleanedUp() && mMapIsReady && mLayoutComplete;
    }

    private void doReadyChecks() {
        // This should be called any time the MapFragment becomes ready.  It'll
        // check to see if it's up, starting the current mode when so.
        if(isReadyToGo()) {
            if(mCurrentMode.isInitComplete()) {
                mCurrentMode.resume();
            } else {
                mCurrentMode.setMap(mMap);
                mCurrentMode.setCentralMap(this);
                mCurrentMode.init(mLastModeBundle);
            }

            if(LocationUtil.isLocationNewEnough(mLastKnownLocation))
                mCurrentMode.onLocationChanged(mLastKnownLocation);
            invalidateOptionsMenu();

            drawKnownLocations();

            // And finally, start listening.
            startListening();
        }
    }

    private void drawKnownLocations() {
        // Now, read all the KnownLocations and put them on the map.  Remove
        // anything we had before.
        if(mKnownLocationMarkers != null) {
            for(Marker m : mKnownLocationMarkers)
                m.remove();
        }
        mKnownLocationMarkers = new LinkedList<>();

        // Now, ONLY if prefs say so...
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if(prefs.getBoolean(GHDConstants.PREF_SHOW_KNOWN_LOCATIONS, true)) {
            for(KnownLocation kl : KnownLocation.getAllKnownLocations(this)) {
                // No snippet this time; there's nothing to do with the marker
                // other than show its name.
                Marker mark = mMap.addMarker(kl.makeMarker(this));
                mKnownLocationMarkers.add(mark);
            }
        }
    }

    /**
     * Gets the {@link ErrorBanner} we currently hold.  This is mostly for the
     * {@link CentralMapMode} classes.
     *
     * @return the current ErrorBanner
     */
    public ErrorBanner getErrorBanner() {
        return mBanner;
    }

    @Override
    public void datePicked(Calendar picked) {
        // Calendar!
        mLastCalendar = picked;
        mCurrentMode.changeCalendar(mLastCalendar);
    }

    private void startListening() {
        if(!isReadyToGo() || mAlreadyListening) return;

        if(checkLocationPermissions(LOCATION_PERMISSION_REQUEST)) {
            // We want high accuracy (after all, that's the whole point of
            // Geohashing), and 1-second location updates are good enough.
            LocationRequest lRequest = LocationRequest.create();
            lRequest.setInterval(1000);
            lRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            // Stupid Android Studio annotator and how it can't tell I've
            // requested permissions already...
            try {
                mFusedLocationClient.requestLocationUpdates(lRequest, mLocationCallback, null);

                // As per the 8.3.0 services, setMyLocationEnabled is a permissions-
                // locked method.  Which, to be honest, is a good thing, really, it
                // didn't make much sense that you could turn that on without
                // permissions before.
                mMap.setMyLocationEnabled(true);

                mAlreadyListening = true;
            } catch (SecurityException se) {
                // We MUST have permissions at this point, given the call to
                // checkLocationPermissions should have returned false if not.
                // If we're in some situation where we STILL got a
                // SecurityException, calling it again won't help, because
                // that'll just bring us back here in an endless loop.  So, we
                // ignore it.
            }
        }
    }

    private void stopListening() {
        if(!mAlreadyListening) return;

        if(checkLocationPermissions(LOCATION_PERMISSION_REQUEST, true)) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            mAlreadyListening = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(permissions.length <= 0 || grantResults.length <= 0)
            return;

        // CentralMap will generally be handling location permissions.  So...
        if(grantResults[0] == PackageManager.PERMISSION_DENIED) {
            // Whoops.  We're sunk.  Go to the permission failure dialog thing.
            Bundle args = new Bundle();
            args.putInt(PermissionDeniedDialogFragment.TITLE, R.string.title_permission_location);
            args.putInt(PermissionDeniedDialogFragment.MESSAGE, R.string.explain_permission_location);

            PermissionDeniedDialogFragment frag = new PermissionDeniedDialogFragment();
            frag.setArguments(args);
            frag.show(getFragmentManager(), "PermissionDeniedDialog");

            mPermissionsDenied = true;
        } else {
            // Thankfully, we don't need to ask for forgiveness, as we've
            // got permissions right here!
            startListening();

            mPermissionsDenied = false;
        }

        if(mCurrentMode != null) mCurrentMode.permissionsDenied(mPermissionsDenied);
    }

    private void updateLastGraticule(@NonNull Info info) {
        // This'll just stash the last Graticule away in preferences so we can
        // start with the last-used one if preferences demand it as such.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = prefs.edit();

        edit.putBoolean(GHDConstants.PREF_DEFAULT_GRATICULE_GLOBALHASH, info.isGlobalHash());
        Graticule g = info.getGraticule();

        if(g != null) {
            edit.putString(GHDConstants.PREF_DEFAULT_GRATICULE_LATITUDE, g.getLatitudeString(true));
            edit.putString(GHDConstants.PREF_DEFAULT_GRATICULE_LONGITUDE, g.getLongitudeString(true));
        }

        edit.apply();

        BackupManager bm = new BackupManager(this);
        bm.dataChanged();
    }

    private boolean startFromShortcut(@Nullable Intent intent) {
        // I somehow feel this could be made more efficient...
        if(intent == null) return false;

        String action = intent.getAction();
        if(action == null) return false;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String lastLat = prefs.getString(GHDConstants.PREF_DEFAULT_GRATICULE_LATITUDE, "INVALID");
        String lastLon = prefs.getString(GHDConstants.PREF_DEFAULT_GRATICULE_LONGITUDE, "INVALID");
        boolean globalHash = prefs.getBoolean(GHDConstants.PREF_DEFAULT_GRATICULE_GLOBALHASH, false);

        Graticule g;

        try {
            g = new Graticule(lastLat, lastLon);
        } catch(Exception e) {
            // If a problem popped up, we just assume there was no
            // actual graticule data.
            g = null;
        }

        // In all cases, if this IS a shortcut, we're overwriting
        // mLastModeBundle, no matter what it was.  Obviously, if it isn't a
        // shortcut, leave mLastModeBundle alone.
        switch(action) {
            case ACTION_START_CLOSEST_HASHPOINT:
                mLastModeBundle = new Bundle();
                doStartupClosest();
                break;
            case ACTION_START_GRATICULE_PICKER:
                mLastModeBundle = new Bundle();
                doStartupPicker(g, globalHash);
                break;
            case ACTION_START_LAST_USED:
                mLastModeBundle = new Bundle();
                doStartupLastUsed(g, globalHash);
                break;
            default:
                return false;
        }

        return true;
    }

    private void startInCorrectMode() {
        // If at this point we don't have any mode bundle, we're going to the
        // prefs to figure out where we start.
        if(mLastModeBundle == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

            String startup = prefs.getString(GHDConstants.PREF_STARTUP_BEHAVIOR, GHDConstants.PREFVAL_STARTUP_CLOSEST);
            mLastModeBundle = new Bundle();

            if(startup.equals(GHDConstants.PREFVAL_STARTUP_CLOSEST)) {
                // Closest point.  The default.
                doStartupClosest();
            } else {
                // The other cases, we need to know the last-known graticule and
                // globalhashiness.
                String lastLat = prefs.getString(GHDConstants.PREF_DEFAULT_GRATICULE_LATITUDE, "INVALID");
                String lastLon = prefs.getString(GHDConstants.PREF_DEFAULT_GRATICULE_LONGITUDE, "INVALID");
                boolean globalHash = prefs.getBoolean(GHDConstants.PREF_DEFAULT_GRATICULE_GLOBALHASH, false);

                Graticule g;

                try {
                    g = new Graticule(lastLat, lastLon);
                } catch(Exception e) {
                    // If a problem popped up, we just assume there was no
                    // actual graticule data.
                    g = null;
                }

                if(startup.equals(GHDConstants.PREFVAL_STARTUP_LAST_USED)) {
                    doStartupLastUsed(g, globalHash);
                } else if(startup.equals(GHDConstants.PREFVAL_STARTUP_PICKER)) {
                    // The graticule picker.  In theory, we MAY have a graticule to
                    // start with.  With which to start.
                    doStartupPicker(g, globalHash);
                }
            }

        }
    }

    private void doStartupClosest() {
        mLastModeBundle.putBoolean(ExpeditionMode.DO_INITIAL_START, true);
        mSelectAGraticule = false;
    }

    private void doStartupLastUsed(@Nullable Graticule g, boolean globalHash) {
        mSelectAGraticule = false;

        // Last-used point.  Now, if we don't HAVE any such data, we
        // fall back to closest point behavior.
        if(g == null && !globalHash) {
            // Well, poop.
            mLastModeBundle.putBoolean(ExpeditionMode.DO_INITIAL_START, true);
        } else {
            // If we've got something, yay!  Add in data as appropriate.
            // Start with today.
            mLastModeBundle.putSerializable(CentralMapMode.CALENDAR, Calendar.getInstance());

            if(globalHash)
                mLastModeBundle.putBoolean(CentralMapMode.GLOBALHASH, true);
            if(g != null)
                mLastModeBundle.putParcelable(CentralMapMode.GRATICULE, g);
        }
    }

    private void doStartupPicker(@Nullable Graticule g, boolean globalHash) {
        mLastModeBundle.putBoolean(CentralMapMode.GLOBALHASH, globalHash);
        if(g != null)
            mLastModeBundle.putParcelable(CentralMapMode.GRATICULE, g);
        mLastModeBundle.putSerializable(CentralMapMode.CALENDAR, Calendar.getInstance());
        mSelectAGraticule = true;
    }
}