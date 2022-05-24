/*
 * InfoBox.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.widgets;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.location.Location;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.location.LocationListener;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.util.UnitConverter;

import java.text.DecimalFormat;

/**
 * This is the info box.  It sits neatly on top of the map screen.  Given an
 * Info and a stream of updates, it'll report on where the user is and how far
 * from the target they are.
 */
public class InfoBox extends LinearLayout implements LocationListener {

    private Info mInfo;

    private TextView mDest;
    private TextView mYou;
    private TextView mDistance;
    private TextView mAccuracyLow;
    private TextView mAccuracyReallyLow;

    private Location mLastLocation;

    private static final DecimalFormat DIST_FORMAT = new DecimalFormat("###.###");

    private boolean mAlreadyLaidOut = false;
    private boolean mWaitingToShow = false;
    private boolean mUnavailable = false;

    /** If the InfoBox should be faded out. */
    private boolean mFaded = false;
    /** If the InfoBox should be visible and not off-screen. */
    private boolean mVisible = false;

    // The last-seen states for each type (so we don't overwrite any animation
    // in progress).
    private boolean mLastFaded = false;
    private boolean mLastVisible = false;

    public InfoBox(Context c) {
        this(c, null);
    }

    public InfoBox(Context c, AttributeSet attrs) {
        super(c, attrs);

        // How about some setup?
        setBackgroundColor(ContextCompat.getColor(c, R.color.infobox_background));
        int padding = getResources().getDimensionPixelSize(R.dimen.infobox_padding);
        setPadding(padding, padding, padding, padding);
        setOrientation(LinearLayout.VERTICAL);

        // I stand by my decision to elevate!  Even though I'm pretty sure it
        // doesn't do anything with a translucent background.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            setElevation(getResources().getDimension(R.dimen.elevation_infobox));

        // INFLATE!
        inflate(c, R.layout.infobox, this);

        mDest = findViewById(R.id.infobox_hashpoint);
        mYou = findViewById(R.id.infobox_you);
        mDistance = findViewById(R.id.infobox_distance);
        mAccuracyLow = findViewById(R.id.infobox_accuracy_low);
        mAccuracyReallyLow = findViewById(R.id.infobox_accuracy_really_low);

        // Make it not visible immediately.  We'll re-enable it if need be once
        // we get the global layout listener called.
        setAlpha(0.0f);

        // As usual, make sure the view's just gone until we need it.
        // ExpeditionMode will pull it back in.
        getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            // Got a height!  Hopefully.
            if(!mAlreadyLaidOut) {
                mAlreadyLaidOut = true;

                // Make it off-screen first, then animate it on if need be.
                setInfoBoxVisible(false);
                animateInfoBoxVisible(mWaitingToShow);
            }
        });
    }

    /**
     * Sets the Info.  If null, this will make it go to standby.
     *
     * @param info the new Info
     */
    public void setInfo(@Nullable final Info info) {
        // New info!
        mInfo = info;

        updateBox();
    }

    private void updateBox() {
        ((Activity)getContext()).runOnUiThread(() -> {
            float accuracy = 5.0f;
            if(mLastLocation != null)
                accuracy = mLastLocation.getAccuracy();

            // Make sure we're dealing with sane data if we got this from an
            // emulator or mock location data...
            if(accuracy == 0.0f)
                accuracy = 5.0f;

            // Redraw the Info.  Always do this.  The user might be coming
            // back from Preferences, for instance.
            if(mInfo == null) {
                mDest.setText(R.string.unknown_title);
            } else {
                mDest.setText(UnitConverter.makeFullCoordinateString(getContext(), mInfo.getFinalLocation(), false, UnitConverter.OUTPUT_SHORT));
            }

            // Reset the accuracy warnings.  The right one will go back up
            // as need be.
            mAccuracyLow.setVisibility(View.GONE);
            mAccuracyReallyLow.setVisibility(View.GONE);

            // If we've got a location yet, use that.  If not, to standby
            // with you!
            if(mUnavailable) {
                mYou.setVisibility(View.GONE);
            } else {
                mYou.setVisibility(View.VISIBLE);
            }

            if(mLastLocation == null) {
                mYou.setText(R.string.unknown_title);
            } else {
                mYou.setText(UnitConverter.makeFullCoordinateString(getContext(), mLastLocation, false, UnitConverter.OUTPUT_SHORT));

                // Hey, as long as we're here, let's also do accuracy.
                if(accuracy >= GHDConstants.REALLY_LOW_ACCURACY_THRESHOLD)
                    mAccuracyReallyLow.setVisibility(View.VISIBLE);
                else if(accuracy >= GHDConstants.LOW_ACCURACY_THRESHOLD)
                    mAccuracyLow.setVisibility(View.VISIBLE);
            }

            // Next, calculate the distance, if possible.
            if(mUnavailable) {
                mDistance.setVisibility(View.GONE);
            } else {
                mDistance.setVisibility(View.VISIBLE);
            }

            if(mLastLocation == null || mInfo == null) {
                mDistance.setText(R.string.unknown_title);
                mDistance.setTextColor(ContextCompat.getColor(getContext(), R.color.infobox_text));
            } else {
                float distance = mLastLocation.distanceTo(mInfo.getFinalLocation());
                mDistance.setText(UnitConverter.makeDistanceString(getContext(), DIST_FORMAT, distance));

                // Plus, if we're close enough AND accurate enough, make the
                // text be green.  We COULD do this with geofencing
                // callbacks and all, but, I mean, we're already HERE,
                // aren't we?
                if(accuracy < GHDConstants.LOW_ACCURACY_THRESHOLD && distance <= accuracy)
                    mDistance.setTextColor(ContextCompat.getColor(getContext(), R.color.infobox_in_range));
                else
                    mDistance.setTextColor(ContextCompat.getColor(getContext(), R.color.infobox_text));

            }
        });
    }

    /**
     * Slides the InfoBox in to or out of view.
     *
     * @param visible true to slide in, false to slide out
     */
    public void animateInfoBoxVisible(boolean visible) {
        if(!mAlreadyLaidOut) {
            mWaitingToShow = visible;
        } else {
            mVisible = visible;
            resolveInfoBoxState(null);
        }
    }

    /**
     * Slides the InfoBox out of view (ONLY out of view), with an ending action.
     *
     * @param endAction action to perform after animation completes
     */
    public void animateInfoBoxOutWithEndAction(@Nullable Runnable endAction) {
        mVisible = false;
        resolveInfoBoxState(endAction);
    }

    /**
     * Makes the InfoBox be in or out of view without animating it.
     * @param visible true to appear, false to vanish
     */
    public void setInfoBoxVisible(boolean visible) {
        mVisible = visible;
        forceInfoBoxState();
    }

    /**
     * Fades out the InfoBox and shuts off the click handler.  This is used to
     * keep the final destination flag visible if it's underneath the box.
     *
     * @param faded true to be faded, false to be normal
     */
    public void fadeOutInfoBox(boolean faded) {
        mFaded = faded;
        resolveInfoBoxState(null);
    }

    /**
     * Forces the InfoBox to the faded state, shutting off the click handler in
     * the process.
     *
     * @param faded true to be faded, false to be normal
     */
    public void setInfoBoxFaded(boolean faded) {
        mFaded = faded;
        forceInfoBoxState();
    }

    private void resolveInfoBoxState(Runnable endAction) {
        if(mVisible == mLastVisible && mFaded == mLastFaded) return;

        // Quick note: The size of the InfoBox might change due to the width
        // of the text shown (as well as the accuracy warning), but since we
        // alpha it out anyway, that shouldn't be a real major issue.
        if(!mVisible)
            animate().translationX(getWidth()).alpha(0.0f).withEndAction(endAction);
        else if(mFaded)
            animate().translationX(0.0f).alpha(0.2f).withEndAction(endAction);
        else
            animate().translationX(0.0f).alpha(1.0f).withEndAction(endAction);

        // If the box is faded OR hidden, we can't click it.
        setClickable(mVisible && !mFaded);

        mLastVisible = mVisible;
        mLastFaded = mFaded;
    }

    private void forceInfoBoxState() {
        // Same as in resolve, but without animation.
        if(!mVisible) {
            setTranslationX(getWidth());
            setAlpha(0.0f);
        } else if(mFaded) {
            setTranslationX(0.0f);
            setAlpha(0.2f);
        } else {
            setTranslationX(0.0f);
            setAlpha(1.0f);
        }

        setClickable(mVisible && !mFaded);

        // Note that we don't bother checking if this has changed, since we
        // should be overwriting any animation at this point anyway.
        mLastVisible = mVisible;
        mLastFaded = mFaded;
    }

    /**
     * Sets whether or not the location data is unavailable.  It's unavailable
     * if the user didn't give us permission to get it.  If so, the current
     * location and distance fields will just say they're unavailable.  This is
     * different from if we don't have the appropriate data YET, but CAN get it
     * if it shows up.  The widget defaults to available (false).
     *
     * @param flag true for unavailable, false for available
     */
    public void setUnavailable(boolean flag) {
        mUnavailable = flag;
        updateBox();
    }

    @Override
    public void onLocationChanged(Location location) {
        // Hey, look, a location!
        mLastLocation = location;
        updateBox();
    }

    /**
     * Gets the current location and size of the InfoBox, as a Rect (pass one in
     * and it'll be updated), relative to the parent view.
     *
     * @param output the Rect in which data will go
     */
    public void getLocationRect(@NonNull Rect output) {
        output.set(getLeft(), getTop(), getRight(), getBottom());
    }
}
