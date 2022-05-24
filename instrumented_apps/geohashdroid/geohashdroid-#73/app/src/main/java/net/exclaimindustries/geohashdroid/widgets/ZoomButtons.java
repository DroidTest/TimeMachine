/*
 * ZoomButtons.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.widgets;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import net.exclaimindustries.geohashdroid.R;

/**
 * The <code>ZoomButtons</code> container handles the button in the lower-left
 * of CentralMap.  It pops out when tapped, revealing more buttons to center and
 * re-zoom the view as need be.  No, this isn't related to the zoom buttons on
 * the old API v1 maps.
 */
public class ZoomButtons extends RelativeLayout {
    private static final String DEBUG_TAG = "ZoomButtons";

    private ImageButton mZoomMenu;
    private ImageButton mCancelMenu;
    private ImageButton mZoomFitBoth;
    private ImageButton mZoomUser;
    private ImageButton mZoomDestination;

    private boolean mAlreadyLaidOut = false;

    // So we don't have to keep recalculating it for five buttons and their
    // margins, that's why.
    private float mButtonWidth = 0.0f;

    /** Zoom to fit both the user and the hashpoint on screen at once. */
    public static final int ZOOM_FIT_BOTH = 0;
    /** Zoom to the user's location. */
    public static final int ZOOM_USER = 1;
    /** Zoom to the hashpoint. */
    public static final int ZOOM_DESTINATION = 2;

    /**
     * This should be implemented by anything that's waiting to respond to the
     * zoom buttons.  So, ExpeditionMode, really.
     */
    public interface ZoomButtonListener {
        /**
         * Called when a zoom button is pressed.  Not, mind you, when either the
         * menu button itself or the cancel button are pressed.
         *
         * @param container this, for convenience
         * @param which an int specifying which button just got pressed
         * @see #ZOOM_FIT_BOTH
         * @see #ZOOM_USER
         * @see #ZOOM_DESTINATION
         */
        void zoomButtonPressed(View container, int which);
    }

    private ZoomButtonListener mListener;

    public ZoomButtons(Context c) {
        this(c, null);
    }

    public ZoomButtons(Context c, AttributeSet attrs) {
        super(c, attrs);

        inflate(c, R.layout.zoom_buttons, this);

        // Gather up all our sub-buttons...
        mZoomMenu = findViewById(R.id.zoom_button_menu);
        mCancelMenu = findViewById(R.id.zoom_button_cancel);
        mZoomFitBoth = findViewById(R.id.zoom_button_fit_both);
        mZoomUser = findViewById(R.id.zoom_button_you);
        mZoomDestination = findViewById(R.id.zoom_button_destination);

        // ...and make them do something.
        mZoomFitBoth.setOnClickListener(v -> {
            if(mListener != null)
                mListener.zoomButtonPressed(ZoomButtons.this, ZOOM_FIT_BOTH);
            showMenu(false);
        });

        mZoomUser.setOnClickListener(v -> {
            if(mListener != null)
                mListener.zoomButtonPressed(ZoomButtons.this, ZOOM_USER);
            showMenu(false);
        });

        mZoomDestination.setOnClickListener(v -> {
            if(mListener != null)
                mListener.zoomButtonPressed(ZoomButtons.this, ZOOM_DESTINATION);
            showMenu(false);
        });

        mZoomMenu.setOnClickListener(v -> showMenu(true));

        mCancelMenu.setOnClickListener(v -> showMenu(false));

        // Wait for layout, as usual...
        getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if(!mAlreadyLaidOut) {
                mAlreadyLaidOut = true;
                // Get hold of the basic widths of everything.  We'll just
                // re-use that a lot.
                mButtonWidth = mCancelMenu.getWidth() + (2 * getResources().getDimension(R.dimen.margin_zoom_button));

                // First layout, make all the buttons be off-screen.  The
                // right mode will be set back on as need be.
                mZoomMenu.setTranslationX(-mButtonWidth);
                mCancelMenu.setTranslationX(-mButtonWidth);
                mZoomFitBoth.setTranslationX(-mButtonWidth);
                mZoomUser.setTranslationX(-mButtonWidth);
                mZoomDestination.setTranslationX(-mButtonWidth);

                showMenu(false);
            }
        });
    }

    /**
     * Sets whether the menu is showing (the three options and cancel are up) or
     * not (the button that triggers the menu is up).
     *
     * @param show true to show, false to hide
     */
    public void showMenu(boolean show) {
        if(mAlreadyLaidOut) {
            // Only do this if we're laid out.  Otherwise, this'll go haywire
            // with the widget sizes if mButtonWidth isn't defined.
            if(show) {
                // Menu in!  Button out!
                mZoomMenu.animate().translationX(-mButtonWidth);
                mCancelMenu.animate().translationX(0.0f);
                mZoomFitBoth.animate().translationX(0.0f);
                mZoomUser.animate().translationX(0.0f);
                mZoomDestination.animate().translationX(0.0f);
            } else {
                // Menu out!  Button in!
                mZoomMenu.animate().translationX(0.0f);
                mCancelMenu.animate().translationX(-mButtonWidth);
                mZoomFitBoth.animate().translationX(-mButtonWidth);
                mZoomUser.animate().translationX(-mButtonWidth);
                mZoomDestination.animate().translationX(-mButtonWidth);
            }
        }
    }

    /**
     * Sets whatever's going to listen to the buttons.
     *
     * @param listener said listener
     */
    public void setListener(ZoomButtonListener listener) {
        mListener = listener;
    }

    /**
     * Enables or disables a button.  Note that this won't do the logic to make
     * sure "fit both" is disabled when either "your location" or "final
     * destination" are, so do that yourself.
     *
     * @param button button to disable, by  ZOOM_* statics
     * @param enabled true to enable, false to disable
     * @see #ZOOM_FIT_BOTH
     * @see #ZOOM_USER
     * @see #ZOOM_DESTINATION
     */
    public void setButtonEnabled(int button, final boolean enabled) {
        View toDisable = null;

        switch(button) {
            case ZOOM_FIT_BOTH:
                toDisable = mZoomFitBoth;
                break;
            case ZOOM_USER:
                toDisable = mZoomUser;
                break;
            case ZOOM_DESTINATION:
                toDisable = mZoomDestination;
                break;
        }

        // Of course, if this wasn't a valid button, toDisable will remain null,
        // meaning the caller screwed it up.
        if(toDisable == null) {
            Log.w(DEBUG_TAG, "There's no such zoom button with an ID of " + button + "!");
            return;
        }

        // But with a button in hand...
        final View reallyToDisable = toDisable;
        ((Activity)getContext()).runOnUiThread(() -> reallyToDisable.setEnabled(enabled));
    }
}
