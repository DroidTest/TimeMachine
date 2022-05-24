/*
 * LocationUtil.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.tools;

import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;

/**
 * <code>LocationUtil</code> holds any interesting {@link Location}-related
 * thingamajigs I can come up with.
 */
public class LocationUtil {
    /**
     * The default time a {@link Location} is considered "new enough".
     * Currently a half hour.
     */
    public static final long NEW_ENOUGH = 1000 * 60 * 30;

    /**
     * Returns whether or not the given {@link Location} is "new enough", as
     * determined by the {@link #NEW_ENOUGH} field.  Note that a null Location
     * is never new enough.
     *
     * @param l Location to check
     * @return true if it's new enough, false if it's too old
     */
    public static boolean isLocationNewEnough(@Nullable Location l) {
        return isLocationNewEnough(l, NEW_ENOUGH);
    }

    /**
     * Returns whether or not the given {@link Location} is "new enough", as
     * determined by the supplied age.  Note that a null Location is never new
     * enough.
     *
     * @param l Location to check
     * @param age the oldest that l can be "new enough", in millis
     * @return true if it's new enough, false if it's too old
     */
    public static boolean isLocationNewEnough(@Nullable Location l, long age) {
        return l != null && System.currentTimeMillis() - l.getTime() < age;
    }

    /**
     * Returns a Location based on the given LatLng.
     *
     * @param latLng a LatLng to Locationify
     * @return a Location
     */
    @NonNull
    public static Location latLngToLocation(@NonNull LatLng latLng) {
        Location loc = new Location("");
        loc.setLatitude(latLng.latitude);
        loc.setLongitude(latLng.longitude);

        return loc;
    }
}
