/*
 * CentralMapExtraFragment.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;

import com.google.android.gms.location.LocationListener;

import net.exclaimindustries.geohashdroid.activities.DetailedInfoActivity;
import net.exclaimindustries.geohashdroid.activities.WikiActivity;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.util.PermissionsDeniedListener;

/**
 * This is the base class from which the two extra Fragments {@link net.exclaimindustries.geohashdroid.activities.CentralMap}
 * can use derive.  It simply allows better communication between them and
 * {@link net.exclaimindustries.geohashdroid.util.ExpeditionMode}.
 */
public abstract class CentralMapExtraFragment
        extends BaseGHDThemeFragment
        implements LocationListener, PermissionsDeniedListener {
    /**
     * The various types of CentralMapExtraFragment that can exist.  It's either
     * this or a mess of instanceof checks to see what's currently showing.
     */
    public enum FragmentType {
        /** The Detailed Info fragment. */
        DETAILS,
        /** The Wiki fragment. */
        WIKI
    }

    /** The bundle key for the Info. */
    public final static String INFO = "info";

    /**
     * The bundle key for if permissions were explicitly denied coming into the
     * fragment.
     */
    public final static String PERMISSIONS_DENIED = "permissionsDenied";

    /**
     * Now, what you've got here is your garden-variety interface that something
     * ought to implement to handle the close button on this here fragment.
     */
    public interface CloseListener {
        /**
         * Called when the user clicks the close button.  Use this opportunity
         * to either dismiss the fragment or close the activity that contains
         * it.
         *
         * @param fragment the CentralMapExtraFragment that is about to be closed
         */
        void extraFragmentClosing(CentralMapExtraFragment fragment);

        /**
         * Called during onDestroy().  ExpeditionMode needs this so it knows
         * when the user backed out of the fragment, as opposed to just the
         * close button.
         *
         * @param fragment the CentralMapExtraFragment that is being destroyed
         */
        void extraFragmentDestroying(CentralMapExtraFragment fragment);
    }

    protected CloseListener mCloseListener;
    protected Info mInfo;
    protected boolean mPermissionsDenied;

    /**
     * Sets what'll be listening for the close button and/or an onDestroy event.
     *
     * @param listener some CloseListener somewhere
     */
    public void setCloseListener(CloseListener listener) {
        mCloseListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // First, see if there's an instance state.
        if(savedInstanceState != null) {
            // If so, use what's in there.
            mInfo = savedInstanceState.getParcelable(INFO);
            mPermissionsDenied = savedInstanceState.getBoolean(PERMISSIONS_DENIED, false);
        } else {
            // If not, go to the arguments.
            Bundle args = getArguments();

            if(args != null) {
                mInfo = args.getParcelable(INFO);
                mPermissionsDenied = args.getBoolean(PERMISSIONS_DENIED, false);
            }
        }

    }

    @Override
    public void onDestroy() {
        // The parent needs to know when this fragment is destroyed so it can
        // make the FrameLayout go away.
        if(mCloseListener != null)
            mCloseListener.extraFragmentDestroying(this);

        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Remember that last info.  Owing to how ExpeditionMode works, it might
        // have changed since arguments time.  If it DIDN'T change, well, it'll
        // be the same as the arguments anyway.
        outState.putParcelable(INFO, mInfo);
        outState.putBoolean(PERMISSIONS_DENIED, mPermissionsDenied);
    }

    /**
     * Registers a button (or, well, a {@link View} in general) to act as the
     * close button, calling the close method on the CloseListener.
     *
     * @param v the View that will act as the close button
     */
    protected void registerCloseButton(@NonNull View v) {
        v.setOnClickListener(v1 -> {
            if(mCloseListener != null)
                mCloseListener.extraFragmentClosing(CentralMapExtraFragment.this);
        });
    }

    /**
     * Sets the Info for this fragment, to whatever degree that's useful for it.
     * Whatever gets set here will override any arguments originally passed in
     * if and when onSaveInstanceState is needed.
     *
     * @param info that new Info
     */
    public void setInfo(@Nullable final Info info) {
        mInfo = info;
    }

    /**
     * Gets what type of CentralMapExtraFragment this is so you don't have to
     * keep using instanceof.  Stop that.
     *
     * @return a FragmentType enum
     */
    @NonNull
    public abstract FragmentType getType();

    /**
     * Static factory that makes an Intent for a given FragmentType's Activity
     * container.  This happens if the user's on a phone.
     *
     * @param type the type
     * @return one factory-direct Intent
     */
    @NonNull
    public static Intent makeIntentForType(Context c, FragmentType type) {
        switch(type) {
            case DETAILS:
                return new Intent(c, DetailedInfoActivity.class);
            case WIKI:
                return new Intent(c, WikiActivity.class);
        }

        throw new RuntimeException("I don't know what sort of FragmentType " + type + " is supposed to be!");
    }

    /**
     * Static factory that makes a Fragment for a given FragmentType.  This
     * happens if the user's on a tablet.
     *
     * @param type the type
     * @return a fragment
     */
    public static CentralMapExtraFragment makeFragmentForType(FragmentType type) {
        switch(type) {
            case DETAILS:
                return new DetailedInfoFragment();
            case WIKI:
                return new WikiFragment();
        }

        throw new RuntimeException("I don't know what sort of FragmentType " + type + " is supposed to be!");
    }
}
