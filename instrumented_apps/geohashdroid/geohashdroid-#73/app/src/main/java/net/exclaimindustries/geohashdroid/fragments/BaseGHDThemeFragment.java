/*
 * BaseGHDThemeFragment.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import net.exclaimindustries.geohashdroid.util.GHDConstants;

/**
 * Sort of like a {@link net.exclaimindustries.geohashdroid.activities.BaseGHDThemeActivity},
 * this class sets up a night-aware Fragment.  This assumes, however, that the
 * containing Activity will do the recreating and restarting on changes, so this
 * is more just aware of what the current setting is.
 */

public abstract class BaseGHDThemeFragment
        extends Fragment {
    /**
     * Returns whether or not the app is in night mode.  Just grabbing the
     * current theme doesn't quite work as easily as you'd think.
     *
     * @return true for night, false for not
     */
    protected boolean isNightMode() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        return prefs.getBoolean(GHDConstants.PREF_NIGHT_MODE, false);
    }
}
