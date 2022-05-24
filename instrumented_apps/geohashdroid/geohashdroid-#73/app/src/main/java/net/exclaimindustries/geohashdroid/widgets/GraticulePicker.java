/*
 * GraticulePicker.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Graticule;

/**
 * This is the box of graticule-picking goodness that appears on the bottom of
 * the map during Select-A-Graticule mode.
 */
public class GraticulePicker extends RelativeLayout {

    private EditText mLat;
    private EditText mLon;
    private CheckBox mGlobal;
    private Button mClosest;

    private boolean mExternalUpdate;
    private boolean mAlreadyLaidOut = false;
    private boolean mWaitingToShow = false;

    private GraticulePickerListener mListener;

    /**
     * The interface of choice for when GraticulePicker needs to talk back to
     * something.  Make sure something implements this, else the whole thing
     * will sort of not work.
     */
    public interface GraticulePickerListener {
        /**
         * Called when a new Graticule is picked.  This is called EVERY time the
         * user presses a key, so be careful.  If the input is blatantly
         * incomplete (i.e. empty or just a negative sign), this won't be
         * called.
         *
         * @param g the new Graticule (null if it's a globalhash)
         */
        void updateGraticule(@Nullable Graticule g);

        /**
         * Called when the user presses the "Find Closest" button.  Later on,
         * this widget should get setNewGraticule called on it with the results
         * of said search (assuming it can get such a result).
         */
        void findClosest();

        /**
         * Called when the picker is closing.  For now, this just means when
         * the close button is pressed.  In the future, it might also mean if
         * it gets swipe-to-dismiss'd.  Note that this won't do its own
         * dismissal.  You need to handle that yourself once you get the
         * callback.
         */
        void graticulePickerClosing();
    }

    public GraticulePicker(Context c) {
        this(c, null);
    }

    public GraticulePicker(Context c, AttributeSet attrs) {
        super(c, attrs);

        // Deal out a little bit of setup justice...
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        boolean night = prefs.getBoolean(GHDConstants.PREF_NIGHT_MODE, false);
        if(night)
            setBackgroundColor(ContextCompat.getColor(c, android.R.color.black));
        else
            setBackgroundColor(ContextCompat.getColor(c, android.R.color.white));

        int padding = getResources().getDimensionPixelSize(R.dimen.standard_padding);
        setPadding(padding, padding, padding, padding);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClickable(true);

        // Who wants a neat-looking shadow effect as if this were some sort of
        // material hovering a few dp above the map?  You do, assuming you're
        // using Lollipop!
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            setElevation(getResources().getDimension(R.dimen.elevation_graticule_picker));

        // Now then, let's get inflated.
        inflate(c, R.layout.graticulepicker, this);

        // Here come the widgets.  Each is magical and unique.
        mLat = findViewById(R.id.grat_lat);
        mLon = findViewById(R.id.grat_lon);
        mGlobal = findViewById(R.id.grat_globalhash);
        mClosest = findViewById(R.id.grat_closest);
        ImageButton close = findViewById(R.id.close);

        // And how ARE they magical?  Well, like this.  First, any time the
        // boxes are updated, send out a new Graticule to the Activity.
        TextWatcher tw = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Blah.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // BLAH!
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Action!
                if(!mExternalUpdate)
                    dispatchGraticule();
            }
        };

        mLat.addTextChangedListener(tw);
        mLon.addTextChangedListener(tw);

        // Also, when the checkbox gets changed, set/unset Globalhash mode.
        mGlobal.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked) {
                // If it's checked, mLat and mLon go disabled, as you can't
                // set a specific graticule.
                mLat.setEnabled(false);
                mLon.setEnabled(false);
            } else {
                mLat.setEnabled(true);
                mLon.setEnabled(true);
            }

            if(!mExternalUpdate)
                dispatchGraticule();
        });

        // Then, the Find Closest button.  That one we foist off on the calling
        // Activity.
        mClosest.setOnClickListener(v -> {
            v.setEnabled(false);
            if(mListener != null)
                mListener.findClosest();
        });

        // The close button needs to, well, close.
        close.setOnClickListener(v -> {
            if(mListener != null)
                mListener.graticulePickerClosing();
        });

        // Plus, the close button needs updating if it's night.  That grey is
        // just a weeeeee bit too dark for the black background.
        if(night) close.setImageDrawable(ContextCompat.getDrawable(c, R.drawable.cancel_button_dark));

        // And then there's this again.  Huh.  You'd think I should make a
        // parent class to handle this.
        getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            // Got a height!  Hopefully.
            if(!mAlreadyLaidOut) {
                mAlreadyLaidOut = true;

                // Make it off-screen first, then animate it on if need be.
                setGraticulePickerVisible(false);
                animateGraticulePickerVisible(mWaitingToShow);
            }
        });
    }

    /**
     * Sets a new Graticule for the EditTexts.  This is called at startup and
     * any time the user taps on the map to pick a new Graticule.
     *
     * @param g the new Graticule
     */
    public void setNewGraticule(Graticule g) {
        // Make sure this flag is set so we don't wind up double-updating.
        mExternalUpdate = true;

        // This should NEVER be a globalhash, as that isn't possible from the
        // map.  But, it could be null, and we're nothing if not defensive here.
        if(g == null) {
            mGlobal.setChecked(true);
        } else {
            // Update text as need be.  Remember, negative zero IS valid!
            mGlobal.setChecked(false);
            mLat.setText(g.getLatitudeString(true));
            mLon.setText(g.getLongitudeString(true));
        }

        // And we're done, so unset the flag.
        mExternalUpdate = false;

        // NOW we can dispatch the change.
        dispatchGraticule();
    }

    /**
     * Sets the globalhash checkbox to be checked or not.  This may dispatch a
     * new Graticule.
     *
     * @param global true to check, false to uncheck
     */
    public void setGlobalHash(boolean global) {
        mGlobal.setChecked(global);
    }

    /**
     * Tells the widget to trigger its listener so its {@link GraticulePickerListener#updateGraticule(Graticule)}
     * method will be called if need be.
     */
    public void triggerListener() {
        dispatchGraticule();
    }

    private void dispatchGraticule() {
        Graticule toSend;

        try {
            toSend = buildGraticule();
        } catch (Exception e) {
            // If an exception is thrown, we don't have valid input.
            return;
        }

        // If we got here, we can send it on its merry way!
        if(mListener != null)
            mListener.updateGraticule(toSend);
    }

    private Graticule buildGraticule() throws NullPointerException, NumberFormatException {
        // First, read the inputs.
        if(mGlobal.isChecked()) {
            // A checked globalhash means we always send a null Graticule, no
            // matter what the inputs say, even if those inputs are invalid.
            return null;
        } else {
            // Otherwise, make a Graticule.  The constructor will throw as need
            // be.
            return new Graticule(mLat.getText().toString(), mLon.getText().toString());
        }
    }

    /**
     * Sets the {@link GraticulePickerListener}.  If this is either null or
     * never called, this whole Fragment won't do much.
     *
     * @param listener the new listener
     */
    public void setListener(GraticulePickerListener listener) {
        mListener = listener;

        dispatchGraticule();
    }

    /**
     * Gets the currently-input Graticule.  This will return null if there's no
     * valid input yet OR if the Globalhash checkbox is ticked, so make sure to
     * also check {@link #isGlobalhash()}.
     *
     * @return the current Graticule, or null
     */
    public Graticule getGraticule() {
        try {
            return buildGraticule();
        } catch(Exception e) {
            return null;
        }
    }

    /**
     * Gets whether or not Globalhash is ticked.  Note that if this is false, it
     * doesn't necessarily mean there's a valid Graticule in the inputs.
     *
     * @return true if global, false if not
     */
    public boolean isGlobalhash() {
        return mGlobal.isChecked();
    }

    /**
     * Resets the Find Closest button after it's been triggered.  It'll be
     * disabled otherwise.
     */
    public void resetFindClosest() {
        mClosest.setEnabled(true);
    }

    /**
     * Makes the Find Closest button visible or invisible.  Make it invisible if
     * location permissions have been denied.
     *
     * @param hidden true to be {@link View#INVISIBLE}, false for {@link View#VISIBLE}
     */
    public void setClosestHidden(boolean hidden) {
        mClosest.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
    }

    /**
     * Slides the picker in to or out of view.
     *
     * @param visible true to slide in, false to slide out
     */
    public void animateGraticulePickerVisible(boolean visible) {
        if(!mAlreadyLaidOut) {
            mWaitingToShow = visible;
        } else {
            if(!visible) {
                // Slide out!
                animate().translationY(getHeight()).alpha(0.0f);
            } else {
                // Slide in!
                animate().translationY(0.0f).alpha(1.0f);
            }
        }
    }

    /**
     * Slides the picker out of view (ONLY out of view), with an ending action.
     *
     * @param endAction action to perform after animation completes
     */
    public void animateGraticulePickerOutWithEndAction(@Nullable Runnable endAction) {
        animate().translationY(getHeight()).alpha(0.0f).withEndAction(endAction);
    }

    /**
     * Makes the picker be in or out of view without animating it.
     *
     * @param visible true to appear, false to vanish
     */
    public void setGraticulePickerVisible(boolean visible) {
        if(!visible) {
            setTranslationY(getHeight());
            setAlpha(0.0f);
        } else {
            setTranslationY(0.0f);
            setAlpha(1.0f);
        }
    }
}
