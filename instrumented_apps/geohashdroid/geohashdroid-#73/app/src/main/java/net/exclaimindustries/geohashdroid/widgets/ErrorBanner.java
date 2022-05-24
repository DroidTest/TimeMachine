/*
 * ErrorBanner.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.widgets;

import android.app.Activity;
import android.content.Context;
import android.preference.PreferenceManager;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.GHDConstants;

/**
 * <p>
 * An <code>ErrorBanner</code> is meant to appear on the top of a View (usually
 * the root View of an Activity) and slides in and out of place to report on
 * errors, warnings, notifications, etc.
 * </p>
 *
 * <p>
 * Given it's used for informational purposes, not just errors, this class is
 * perhaps increasingly ill-named.
 * </p>
 */
public class ErrorBanner extends LinearLayout {
    private TextView mMessage;
    private ImageButton mClose;

    private boolean mAlreadyLaidOut = false;
    private boolean mIsNightMode;

    /**
     * Use these in {@link #setErrorStatus(Status)} to set a premade
     * background for these sorts of errors.
     */
    public enum Status {
        /** A normal error banner (white or black). */
        NORMAL,
        /** An error banner screaming a warning (yellow). */
        WARNING,
        /** An error banner just giving up with an error (red). */
        ERROR,
        /** A banner that proclaims victory (green)! */
        VICTORY
    }

    public ErrorBanner(Context context) {
        this(context, null);
    }

    public ErrorBanner(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.error_banner, this);

        // Get us the message widget for later.
        mMessage = findViewById(R.id.error_text);

        // The button is always close.
        mClose = findViewById(R.id.close);
        mClose.setOnClickListener(v -> {
            // Away it goes!
            animateBanner(false);
        });

        // Night?  Maybe?
        mIsNightMode = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(GHDConstants.PREF_NIGHT_MODE, false);

        // Set the close button as need be.
        if(mIsNightMode)
            mClose.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.cancel_button_dark));

        // On startup, we want to make sure the view is off-screen until we're
        // told different.
        getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            // Got a height!  Hopefully.
            if(!mAlreadyLaidOut) {
                mAlreadyLaidOut = true;
                setBannerVisible(false);
                setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Sets the banner's visibility.  Note that this is NOT the same as the View
     * visibility (GONE, INVISIBLE, VISIBLE).  This sets whether or not the
     * banner is hidden away off the top of the view and alpha'd to 0.  You
     * would use this one to <i>instantly</i> change the visibility, not animate
     * it.
     *
     * @param visible true to be visible, false to be hidden
     */
    public void setBannerVisible(boolean visible) {
        if(!visible) {
            // The translation should be negative the height of the view, which
            // should neatly hide it away while allowing it to slide back in
            // later if need be.
            setTranslationY(-getHeight());
            setAlpha(0.0f);
        } else {
            // Otherwise, it goes back to its normal spot.
            setTranslationY(0.0f);
            setAlpha(1.0f);
        }
    }

    /**
     * Slides the banner in or out of view.  Make sure it's set up first!
     *
     * @param visible true to show, false to slide away
     */
    public void animateBanner(boolean visible) {
        if(!visible) {
            // Slide out!
            animate().translationY(-getHeight()).alpha(0.0f);
        } else {
            // Slide in!
            animate().translationY(0.0f).alpha(1.0f);
        }
    }

    /**
     * Sets the text on the banner.
     *
     * @param text text to display
     */
    public void setText(final String text) {
        ((Activity)getContext()).runOnUiThread(() -> {
            setCloseVisible(true);
            mMessage.setText(text);
        });
    }

    /**
     * Convenience method to set a commonly-used background color.
     *
     * @param b type of background to set
     */
    public void setErrorStatus(@NonNull Status b) {
        @ColorRes int color = 0;

        switch(b) {
            case NORMAL:
                color = (mIsNightMode ? R.color.error_banner_normal_dark : R.color.error_banner_normal);
                break;
            case WARNING:
                color = (mIsNightMode ? R.color.error_banner_warning_dark : R.color.error_banner_warning);
                break;
            case ERROR:
                color = (mIsNightMode ? R.color.error_banner_error_dark : R.color.error_banner_error);
                break;
            case VICTORY:
                color = (mIsNightMode ? R.color.error_banner_victory_dark : R.color.error_banner_victory);
        }

        setBackgroundErrorColor(color);
    }

    /**
     * Sets the background color.  For instance, you'd set this to red for
     * errors, yellow for warnings, white if you forgot to change the color,
     * that sort of thing.
     *
     * @param color the new color to set
     */
    public void setBackgroundErrorColor(@ColorRes final int color) {
        ((Activity)getContext()).runOnUiThread(() -> {
            setCloseVisible(true);
            setBackgroundColor(ContextCompat.getColor(getContext(), color));
        });
    }

    /**
     * <p>
     * Sets the close button to be visible or not.  Making it invisible
     * effectively makes the banner uncloseable from the UI.
     * </p>
     *
     * <p>
     * Note that, by design, {@link #setText(String)}, {@link #setBackgroundErrorColor(int)},
     * and {@link #setErrorStatus(Status)} will automatically make this visible
     * again, since those are typically called when making a new banner anyway.
     * Make sure you hide the close button LAST.
     * </p>
     *
     * @param visible true to make visible, false to hide
     */
    public void setCloseVisible(final boolean visible) {
        ((Activity)getContext()).runOnUiThread(() -> mClose.setVisibility(visible ? View.VISIBLE : View.GONE));
    }
}
