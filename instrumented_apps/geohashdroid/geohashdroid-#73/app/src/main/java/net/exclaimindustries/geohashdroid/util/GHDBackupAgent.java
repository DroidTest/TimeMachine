/*
 * GHDBackupAgent.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

/**
 * This is your standard run-of-the-mill BackupAgentHelper for pre-Marshmallow
 * backups.
 */
public class GHDBackupAgent extends BackupAgentHelper {
    static final String PREFS_BACKUP_KEY = "prefsBackupKey";

    @Override
    public void onCreate() {
        // Hoo boy, there's a few prefs to back up...
        SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(
                this,
                GHDConstants.PREF_AUTOZOOM,
                GHDConstants.PREF_COORD_UNITS,
                GHDConstants.PREF_DEFAULT_GRATICULE_GLOBALHASH,
                GHDConstants.PREF_DEFAULT_GRATICULE_LATITUDE,
                GHDConstants.PREF_DEFAULT_GRATICULE_LONGITUDE,
                GHDConstants.PREF_DIST_UNITS,
                GHDConstants.PREF_INFOBOX,
                GHDConstants.PREF_KNOWN_LOCATIONS,
                GHDConstants.PREF_LAST_MAP_TYPE,
                GHDConstants.PREF_LAST_SEEN_VERSION,
                GHDConstants.PREF_NEARBY_POINTS,
                GHDConstants.PREF_SHOW_KNOWN_LOCATIONS,
                GHDConstants.PREF_STARTUP_BEHAVIOR,
                GHDConstants.PREF_STOCK_ALARM,
                GHDConstants.PREF_STOCK_CACHE_SIZE,
                GHDConstants.PREF_STOP_BUGGING_ME_PREFETCH_WARNING,
                GHDConstants.PREF_WIKI_PASS,
                GHDConstants.PREF_WIKI_USER,
                GHDConstants.PREF_NIGHT_MODE
        );
        addHelper(PREFS_BACKUP_KEY, helper);
    }
}
