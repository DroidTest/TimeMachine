/*
 * KnownLocation.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.tools.LocationUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * This represents a single known location.  It's got a LatLng and a name, as
 * well as a way to serialize itself out to a preference, mostly by making
 * itself into a JSON chunk.
 */
public class KnownLocation implements Parcelable {
    private String mName;
    private LatLng mLocation;
    private double mRange;
    private boolean mRestrictGraticule = false;

    private static final String DEBUG_TAG = "KnownLocation";

    /**
     * Private version of the constructor used during {@link #deserialize(JSONObject)}.
     */
    private KnownLocation() { }

    /**
     * Builds up a new KnownLocation.
     *
     * @param name the name of this mLocation
     * @param location a LatLng where it can be found
     * @param range how close it has to be before it triggers a notification, in m
     * @param restrictGraticule true to only consider the location's native graticule for range purposes, rather than find the closest one
     */
    public KnownLocation(@NonNull String name, @NonNull LatLng location, double range, boolean restrictGraticule) {
        mName = name;
        mRange = range;
        mRestrictGraticule = restrictGraticule;

        // The marker needs SOME title.
        if(mName.isEmpty()) mName = "?";

        mLocation = location;
    }

    private KnownLocation(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // Nice how this is easily parcelable.
        dest.writeString(mName);
        dest.writeParcelable(mLocation, 0);
        dest.writeDouble(mRange);
        dest.writeByte((byte)(mRestrictGraticule ? 0 : 1));
    }

    public void readFromParcel(Parcel in) {
        // Same way it went out.
        mName = in.readString();
        mLocation = in.readParcelable(KnownLocation.class.getClassLoader());
        mRange = in.readDouble();
        mRestrictGraticule = in.readByte() != 0;
    }

    public static final Parcelable.Creator<KnownLocation> CREATOR = new Parcelable.Creator<KnownLocation>() {
        @Override
        public KnownLocation createFromParcel(Parcel source) {
            return new KnownLocation(source);
        }

        @Override
        public KnownLocation[] newArray(int size) {
            return new KnownLocation[size];
        }
    };

    /**
     * Deserializes a single JSONObject into a KnownLocation.
     *
     * @param obj the object to deserialize
     * @return a new KnownLocation, or null if something went wrong
     */
    @Nullable
    public static KnownLocation deserialize(@NonNull JSONObject obj) {
        KnownLocation toReturn = new KnownLocation();

        try {
            toReturn.mName = obj.getString("name");
            toReturn.mLocation = new LatLng(obj.getDouble("lat"), obj.getDouble("lon"));
            toReturn.mRange = obj.getDouble("range");
            // Well, whoops.  Turns out I completely forgot to serialize this in
            // previous versions.  We don't want this throwing an exception if
            // it's not there, so we'll assume false if it doesn't exist.
            toReturn.mRestrictGraticule = obj.optBoolean("restrictGraticule", false);
            return toReturn;
        } catch(JSONException je) {
            Log.e(DEBUG_TAG, "Couldn't deserialize a KnownLocation for some reason!", je);
            return null;
        }
    }

    /**
     * Gets all KnownLocations from Preferences and returns them as a List.
     *
     * @param c a Context
     * @return a List full of KnownLocations (or an empty List)
     */
    @NonNull
    public static List<KnownLocation> getAllKnownLocations(@NonNull Context c) {
        List<KnownLocation> toReturn = new ArrayList<>();

        // To the preferences!
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        String blob = prefs.getString(GHDConstants.PREF_KNOWN_LOCATIONS, "[]");

        // I really hope this is a JSONArray...
        JSONArray arr;
        try {
            arr = new JSONArray(blob);
        } catch(JSONException je) {
            Log.e(DEBUG_TAG, "Couldn't parse the known locations JSON blob!", je);
            return toReturn;
        }

        // What's more, I really hope every entry in the JSONArray is a
        // JSONObject that happens to be a KnownLocation...
        for(int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                KnownLocation kl = deserialize(obj);
                if(kl != null) toReturn.add(kl);
            } catch(JSONException je) {
                Log.e(DEBUG_TAG, "Item " + i + " in the known locations JSON blob wasn't a JSONObject!", je);
            }
        }

        return toReturn;
    }

    /**
     * Serializes this out into a single JSONObject.
     *
     * @return a JSONObject that can be used to store this data.
     */
    @NonNull
    public JSONObject serialize() {
        JSONObject toReturn = new JSONObject();

        try {
            toReturn.put("name", mName);
            toReturn.put("lat", mLocation.latitude);
            toReturn.put("lon", mLocation.longitude);
            toReturn.put("range", mRange);
            toReturn.put("restrictGraticule", mRestrictGraticule);
        } catch(JSONException je) {
            // This really, REALLY shouldn't happen.  Really.
            Log.e("KnownLocation", "JSONException trying to add data into the to-return object?  The hell?", je);
        }

        return toReturn;
    }

    /**
     * Stores a bunch of KnownLocations to preferences.  Note that this <b>replaces</b>
     * all currently-stored KnownLocations.
     *
     * @param c a Context
     * @param locations a List of KnownLocations
     */
    public static void storeKnownLocations(@NonNull Context c, @NonNull List<KnownLocation> locations) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor edit = prefs.edit();

        JSONArray arr = new JSONArray();

        for(KnownLocation kl : locations) {
            arr.put(kl.serialize());
        }

        // Man, that's easy.
        edit.putString(GHDConstants.PREF_KNOWN_LOCATIONS, arr.toString());
        edit.apply();

        BackupManager bm = new BackupManager(c);
        bm.dataChanged();
    }

    /**
     * Gets the name of this KnownLocation.
     *
     * @return a name
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Gets the LatLng this KnownLocation represents.
     *
     * @return a LatLng
     */
    @NonNull
    public LatLng getLatLng() {
        return mLocation;
    }

    /**
     * Convenience method that returns whether or not this KnownLocation lies
     * in 30W Rule territory.
     *
     * @return true if 30W, false if not
     */
    public boolean is30w() {
        return mLocation.longitude >= -30;
    }

    /**
     * Gets the range (in m) required before this KnownLocation will trigger a
     * notification.
     *
     * @return the range
     */
    public double getRange() {
        return mRange;
    }

    /**
     * <p>
     * Returns whether or not this KnownLocation is graticule-restricted.  That
     * is, if it should ONLY compare the location's native Graticule when
     * looking for the closest Info.
     * </p>
     *
     * @return true if this is graticule-restricted, false if not
     */
    public boolean isRestrictedGraticule() {
        return mRestrictGraticule;
    }

    /**
     * Convenience method to determine the distance from this KnownLocation to
     * the given Info.
     *
     * @param info the Info to check
     * @return the distance from here to the Info, in meters
     */
    public double getDistanceFrom(@NonNull Info info) {
        return (double)LocationUtil.latLngToLocation(mLocation).distanceTo(info.getFinalLocation());
    }

    /**
     * Determines if this KnownLocation is close enough to the given coordinates
     * to trigger a notification.  Note that if the range was specified as zero
     * or less, this will always return false.
     *
     * @param to the LatLng to which this is being compared
     * @return true if close enough, false if not
     */
    public boolean isCloseEnough(@NonNull LatLng to) {
        if(mRange <= 0.0) return false;

        // Stupid LatLngs.  I didn't have to deal with these conversions back
        // when everything just used Locations...
        float dist[] = new float[1];

        Location.distanceBetween(mLocation.latitude, mLocation.longitude, to.latitude, to.longitude, dist);

        return dist[0] <= mRange;
    }

    /**
     * Determines the closest non-globalhash Info to this KnownLocation for the
     * given date.  That is, it will check all nine graticules around this
     * KnownLocation and figures out which has the closest hashpoint.  Note that
     * if graticule restriction is on for this KnownLocation, it will ALWAYS
     * return the Info for the graticule in which this location lives.
     *
     * @param con a Context so we can get additional Infos
     * @param cal a Calendar representing the date to use
     * @return the closest Info to this KnownLocation
     * @throws IllegalArgumentException if there isn't any stock data for the given Calendar
     */
    @NonNull
    public Info getClosestInfo(@NonNull Context con,
                               @NonNull Calendar cal) throws IllegalArgumentException {
        // Get us a base Graticule.
        Graticule base = new Graticule(mLocation);

        // If we're in graticule restriction, short-circuit it to ONLY stick
        // to the base Graticule.
        if(mRestrictGraticule) {
            Info info = HashBuilder.getStoredInfo(con, cal, base);

            if(info == null)
                throw new IllegalArgumentException("Info didn't exist in the cache for that date!");

            return info;
        }

        double bestSoFar = Double.MAX_VALUE;
        Info bestInfo = null;

        for(int i = -1; i <= 1; i++) {
            for(int j = -1; j <= 1; j++) {
                // Offset the base Graticule, if need be...
                Graticule check = base;
                if(i != 0 && j != 0) {
                    check = Graticule.createOffsetFrom(base, i, j);
                }

                // Okay, now we can get an Info...
                Info info = HashBuilder.getStoredInfo(con, cal, check);

                if(info == null) {
                    // If the info is ever null, we're asking for a date that
                    // doesn't exist yet.  Doesn't matter if some of the infos
                    // in this loop succeeded (unless we already returned true);
                    // ALL the infos SHOULD ALWAYS exist if any of them do, so
                    // that's still really really bad.
                    throw new IllegalArgumentException("Info didn't exist in the cache for that date!");
                }

                // Now, how close is it?
                double dist = getDistanceFrom(info);
                if(dist < bestSoFar) {
                    bestSoFar = dist;
                    bestInfo = info;
                }
            }
        }

        // Well, whatever we have, it's the closest!
        if(bestInfo == null)
            throw new IllegalArgumentException("Couldn't find any Infos at all to compare!  The hell?");

        return bestInfo;
    }

    /**
     * <p>
     * Makes a MarkerOptions out of this KnownLocation (when added to the map,
     * you get the actual Marker back).  This can be directly placed on the map,
     * but you might want to stick it in something that can build a cluster or
     * something.
     * </p>
     *
     * <p>
     * Note that this MarkerOptions won't have a snippet.  The caller has to set
     * that itself.  The title, though, will be the KnownLocation's name.
     * </p>
     *
     * @param c a Context
     * @return a MarkerOptions representing this KnownLocation
     */
    @NonNull
    public MarkerOptions makeMarker(@NonNull Context c) {
        MarkerOptions toReturn = new MarkerOptions();

        toReturn.flat(false)
                .draggable(false)
                .icon(BitmapDescriptorFactory.fromBitmap(buildMarkerBitmap(c)))
                .anchor(0.5f, 1.0f)
                .position(mLocation)
                .title(mName);

        // The snippet should be set by the caller.  That'll either be
        // instructions to tap it again to edit/add it or the distance from it
        // to the hashpoint.

        return toReturn;
    }

    /**
     * Makes a CircleOptions out of this KnownLocation (when added to the map,
     * you get the actual Circle back).  This is used in KnownLocationsPicker to
     * give the user a better idea of what the range looks like.
     *
     * @param c a Context
     * @return a CircleOptions representing this KnownLocation's location and range
     */
    @NonNull
    public CircleOptions makeCircle(@NonNull Context c) {
        CircleOptions toReturn = new CircleOptions();

        KnownLocationPinData data = new KnownLocationPinData(c, mLocation);
        int baseColor = data.getColor();

        toReturn.center(mLocation)
                .radius(mRange)
                .strokeWidth(c.getResources().getDimension(R.dimen.known_location_circle_stroke_width))
                .strokeColor(
                        Color.argb(
                                c.getResources().getInteger(R.integer.known_location_circle_stroke_alpha),
                                Color.red(baseColor),
                                Color.green(baseColor),
                                Color.blue(baseColor)))
                .fillColor(
                        Color.argb(
                                c.getResources().getInteger(R.integer.known_location_circle_alpha),
                                Color.red(baseColor),
                                Color.green(baseColor),
                                Color.blue(baseColor)));

        return toReturn;
    }

    @NonNull
    private Bitmap buildMarkerBitmap(@NonNull Context c) {
        // Oh, this is going to be FUN.
        int dim = c.getResources().getDimensionPixelSize(R.dimen.known_location_marker_canvas_size);
        float radius = c.getResources().getDimension(R.dimen.known_location_pin_head_radius);

        Bitmap bitmap = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        KnownLocationPinData pinData = new KnownLocationPinData(c, mLocation);

        // Draw the pin line first.  That goes from the bottom-center up to
        // wherever the radius and length take us.
        float topX = Double.valueOf((dim / 2) + (pinData.getLength() * Math.cos(pinData.getAngle()))).floatValue();
        float topY = Double.valueOf(dim - (pinData.getLength() * Math.sin(pinData.getAngle()))).floatValue();
        paint.setStrokeWidth(c.getResources().getDimension(R.dimen.known_location_stroke));
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLACK);

        canvas.drawLine(dim / 2, dim, topX, topY, paint);

        // On the top of that line, fill in a circle.
        paint.setColor(pinData.getColor());
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(topX, topY, radius, paint);

        // And outline it.
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(topX, topY, radius, paint);

        return bitmap;
    }

    @Override
    public String toString() {
        return "\"" + mName + "\": " + mLocation.latitude + ", " + mLocation.longitude;
    }

    @Override
    public boolean equals(Object o) {
        if(o == this) return true;

        if(!(o instanceof KnownLocation)) return false;

        // Locations should have identical names, locations, and ranges if
        // we're expecting them to be identical.
        final KnownLocation other = (KnownLocation)o;

        return (mName == null ? other.mName == null : mName.equals(other.mName))
                && mRange == other.mRange
                && (mLocation == null ? other.mLocation == null : mLocation.equals(other.mLocation));
    }

    @Override
    public int hashCode() {
        // Good thing there's only three fields to hash up...
        int toReturn = 17;

        long convert = Double.doubleToLongBits(mRange);
        toReturn = 31 * toReturn + (int)(convert & (convert >>> 32));
        toReturn = 31 * toReturn + (mLocation == null ? 0 : mLocation.hashCode());
        toReturn = 31 * toReturn + (mName == null ? 0 : mName.hashCode());

        return toReturn;
    }
}
