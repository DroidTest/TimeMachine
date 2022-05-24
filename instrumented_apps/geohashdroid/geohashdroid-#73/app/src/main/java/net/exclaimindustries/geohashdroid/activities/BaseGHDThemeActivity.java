/*
 * BaseGHDThemeActivity.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.app.Activity;
import android.app.backup.BackupManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.GHDConstants;

/**
 * A <code>BaseGHDThemeActivity</code> sets up the theme (day or night) during
 * onCreate, as well as offer methods to switch between the themes.
 */
public abstract class BaseGHDThemeActivity
        extends Activity {

    private boolean mStartedInNight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mStartedInNight = prefs.getBoolean(GHDConstants.PREF_NIGHT_MODE, false);

        // We have to do this BEFORE any layouts are set up.
        if(mStartedInNight)
            setTheme(R.style.Theme_GeohashDroidDark);
        else
            setTheme(R.style.Theme_GeohashDroid);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If the nightiness has changed since we paused, do a recreate.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(prefs.getBoolean(GHDConstants.PREF_NIGHT_MODE, false) != mStartedInNight)
            recreate();
    }

    /**
     * Returns whether or not the app is in night mode.  Just grabbing the
     * current theme doesn't quite work as easily as you'd think.
     *
     * @return true for night, false for not
     */
    protected boolean isNightMode() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getBoolean(GHDConstants.PREF_NIGHT_MODE, false);
    }

    /**
     * <p>
     * Sets whether or not the app is in night mode.
     * </p>
     *
     * <p>
     * <b>NOTE:</b> If the state of night mode changes, <b>the Activity WILL be
     * recreated</b>, as that's the only way you can change themes on-the-fly.
     * Make sure you've done whatever you need to BEFORE calling this, as there
     * is NO guarantee execution will meaningfully continue past this!
     * </p>
     *
     * @param night true to be night, false to be not night
     */
    protected void setNightMode(boolean night) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Remember, ONLY act on this if this changed at all!
        if(prefs.getBoolean(GHDConstants.PREF_NIGHT_MODE, false) != night) {
            SharedPreferences.Editor edit = prefs.edit();
            edit.putBoolean(GHDConstants.PREF_NIGHT_MODE, night);
            edit.apply();

            BackupManager bm = new BackupManager(this);
            bm.dataChanged();

            recreate();
        }
    }
}
