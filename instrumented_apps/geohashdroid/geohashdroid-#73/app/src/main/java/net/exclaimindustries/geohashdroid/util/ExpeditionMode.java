/*
 * ExpeditionMode.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.activities.CentralMap;
import net.exclaimindustries.geohashdroid.activities.DetailedInfoActivity;
import net.exclaimindustries.geohashdroid.fragments.CentralMapExtraFragment;
import net.exclaimindustries.geohashdroid.fragments.NearbyGraticuleDialogFragment;
import net.exclaimindustries.geohashdroid.services.StockService;
import net.exclaimindustries.geohashdroid.widgets.ErrorBanner;
import net.exclaimindustries.geohashdroid.widgets.InfoBox;
import net.exclaimindustries.geohashdroid.widgets.ZoomButtons;
import net.exclaimindustries.tools.AndroidUtil;
import net.exclaimindustries.tools.DateTools;
import net.exclaimindustries.tools.LocationUtil;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * <code>ExpeditionMode</code> is the "main" mode, where it follows one point,
 * maybe shows eight close points, and allows for wiki mode or whatnot.
 */
public class ExpeditionMode
        extends CentralMap.CentralMapMode
        implements GoogleMap.OnInfoWindowClickListener,
                   GoogleMap.OnCameraMoveListener,
                   NearbyGraticuleDialogFragment.NearbyGraticuleClickedCallback,
                   CentralMapExtraFragment.CloseListener,
                   ZoomButtons.ZoomButtonListener {
    private static final String DEBUG_TAG = "ExpeditionMode";

    private static final String NEARBY_DIALOG = "nearbyDialog";
    private static final String EXTRA_FRAGMENT_BACK_STACK = "ExtraFragment";

    public static final String DO_INITIAL_START = "doInitialStart";

    private boolean mReplacingFragment = false;
    private boolean mVictoryReported = false;

    // This will hold all the nearby points we come up with.  They'll be
    // removed any time we get a new Info in.  It's a map so that we have a
    // quick way to switch to a new Info without having to call StockService.
    private final Map<Marker, Info> mNearbyPoints = new HashMap<>();

    private Info mCurrentInfo;
    private DisplayMetrics mMetrics;

    // We want to remember these long-term.  If a stock lookup fails in a start-
    // from-last-used-graticule situation, we'll want to re-use these if the
    // user switches the date.
    private Graticule mStartingGraticule;
    private boolean mStartingGlobalHash;

    // This is only used in really weird startup cases.  Otherwise, we'll be
    // explicitly using the Calendar from changeCalendar() or implicitly using
    // it from mCurrentInfo.
    private Calendar mInitialCalendar;

    private Location mInitialCheckLocation;

    private InfoBox mInfoBox;
    private CentralMapExtraFragment mExtraFragment;
    private ZoomButtons mZoomButtons;

    // These booleans tell us that the location handler is waiting to act on a
    // result in some manner other than the victory listener or updating the
    // InfoBox.
    private boolean mWaitingOnInitialZoom = false;
    private boolean mWaitingOnEmptyStart = false;
    private boolean mWaitingOnZoomToUser = false;

    // Then there's this one empty start boolean.
    private boolean mWaitingOnEmptyStartInfo = false;

    private Rect mMarkerDimens = new Rect();
    private Rect mInfoBoxDimens = new Rect();

    private int mMarkerWidth = -1;
    private int mMarkerHeight = -1;

    private View.OnClickListener mInfoBoxClicker = v -> launchExtraFragment(CentralMapExtraFragment.FragmentType.DETAILS);

    @Override
    public void setCentralMap(@NonNull CentralMap centralMap) {
        super.setCentralMap(centralMap);

        // Get the size of the marker.  We'll need this for later.
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(centralMap.getResources(), R.drawable.final_destination, opts);
        mMarkerWidth = opts.outWidth;
        mMarkerHeight = opts.outHeight;

        // Build up our metrics, too.
        mMetrics = new DisplayMetrics();
        centralMap.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    @Override
    public void init(@Nullable Bundle bundle) {
        // We listen to the map.  A lot.  For many, many reasons.
        mMap.setOnInfoWindowClickListener(this);
        mMap.setOnCameraMoveListener(this);

        // Set a title to begin with.  We'll get a new one soon, hopefully.
        setTitle(R.string.app_name);

        // Do we have a Bundle to un-Bundlify?
        if(bundle != null) {
            // And if we DO have a Bundle, does that Bundle have an Info?
            mCurrentInfo = bundle.getParcelable(INFO);
            if(mCurrentInfo != null) {
                // Info!  Yay!  We can request a stock based on that!  Okay,
                // technically the Info should already have the stock we need,
                // but this also lets us get the nearby points if need be.
                requestStock(mCurrentInfo.getGraticule(), mCurrentInfo.getCalendar(), StockService.FLAG_USER_INITIATED | (needsNearbyPoints() ? StockService.FLAG_INCLUDE_NEARBY_POINTS : 0));
            } else if((bundle.containsKey(GRATICULE) || bundle.containsKey(GLOBALHASH)) && bundle.containsKey(CALENDAR)) {
                // We've got a request to make!  Chances are, StockService will
                // have this in cache.
                mStartingGraticule = bundle.getParcelable(GRATICULE);
                mStartingGlobalHash = bundle.getBoolean(GLOBALHASH, false);
                Calendar cal = (Calendar) bundle.getSerializable(CALENDAR);

                // We only go through with this if we have a Calendar and
                // either a globalhash or a Graticule.
                if(cal != null && (mStartingGlobalHash || mStartingGraticule != null)) {
                    requestStock((mStartingGlobalHash ? null : mStartingGraticule), cal, StockService.FLAG_USER_INITIATED | (needsNearbyPoints() ? StockService.FLAG_INCLUDE_NEARBY_POINTS : 0));
                }
            } else if(bundle.getBoolean(DO_INITIAL_START, false) && !arePermissionsDenied()) {
                // If we didn't get an Info, well, maybe there's an initial
                // start to fire off?
                doEmptyStart();
            }
        }

        // Also, let's get that InfoBox taken care of.
        mInfoBox = new InfoBox(mCentralMap);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            params.addRule(RelativeLayout.ALIGN_PARENT_END);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        ((RelativeLayout)mCentralMap.findViewById(R.id.map_content)).addView(mInfoBox, params);

        // Start things in motion IF the preference says to do so.
        if(showInfoBox()) {
            mInfoBox.animateInfoBoxVisible(true);
        }

        mInfoBox.setOnClickListener(mInfoBoxClicker);

        // Check for the extra fragment container first.  If the screen's too
        // small for it to fit, Android will remove it, mostly by shifting to
        // the smaller-form layout, which is really super convenient for us in
        // the case of multi-window stuff.  What's more, that also changes all
        // the actions related to picking the fragments to go to the activities
        // anyway.  Lucky us!
        View fragmentContainer = mCentralMap.findViewById(R.id.extra_fragment_container);
        if(fragmentContainer != null) {
            // Plus, if the detailed info fragment's already there, make its
            // container go visible, too.
            FragmentManager manager = mCentralMap.getFragmentManager();
            mExtraFragment = (CentralMapExtraFragment) manager.findFragmentById(R.id.extra_fragment_container);
            if(mExtraFragment != null) {
                fragmentContainer.setVisibility(View.VISIBLE);
                mExtraFragment.setCloseListener(this);
            }
        }

        // The zoom buttons also need to go in.
        mZoomButtons = new ZoomButtons(mCentralMap);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        ((RelativeLayout)mCentralMap.findViewById(R.id.map_content)).addView(mZoomButtons, params);
        mZoomButtons.setListener(this);
        mZoomButtons.showMenu(false);
        mZoomButtons.setButtonEnabled(ZoomButtons.ZOOM_DESTINATION, false);
        mZoomButtons.setButtonEnabled(ZoomButtons.ZOOM_FIT_BOTH, false);

        permissionsDenied(arePermissionsDenied());

        mInitComplete = true;
    }

    @Override
    public void cleanUp() {
        super.cleanUp();

        // First, get rid of the listens.
        if(mMap != null) {
            mMap.setOnInfoWindowClickListener(null);
            mMap.setOnCameraMoveListener(null);
        }

        // Remove the nearby points, too.  The superclass took care of the final
        // destination marker for us.
        removeNearbyPoints();

        // The InfoBox should also go away at this point.
        if(mInfoBox != null) {
            mInfoBox.animateInfoBoxOutWithEndAction(() -> ((ViewGroup) mCentralMap.findViewById(R.id.map_content)).removeView(mInfoBox));
        }

        // Plus, any bonus fragment we might have.
        if(mExtraFragment != null)
            extraFragmentClosing(mExtraFragment);

        // Zoom buttons, you go away, too.  In this case, we animate the entire
        // block away ourselves and remove it when done with a callback.
        if(mZoomButtons != null) {
            mZoomButtons.animate().translationX(-mZoomButtons.getWidth()).withEndAction(() -> ((ViewGroup) mCentralMap.findViewById(R.id.map_content)).removeView(mZoomButtons));
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle bundle) {
        // At instance save time, stash away the last Info we knew about.  If we
        // have anything at all, it'll always be an Info.  If we don't have one,
        // we weren't displaying anything, and thus don't need to stash a
        // Calendar, Graticule, etc.
        bundle.putParcelable(INFO, mCurrentInfo);

        // Also, if we were in the middle of waiting on the empty start, write
        // that out to the bundle.  It'll come back in and we can start the
        // whole process anew.
        if(mWaitingOnEmptyStart)
            bundle.putBoolean(DO_INITIAL_START, true);
    }

    @Override
    public void pause() {
        // Hey, wow, we're not doing anything here anymore!
    }

    @Override
    public void resume() {
        if(!mInitComplete) return;

        // If need be, start listening again!
        if(mWaitingOnInitialZoom)
            doInitialZoom();
        else
            doReloadZoom();

        // Also if need be, try that empty start again!
        if(mWaitingOnEmptyStart)
            doEmptyStart();

        if(showInfoBox()) {
            mInfoBox.animateInfoBoxVisible(true);
        } else {
            mInfoBox.animateInfoBoxVisible(false);
        }

        // Re-check the nearby points pref.  If that changed, we need to either
        // remove or add the points.  Actually, to keep it simple, just wipe
        // the old points anyway and only draw them back if the pref says so.
        // Remember, since this isn't really an Activity lifecycle, resume() is
        // NOT called immediately after init(), so this will only happen when
        // we're coming back from somewhere else (like, say, Preferences, where
        // this sort of thing might change).
        if(mCurrentInfo != null) {
            removeNearbyPoints();

            if(needsNearbyPoints()) {
                // Hey, let's use the little-used FLAG_AUTO_INITIATED!  That'll
                // do as a flag that tells us we're ONLY waiting on nearby
                // points!
                requestStock(mCurrentInfo.getGraticule(), mCurrentInfo.getCalendar(), StockService.FLAG_INCLUDE_NEARBY_POINTS | StockService.FLAG_AUTO_INITIATED);
            }
        }

        permissionsDenied(arePermissionsDenied());
    }

    @Override
    public void onCreateOptionsMenu(Context c, MenuInflater inflater, Menu menu) {
        inflater.inflate(R.menu.centralmap_expedition, menu);

        // Maps?  You there?
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse("geo:0,0?q=loc:0,0"));
        if(!AndroidUtil.isIntentAvailable(c, i))
            menu.removeItem(R.id.action_send_to_maps);

        // Make sure radar is removed if there's no radar to radar our radar.
        // Radar radar radar radar radar.
        if(!AndroidUtil.isIntentAvailable(c, GHDConstants.ACTION_SHOW_RADAR))
            menu.removeItem(R.id.action_send_to_radar);

        // If we don't have any Info yet, we can't have things that depend on
        // it, such as wiki, details, Send To Maps, or Send To Radar.
        if(mCurrentInfo == null) {
            menu.removeItem(R.id.action_send_to_maps);
            menu.removeItem(R.id.action_send_to_radar);
            menu.removeItem(R.id.action_details);
            menu.removeItem(R.id.action_wiki);
            menu.removeItem(R.id.action_try_tomorrow);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_selectagraticule: {
                // It's Select-A-Graticule Mode!  At long last!
                mCentralMap.enterSelectAGraticuleMode();
                return true;
            }
            case R.id.action_details: {
                // Here, the user's pressed the menu item for details, probably
                // either because they don't have the infobox visible on the
                // main display or they were poking every option and wanted to
                // see what this would do.  Here's what it do:
                launchExtraFragment(CentralMapExtraFragment.FragmentType.DETAILS);
                return true;
            }
            case R.id.action_wiki: {
                // Same as with details, but with the wiki instead.
                launchExtraFragment(CentralMapExtraFragment.FragmentType.WIKI);
                return true;
            }
            case R.id.action_send_to_maps: {
                // Juuuuuuust like in DetailedInfoActivity...
                if(mCurrentInfo != null) {
                    // To the map!
                    Intent i = new Intent();
                    i.setAction(Intent.ACTION_VIEW);

                    String location = mCurrentInfo.getLatitude() + "," + mCurrentInfo.getLongitude();

                    i.setData(Uri.parse("geo:0,0?q=loc:"
                            + location
                            + "("
                            + mCentralMap.getString(
                            R.string.send_to_maps_point_name,
                            DateFormat.getDateInstance(DateFormat.LONG).format(
                                    mCurrentInfo.getCalendar().getTime())) + ")&z=15"));
                    mCentralMap.startActivity(i);
                } else {
                    Toast.makeText(mCentralMap, R.string.error_no_data_to_maps, Toast.LENGTH_LONG).show();
                }

                return true;
            }
            case R.id.action_send_to_radar: {
                // Someone actually picked radar!  How 'bout that?
                if(mCurrentInfo != null) {
                    Intent i = new Intent(GHDConstants.ACTION_SHOW_RADAR);
                    i.putExtra("latitude", (float) mCurrentInfo.getLatitude());
                    i.putExtra("longitude", (float) mCurrentInfo.getLongitude());
                    mCentralMap.startActivity(i);
                } else {
                    Toast.makeText(mCentralMap, R.string.error_no_data_to_radar, Toast.LENGTH_LONG).show();
                }

                return true;
            }
            case R.id.action_try_tomorrow: {
                // Trying tomorrow is easy: Just get today, make it tomorrow,
                // and try it.  What's more, we've already got the mechanisms in
                // place to handle what happens if tomorrow doesn't exist yet.
                if(mCurrentInfo != null) {
                    Calendar cal = (Calendar)mCurrentInfo.getCalendar().clone();
                    cal.add(Calendar.DATE, 1);
                    changeCalendar(cal);
                }
            }
        }

        return false;
    }

    @Override
    public void handleInfo(Info info, Info[] nearby, int flags) {
        // PULL!
        if(mInitComplete) {
            mCentralMap.getErrorBanner().animateBanner(false);

            if((flags & FLAG_FROM_NOTIFICATION) != 0) {
                // We've got an Info here.  We're also (at least) paused.  So
                // let's just try inserting it into the usual flow, minus the
                // point where we check for the closest Info...
                if(!info.isGlobalHash())
                    requestStock(info.getGraticule(), info.getCalendar(), StockService.FLAG_USER_INITIATED | (needsNearbyPoints() ? StockService.FLAG_INCLUDE_NEARBY_POINTS : 0));
                else {
                    setInfo(info);
                    doNearbyPoints(null);
                }
            } else if(mWaitingOnEmptyStartInfo && !info.isGlobalHash()) {
                mWaitingOnEmptyStartInfo = false;
                // Coming in from the initial setup, we might have nearbys.  Get
                // the closest one.
                Info inf = Info.measureClosest(mInitialCheckLocation, info, nearby);

                // Presto!  We've got our Graticule AND Calendar!  Now, to make
                // sure we've got all the nearbys set properly, ask StockService
                // for the data again, this time using the best one.  We'll get
                // it back in the else field quickly, as it's cached now.
                requestStock(inf.getGraticule(), inf.getCalendar(), StockService.FLAG_USER_INITIATED | (needsNearbyPoints() ? StockService.FLAG_INCLUDE_NEARBY_POINTS : 0));
            } else {
                if((flags & StockService.FLAG_AUTO_INITIATED) == 0) {
                    setInfo(info);
                }

                doNearbyPoints(nearby);
            }
        }
    }

    @Override
    public void handleLookupFailure(int reqFlags, int responseCode) {
        // Nothing here yet.
    }

    private void addNearbyPoint(@NonNull Info info) {
        final Graticule g = info.getGraticule();
        if(g == null) return;

        // This will get called repeatedly up to eight times (in rare cases,
        // five times) when we ask for nearby points.  All we need to do is put
        // those points on the map, and stuff them in the map.  Two different
        // varieties of map.
        synchronized(mNearbyPoints) {
            // The title might be a wee bit unwieldy, as it also has to include
            // the graticule's location.  We DO know that this isn't a
            // Globalhash, though.
            String title;
            String gratString = g.getLatitudeString(false) + " " + g.getLongitudeString(false);

            // We have strings for today, tomorrow, and the day after tomorrow.
            // If it's none of those (i.e. either a retro hash or we have stock
            // data more than two days out, like for holidays), go with the
            // date.
            Calendar cal = Calendar.getInstance();
            Calendar infoCal = info.getCalendar();

            if(DateTools.isSameDate(infoCal, cal)) {
                title = mCentralMap.getString(R.string.marker_title_nearby_today_hashpoint,
                        gratString);
            } else if(DateTools.isTomorrow(infoCal, cal)) {
                title = mCentralMap.getString(R.string.marker_title_nearby_tomorrow_hashpoint,
                        gratString);
            } else if(DateTools.isDayAfterTomorrow(infoCal, cal)) {
                title = mCentralMap.getString(R.string.marker_title_nearby_doubletomorrow_hashpoint,
                        gratString);
            } else {
                title = mCentralMap.getString(R.string.marker_title_nearby_retro_hashpoint,
                        DateFormat.getDateInstance(DateFormat.LONG).format(info.getDate()),
                        gratString);
            }

            // Snippet!  Snippet good.
            String snippet = UnitConverter.makeFullCoordinateString(mCentralMap, info.getFinalLocation(), false, UnitConverter.OUTPUT_LONG);

            Marker nearby = mMap.addMarker(new MarkerOptions()
                    .position(info.getFinalDestinationLatLng())
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.final_destination_disabled))
                    .anchor(0.5f, 1.0f)
                    .title(title)
                    .snippet(snippet));

            mNearbyPoints.put(nearby, info);

            // Finally, make sure it should be visible.  Do this per-marker, as
            // we're not always sure we've got the full set of eight (edge case
            // involving the poles) or if all of them will come in at the same
            // time (edge cases involving 30W or 180E/W).
            checkMarkerVisibility(nearby);
        }
    }

    private void checkMarkerVisibility(Marker m) {
        // On a camera change, we need to determine if the nearby markers
        // (assuming they exist to begin with) need to be drawn.  If they're too
        // far away, they'll get in a jumbled mess with the final destination
        // flag, and we don't want that.  This is more or less similar to the
        // clustering support in the Google Maps API v2 utilities, but since we
        // always know the markers will be in a very specific cluster, we can
        // just simplify it all into this.

        // First, if we're not in the middle of an expedition, don't worry about
        // it.
        if(mCurrentInfo != null) {
            // Figure out how far this marker is from the final point.  Hooray
            // for Pythagoras!
            Point dest = mMap.getProjection().toScreenLocation(mDestination.getPosition());
            Point mark = mMap.getProjection().toScreenLocation(m.getPosition());

            // toScreenLocation gives us values as screen pixels, not display
            // pixels.  Let's convert that to display pixels for sanity's sake.
            double dist = Math.sqrt(Math.pow((dest.x - mark.x), 2) + Math.pow(dest.y - mark.y, 2)) / mMetrics.density;

            boolean visible = true;

            // 50dp should be roughly enough.  If I need to change this later,
            // it's going to be because the images will scale by pixel density.
            if(dist < 50)
                visible = false;

            m.setVisible(visible);
        }
    }

    private void checkInfoBoxFading() {
        if(mCurrentInfo == null) return;

        boolean fade;
        mInfoBox.getLocationRect(mInfoBoxDimens);

        // First, check the final destination marker.  The pin on the flag is
        // where the point is, so we would want to check against the entire
        // height of it to see if it crashes into the InfoBox.
        Projection proj = mMap.getProjection();
        Point p = proj.toScreenLocation(mCurrentInfo.getFinalDestinationLatLng());
        mMarkerDimens.set(p.x - (mMarkerWidth / 2),
                p.y - mMarkerHeight,
                p.x + (mMarkerWidth / 2),
                p.y);
        fade = Rect.intersects(mMarkerDimens, mInfoBoxDimens);

        if(!fade) {
            // Continue with current location checking.  We'll use the same
            // bounds as the final destination marker, just for convenience.
            Location loc = getLastKnownLocation();
            if(LocationUtil.isLocationNewEnough(loc)) {
                p = proj.toScreenLocation(new LatLng(loc.getLatitude(), loc.getLongitude()));
                // Except, remember, the current location marker is pinned at
                // the CENTER of the image.  Tricky!
                mMarkerDimens.set(p.x - (mMarkerWidth / 2),
                        p.y - (mMarkerHeight / 2),
                        p.x + (mMarkerWidth / 2),
                        p.y + (mMarkerHeight / 2));

                fade = Rect.intersects(mMarkerDimens, mInfoBoxDimens);
            }
        }

        mInfoBox.fadeOutInfoBox(fade);
    }

    private void doNearbyPoints(@Nullable Info[] nearby) {
        removeNearbyPoints();

        // We should just be able to toss one point in for each Info here.
        if(nearby != null) {
            for(Info info : nearby)
                addNearbyPoint(info);
        }
    }

    private void removeNearbyPoints() {
        synchronized(mNearbyPoints) {
            for(Marker m : mNearbyPoints.keySet()) {
                m.remove();
            }
            mNearbyPoints.clear();
        }
    }

    private void setInfo(final Info info) {
        mCurrentInfo = info;
        mVictoryReported = false;

        // Redraw the menu as need be, too.
        mCentralMap.invalidateOptionsMenu();

        // Set the infobox in motion as well.
        if(showInfoBox()) {
            mInfoBox.animateInfoBoxVisible(true);
        } else {
            mInfoBox.animateInfoBoxVisible(false);
        }

        if(!mInitComplete) return;

        removeDestinationPoint();

        // The InfoBox ALWAYS gets the Info.
        mInfoBox.setInfo(info);

        // Zoom needs updating, too.
        setZoomButtonsEnabled();

        // As does the detail fragment, if it's there.
        if(mExtraFragment != null)
            mExtraFragment.setInfo(info);

        // I suppose a null Info MIGHT come in.  I don't know how yet, but sure,
        // let's assume a null Info here means we just don't render anything.
        if(mCurrentInfo != null) {
            mCentralMap.runOnUiThread(() -> {
                // Marker!
                addDestinationPoint(info);

                // With an Info in hand, we can also change the title.
                StringBuilder newTitle = new StringBuilder();
                Graticule g = mCurrentInfo.getGraticule();
                if(g == null)
                    newTitle.append(mCentralMap.getString(R.string.title_part_globalhash));
                else {
                    newTitle.append(g.getLatitudeString(false)).append(' ').append(g.getLongitudeString(false));
                }
                newTitle.append(", ");
                newTitle.append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(mCurrentInfo.getDate()));
                setTitle(newTitle.toString());

                // Now, the Mercator projection that the map uses clips at
                // around 85 degrees north and south.  If that's where the
                // point is (if that's the Globalhash or if the user
                // legitimately lives in Antarctica), we'll still try to
                // draw it, but we'll throw up a warning that the marker
                // might not show up.  Sure is a good thing an extreme south
                // Globalhash showed up when I was testing this, else I
                // honestly might've forgot.
                ErrorBanner banner = mCentralMap.getErrorBanner();
                if(Math.abs(mCurrentInfo.getLatitude()) > 85) {
                    banner.setErrorStatus(ErrorBanner.Status.WARNING);
                    banner.setText(mCentralMap.getString(R.string.warning_outside_of_projection));
                    banner.animateBanner(true);
                }

                // Finally, try to zoom the map to where it needs to be,
                // assuming we're connected to the APIs and have a location.
                // This is why you make sure things are ready before you
                // call init.
                doInitialZoom();
            });

        } else {
            // Otherwise, make sure the title's back to normal.
            setTitle(R.string.app_name);
        }
    }

    private void zoomToIdeal(Location current) {
        // We can't do an ideal zoom if we don't have permissions!
        if(arePermissionsDenied()) {
            Log.i(DEBUG_TAG, "Tried to do an ideal zoom after permissions were denied, ignoring...");
            return;
        }

        // Where "current" means the user's current location, and we're zooming
        // relative to the final destination, if we have it yet.  Let's check
        // that latter part first.
        if(mCurrentInfo == null) {
            Log.i(DEBUG_TAG, "zoomToIdeal was called before an Info was set, ignoring...");
            return;
        }

        // As a side note, yes, I COULD probably mash this all down to one line,
        // but I want this to be readable later without headaches.
        LatLngBounds bounds = LatLngBounds.builder()
                .include(new LatLng(current.getLatitude(), current.getLongitude()))
                .include(mCurrentInfo.getFinalDestinationLatLng())
                .build();

        CameraUpdate cam = CameraUpdateFactory.newLatLngBounds(bounds, mCentralMap.getResources().getDimensionPixelSize(R.dimen.map_zoom_padding));

        mMap.animateCamera(cam);
    }

    private void zoomToPoint(Location loc) {
        LatLng dest = new LatLng(loc.getLatitude(), loc.getLongitude());
        CameraUpdate cam = CameraUpdateFactory.newLatLngZoom(dest, 15.0f);
        mMap.animateCamera(cam);
    }

    private void zoomToInitialCurrentLocation(Location loc) {
        // This is called during initial lookup, just to make sure the map's at
        // a location OTHER than dead zero while we potentially wait for a stock
        // value to come in.  The zoom will be to half a degree around the
        // current point, just to grab an entire graticule's space.
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(new LatLng(loc.getLatitude() - .5, loc.getLongitude() - .5));
        builder.include(new LatLng(loc.getLatitude() - .5, loc.getLongitude() + .5));
        builder.include(new LatLng(loc.getLatitude() + .5, loc.getLongitude() - .5));
        builder.include(new LatLng(loc.getLatitude() + .5, loc.getLongitude() + .5));
        CameraUpdate cam = CameraUpdateFactory.newLatLngBounds(builder.build(), mCentralMap.getResources().getDimensionPixelSize(R.dimen.map_zoom_padding));
        try {
            // And don't worry, when the stock comes in, that'll fire off a new
            // animateCamera() call, which in turn will cancel this one.
            mMap.animateCamera(cam);
        } catch(IllegalStateException ise) {
            // I really hope it's ready to go by now...
            Log.w(DEBUG_TAG, "The map isn't ready for animating yet!");
        }
    }

    private void doReloadZoom() {
        // This happens on every resume().  The only real difference is that
        // this is protected by a preference, while initial zoom happens any
        // time.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mCentralMap);
        boolean autoZoom = prefs.getBoolean(GHDConstants.PREF_AUTOZOOM, true);

        if(autoZoom) doInitialZoom();
    }

    private void doInitialZoom() {
        // We can't do the initial zoom if we don't have permissions!
        if(arePermissionsDenied()) {
            Log.i(DEBUG_TAG, "Tried to do an initial zoom after permissions were denied, ignoring...");
            return;
        }

        // We want the last known location to be at least SANELY recent.
        Location loc = getLastKnownLocation();
        if(LocationUtil.isLocationNewEnough(loc)) {
            zoomToIdeal(loc);
        } else {
            // Otherwise, wait for the first update and use that for an initial
            // zoom.
            ErrorBanner banner = mCentralMap.getErrorBanner();
            banner.setErrorStatus(ErrorBanner.Status.NORMAL);
            banner.setText(mCentralMap.getText(R.string.search_label).toString());
            banner.setCloseVisible(false);
            banner.animateBanner(true);

            mWaitingOnInitialZoom = true;

            // While we wait, though, zoom in on the destination point, if we
            // have one.
            if(mCurrentInfo != null) {
                zoomToInitialCurrentLocation(mCurrentInfo.getFinalLocation());
            }
        }
    }

    private void doEmptyStart() {
        // We can't do the empty start if we don't have permissions!
        if(arePermissionsDenied()) {
            Log.i(DEBUG_TAG, "Tried to do an empty start after permissions were denied, ignoring...");
            return;
        }

        Log.d(DEBUG_TAG, "Here comes the empty start...");

        // For an initial start, first things first, we ask for the current
        // location.  If it's new enough, we can go with that, as usual.
        Location loc = getLastKnownLocation();
        if(LocationUtil.isLocationNewEnough(loc)) {
            mInitialCheckLocation = loc;
            mWaitingOnEmptyStartInfo = true;
            zoomToInitialCurrentLocation(loc);
            requestStock(new Graticule(loc), Calendar.getInstance(), StockService.FLAG_USER_INITIATED | StockService.FLAG_FIND_CLOSEST);
        } else {
            // Otherwise, it's off to the races.
            ErrorBanner banner = mCentralMap.getErrorBanner();
            banner.setErrorStatus(ErrorBanner.Status.NORMAL);
            banner.setText(mCentralMap.getText(R.string.search_label).toString());
            banner.setCloseVisible(false);
            banner.animateBanner(true);

            mWaitingOnEmptyStart = true;
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        // If a nearby marker's info window was clicked, that means we can
        // switch to another point.
        if(mNearbyPoints.containsKey(marker)) {
            final Info newInfo = mNearbyPoints.get(marker);

            // Get the last-known location (if possible) and prompt the user
            // with a distance.  Then, we've got a fragment that'll do this sort
            // of work for us.
            NearbyGraticuleDialogFragment frag = NearbyGraticuleDialogFragment.newInstance(newInfo, getLastKnownLocation());
            frag.setCallback(this);
            frag.show(mCentralMap.getFragmentManager(), NEARBY_DIALOG);
        }
    }

    @Override
    public void onCameraMove() {
        // We're going to check visibility on each marker individually.  This
        // might make some of them vanish while others remain on, owing to our
        // good friend the Pythagorean Theorem and neat Mercator projection
        // tricks.
        for(Marker m : mNearbyPoints.keySet())
            checkMarkerVisibility(m);

        // Also, let's get the infobox faded as need be.
        checkInfoBoxFading();
    }

    @Override
    public void nearbyGraticuleClicked(Info info) {
        // Info!
        requestStock(info.getGraticule(), info.getCalendar(), StockService.FLAG_USER_INITIATED | (needsNearbyPoints() ? StockService.FLAG_INCLUDE_NEARBY_POINTS : 0));
    }

    @Override
    public void changeCalendar(@NonNull Calendar newDate) {
        // New Calendar!  That means we ask for more stock data!  It doesn't
        // necessarily mean a new point is coming in, but it does mean we're
        // making a request, at least.  The StockService broadcast will let us
        // know what's going on later.
        Graticule g;

        // It should be pretty safe to just change it like this every time.
        mInitialCalendar = newDate;

        // The Graticule we use is either the one in our current Info (thus
        // recycling our current position), whatever the initial check came up
        // with, or the last-used graticule if we're in that startup mode.  The
        // latter two are in case we never came up with a valid Info if, for
        // instance, the check was made before the opening of the DJIA and the
        // user decided to pick a previous day.
        boolean isGlobalHash = false;

        if(mCurrentInfo != null) {
            // If we have a current info, use its graticule to make a new stock
            // out of the calendar.
            g = mCurrentInfo.getGraticule();
            isGlobalHash = mCurrentInfo.isGlobalHash();
        } else if(mInitialCheckLocation != null) {
            // If not, we might have an initial check location, so we can get
            // started from there.
            g = new Graticule(mInitialCheckLocation);
        } else {
            // If not, we're in Last Used Graticule mode, we failed the first
            // stock lookup, and we're changing the date.  Use the known
            // starting graticule.
            g = mStartingGraticule;
            isGlobalHash = mStartingGlobalHash;
        }

        // If we didn't get a Graticule back from any of that (AND this isn't a
        // Globalhash), then we're clearly not ready to make stock requests and
        // are currently waiting for an initial location (or for the user to
        // switch to SelectAGraticuleMode instead).
        if(g != null || isGlobalHash)
            requestStock(g, newDate, StockService.FLAG_USER_INITIATED | (needsNearbyPoints() ? StockService.FLAG_INCLUDE_NEARBY_POINTS : 0));
    }

    private boolean needsNearbyPoints() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mCentralMap);
        return prefs.getBoolean(GHDConstants.PREF_NEARBY_POINTS, true);
    }

    private boolean showInfoBox() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mCentralMap);
        return mCurrentInfo != null && prefs.getBoolean(GHDConstants.PREF_INFOBOX, true);
    }

    private void launchExtraFragment(@NonNull CentralMapExtraFragment.FragmentType type) {
        // First off, ignore this if there's no Info yet.
        if(mCurrentInfo == null) return;

        // Ask CentralMap if there's a fragment container in this layout.
        // If so (tablet layouts), add it to the current screen.  If not
        // (phone layouts), jump off to the dedicated activity.
        View container = mCentralMap.findViewById(R.id.extra_fragment_container);
        if(container == null) {
            // To the Activity!
            Intent i = CentralMapExtraFragment.makeIntentForType(mCentralMap, type);
            i.putExtra(DetailedInfoActivity.INFO, mCurrentInfo);
            mCentralMap.startActivity(i);
        } else {
            // Check to see if the fragment's already there.
            FragmentManager manager = mCentralMap.getFragmentManager();
            CentralMapExtraFragment f;
            try {
                f = (CentralMapExtraFragment) manager.findFragmentById(R.id.extra_fragment_container);
            } catch(ClassCastException cce) {
                f = null;
            }

            // Make the bundle arguments.  We want to argue a whole bundle.
            Bundle args = new Bundle();
            args.putParcelable(CentralMapExtraFragment.INFO, mCurrentInfo);
            args.putBoolean(CentralMapExtraFragment.PERMISSIONS_DENIED, arePermissionsDenied());

            if(f == null) {
                // It's not there!  Make it be there!
                mExtraFragment = CentralMapExtraFragment.makeFragmentForType(type);
                mExtraFragment.setArguments(args);
                mExtraFragment.setCloseListener(this);

                FragmentTransaction trans = manager.beginTransaction();
                trans.replace(R.id.extra_fragment_container, mExtraFragment, EXTRA_FRAGMENT_BACK_STACK);
                trans.addToBackStack(EXTRA_FRAGMENT_BACK_STACK);
                trans.commit();

                // Also, due to how the layout works, the container also needs
                // to go visible now.
                container.setVisibility(View.VISIBLE);
            } else {
                // Okay, something's already there.  Is it the same type of
                // fragment we're trying to launch?
                if(type == f.getType()) {
                    // It is!  Just dismiss it, then.
                    clearExtraFragment();
                } else {
                    // It isn't.  Well, that means we need to replace the old
                    // one.  However, we also need to make sure the destroy
                    // call won't trigger the hide-the-container code in here.
                    // So...
                    mReplacingFragment = true;

                    mExtraFragment = CentralMapExtraFragment.makeFragmentForType(type);
                    mExtraFragment.setArguments(args);
                    mExtraFragment.setCloseListener(this);

                    FragmentTransaction trans = manager.beginTransaction();
                    trans.replace(R.id.extra_fragment_container, mExtraFragment, EXTRA_FRAGMENT_BACK_STACK);
                    trans.addToBackStack(EXTRA_FRAGMENT_BACK_STACK);
                    trans.commit();
                }
            }
        }
    }

    private void clearExtraFragment() {
        // This simply clears out the extra fragment.
        FragmentManager manager = mCentralMap.getFragmentManager();
        try {
            manager.popBackStack(EXTRA_FRAGMENT_BACK_STACK, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } catch(IllegalStateException ise) {
            // We might find ourselves here during shutdown time.  CentralMap
            // triggers its onSaveInstanceState before onDestroy, onDestroy
            // calls cleanUp, cleanUp comes here, and FragmentManager throws a
            // fit if you try to pop the back stack AFTER onSaveInstanceState on
            // an Activity.  In lieu of making more methods in CentralMapMode to
            // implement, we'll just catch the exception and ignore it.
        }
    }

    @Override
    public void extraFragmentClosing(CentralMapExtraFragment fragment) {
        // On the close button, pop the back stack.
        clearExtraFragment();
    }

    @Override
    public void extraFragmentDestroying(CentralMapExtraFragment fragment) {
        // And now that it's being destroyed, hide the container, unless it's
        // being replaced.
        if(mReplacingFragment) {
            mReplacingFragment = false;
        } else {
            View container = mCentralMap.findViewById(R.id.extra_fragment_container);

            if(container != null)
                container.setVisibility(View.GONE);
            else
                Log.w(DEBUG_TAG, "We got extraFragmentDestroying when there's no container in CentralMap for it!  The hell?");

            mExtraFragment = null;
        }
    }

    @Override
    public void zoomButtonPressed(View container, int which) {
        // BEEP.
        switch(which) {
            case ZoomButtons.ZOOM_FIT_BOTH:
                doInitialZoom();
                break;
            case ZoomButtons.ZOOM_DESTINATION:
                // Assuming we already have the destination...
                if(mCurrentInfo == null) {
                    Log.e(DEBUG_TAG, "Tried to zoom to the destination when there is no destination set!");
                } else {
                    zoomToPoint(mCurrentInfo.getFinalLocation());
                }
                break;
            case ZoomButtons.ZOOM_USER:
                // Hopefully the user's already got a valid location.  Else...
                Location loc = getLastKnownLocation();
                if(LocationUtil.isLocationNewEnough(loc)) {
                    zoomToPoint(loc);
                } else {
                    // Otherwise, wait for the first update and use that for the
                    // user's location.
                    ErrorBanner banner = mCentralMap.getErrorBanner();
                    banner.setErrorStatus(ErrorBanner.Status.NORMAL);
                    banner.setText(mCentralMap.getText(R.string.search_label).toString());
                    banner.setCloseVisible(false);
                    banner.animateBanner(true);

                    mWaitingOnZoomToUser = true;
                }

                break;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // This listener handles all listening duties.  We've got a few
        // booleans that tell us what's waiting to be done.
        if(mWaitingOnInitialZoom) {
            mWaitingOnInitialZoom = false;

            if(!isCleanedUp()) {
                mCentralMap.getErrorBanner().animateBanner(false);
                zoomToIdeal(location);
            }
        }

        if(mWaitingOnEmptyStart) {
            mWaitingOnEmptyStart = false;
            mWaitingOnEmptyStartInfo = true;

            if(!isCleanedUp()) {
                mInitialCheckLocation = location;

                // First, zoom to the location.  This'll at least give us
                // something other than the center of the map until the
                // hashpoint comes in.
                zoomToInitialCurrentLocation(location);

                // Second, ask for a stock using that location.
                if(mInitialCalendar == null) mInitialCalendar = Calendar.getInstance();
                requestStock(new Graticule(location), mInitialCalendar, StockService.FLAG_USER_INITIATED | StockService.FLAG_FIND_CLOSEST);
            }
        }

        if(mWaitingOnZoomToUser) {
            mWaitingOnZoomToUser = false;

            if(!isCleanedUp()) {
                mCentralMap.getErrorBanner().animateBanner(false);
                zoomToPoint(location);
            }
        }

        // Next, do the victory observer.  We're not using the built-in
        // geofencing capabilities because we want to use the current GPS
        // accuracy as our fencing radius.  The built-in one requires a
        // single radius that doesn't change, which, to be honest, is
        // perfectly fine for MOST situations.  This just isn't most
        // situations.
        if(mCurrentInfo != null && !mVictoryReported) {

            float accuracy = location.getAccuracy();

            // The accuracy can't be zero, at least not in real-world
            // circumstances.  The only way it'll be zero is if we're using
            // the emulator or there's otherwise a mock location coming in.
            // In that case, treat it as 5m, just so victory can be achieved
            // without being EXACTLY on the point.
            if(accuracy == 0.0f) accuracy = 5.0f;

            if(accuracy < GHDConstants.LOW_ACCURACY_THRESHOLD
                    && mCurrentInfo.getDistanceInMeters(location) < accuracy) {
                // VICTORY!
                ErrorBanner banner = mCentralMap.getErrorBanner();
                banner.setErrorStatus(ErrorBanner.Status.VICTORY);
                banner.setText(mCentralMap.getString(R.string.toast_close_enough));
                banner.setCloseVisible(true);
                banner.animateBanner(true);
                mVictoryReported = true;
            }
        }

        // Update the InfoBox, too.  Fortunately, that takes care of
        // everything in and of itself.
        mInfoBox.onLocationChanged(location);

        // Plus, update the fragment, if there is one.
        if(mExtraFragment != null)
            mExtraFragment.onLocationChanged(location);
    }

    @Override
    public void permissionsDenied(boolean denied) {
        // Make sure the zoom buttons are updated right away.
        setZoomButtonsEnabled();

        // Also, get rid of the banner if we're denied.
        if(denied)
            mCentralMap.getErrorBanner().animateBanner(false);

        // The InfoBox also goes to unavailable mode.
        mInfoBox.setUnavailable(denied);

        // The fragment needs to know to shut off location bits if need be.
        if(mExtraFragment != null)
            mExtraFragment.permissionsDenied(denied);

        // All of the various updates for which we've got booleans will just sit
        // around and not do anything if we never get updates, which we won't if
        // the user denied permissions.
    }

    private void setZoomButtonsEnabled() {
        // Zoom to user is always on if permissions aren't denied.
        mZoomButtons.setButtonEnabled(ZoomButtons.ZOOM_USER, !arePermissionsDenied());

        // Zoom to destination is only on if we have a valid info.
        mZoomButtons.setButtonEnabled(ZoomButtons.ZOOM_DESTINATION, mCurrentInfo != null);

        // Zoom to both is only on if we have a valid info AND permissions
        // aren't denied.
        mZoomButtons.setButtonEnabled(ZoomButtons.ZOOM_FIT_BOTH, mCurrentInfo != null && !arePermissionsDenied());
    }

    @Nullable
    @Override
    protected Info getActiveInfo() {
        return mCurrentInfo;
    }
}
