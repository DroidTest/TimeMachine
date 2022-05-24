/*
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

/**
 * This is a simple utility class which converts a distance output (in meters)
 * into whatever is needed for the job (kilometers, miles, feet).  It also turns
 * coordinates into whatever needs to be displayed (minutes/seconds, etc).
 * 
 * @author Nicholas Killewald
 */
public class UnitConverter {
    /** The number of feet per meter. */
    public static final double FEET_PER_METER = 3.2808399;
    /** The number of feet per mile. */
    public static final int FEET_PER_MILE = 5280;
    
    /** Output should be short, with fewer decimal places. */
    public static final int OUTPUT_SHORT = 0;
    /** Output should be long, with more decimal places. */
    public static final int OUTPUT_LONG = 1;
    /** Output should be even longer, with even more decimal places. */
    public static final int OUTPUT_DETAILED = 2;
    
    // All of the coordinate formats use Locale.US for the locale to force the
    // decimal delimiter to be a period instead of a comma.  Coordinates, as far
    // as I can tell, are always represented with a period, even if the country
    // in question uses commas.
    protected static final DecimalFormat SHORT_FORMAT = new DecimalFormat("##0.000", new DecimalFormatSymbols(Locale.US));
    protected static final DecimalFormat LONG_FORMAT = new DecimalFormat("##0.00000", new DecimalFormatSymbols(Locale.US));
    protected static final DecimalFormat DETAIL_FORMAT = new DecimalFormat("##0.00000000", new DecimalFormatSymbols(Locale.US));
    
    protected static final DecimalFormat SHORT_SECONDS_FORMAT = new DecimalFormat("##0.00", new DecimalFormatSymbols(Locale.US));
    protected static final DecimalFormat LONG_SECONDS_FORMAT = new DecimalFormat("##0.0000", new DecimalFormatSymbols(Locale.US));

    /** The standard short-form distance format. */
    public static final DecimalFormat DISTANCE_FORMAT_SHORT = new DecimalFormat("###.###");

    private static final String DEBUG_TAG = "UnitConverter";

    /**
     * Perform a distance conversion. This will attempt to get whatever
     * preference is set for the job and, using the given DecimalFormat, convert
     * it into a string, suitable for displaying.
     * 
     * @param c
     *            the context from which to get the preferences
     * @param df
     *            the format of the string
     * @param distance
     *            the distance, as returned by Location's distanceTo method
     * @return a String of the distance, with units marked
     */
    @NonNull
    public static String makeDistanceString(@NonNull Context c,
                                            @NonNull DecimalFormat df,
                                            float distance) {
        // First, get the current unit preference.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        String units = prefs.getString(GHDConstants.PREF_DIST_UNITS, GHDConstants.PREFVAL_DIST_METRIC);

        // Second, run the conversion.
        switch(units) {
            case GHDConstants.PREFVAL_DIST_METRIC:
                // Meters are easy, if only for the fact that, by default, the
                // Location object returns distances in meters. And the fact
                // that it's in powers of ten.
                if(distance >= 1000) {
                    return df.format(distance / 1000) + "km";
                } else {
                    return df.format(distance) + "m";
                }
            case GHDConstants.PREFVAL_DIST_IMPERIAL:
                // Convert!
                double feet = distance * FEET_PER_METER;

                if(feet >= FEET_PER_MILE) {
                    return df.format(feet / FEET_PER_MILE) + "mi";
                } else {
                    return df.format(feet) + "ft";
                }
            default:
                return units + "???";
        }
    }
    
    /**
     * Perform a coordinate conversion.  This will read in whatever preference
     * is currently in play (degrees, minutes, seconds) and return a string with
     * both latitude and longitude separated by a space.
     * 
     * @param c
     *            Context from whence the preference comes
     * @param l
     *            Location to calculate
     * @param useNegative
     *            true to use positive/negative values, false to use N/S or E/W
     * @param format
     *            specify the output format using one of the OUTPUT_ statics
     * @return
     *             a string form of the coordinates given
     */
    @NonNull
    public static String makeFullCoordinateString(@NonNull Context c,
                                                  @NonNull Location l,
                                                  boolean useNegative,
                                                  int format) {
        return makeLatitudeCoordinateString(c, l.getLatitude(), useNegative, format) + " "
            + makeLongitudeCoordinateString(c, l.getLongitude(), useNegative, format);
    }

    /**
     * Perform a coordinate conversion.  This will read in whatever preference
     * is currently in play (degrees, minutes, seconds) and return a string with
     * both latitude and longitude separated by a space.
     *
     * @param c
     *            Context from whence the preference comes
     * @param ll
     *            LatLng to calculate
     * @param useNegative
     *            true to use positive/negative values, false to use N/S or E/W
     * @param format
     *            specify the output format using one of the OUTPUT_ statics
     * @return
     *             a string form of the coordinates given
     */
    @NonNull
    public static String makeFullCoordinateString(@NonNull Context c,
                                                  @NonNull LatLng ll,
                                                  boolean useNegative,
                                                  int format) {
        Location l = new Location("");
        l.setLatitude(ll.latitude);
        l.setLongitude(ll.longitude);
        return makeFullCoordinateString(c, l, useNegative, format);
    }
    
    /**
     * This is the latitude half of makeFullCoordinateString.
     * 
     * @param c
     *            Context from whence the preference comes
     * @param lat
     *            Latitude to calculate
     * @param useNegative
     *            true to use positive/negative values, false to use N/S
     * @param format
     *            specify the output format using one of the OUTPUT_ statics
     * @return
     *             a string form of the latitude of the coordinates given
     */
    @NonNull
    public static String makeLatitudeCoordinateString(@NonNull Context c,
                                                      double lat,
                                                      boolean useNegative,
                                                      int format) {
        String units = getCoordUnitPreference(c);
        
        // Keep track of whether or not this is negative.  We'll attach the
        // prefix or suffix later.
        boolean isNegative = lat < 0;
        // Make this absolute so we know we won't have to juggle negatives until
        // we know what they'll wind up being.
        double rawCoord = Math.abs(lat);
        String coord;
        
        coord = makeCoordinateString(units, rawCoord, format);
        
        // Now, attach negative or suffix, as need be.
        if(useNegative) {
            if(isNegative)
                return "-" + coord;
            else
                return coord;
        } else {
            if(isNegative)
                return coord + "S";
            else
                return coord + "N";
        }
    }
    
    /**
     * This is the longitude half of makeFullCoordinateString.
     * 
     * @param c
     *            Context from whence the preference comes
     * @param lon
     *            Longitude to calculate
     * @param useNegative
     *            true to use positive/negative values, false to use E/W
     * @param format
     *            specify the output format using one of the OUTPUT_ statics
     * @return
     *             a string form of the longitude of the coordinates given
     */
    @NonNull
    public static String makeLongitudeCoordinateString(@NonNull Context c,
                                                       double lon,
                                                       boolean useNegative,
                                                       int format) {
        String units = getCoordUnitPreference(c);
        
        // Keep track of whether or not this is negative.  We'll attach the
        // prefix or suffix later.
        boolean isNegative = lon < 0;
        // Make this absolute so we know we won't have to juggle negatives until
        // we know what they'll wind up being.
        double rawCoord = Math.abs(lon);
        String coord;
        
        coord = makeCoordinateString(units, rawCoord, format);
        
        // Now, attach negative or suffix, as need be.
        if(useNegative) {
            if(isNegative)
                return "-" + coord;
            else
                return coord;
        } else {
            if(isNegative)
                return coord + "W";
            else
                return coord + "E";
        }
    }

    @NonNull
    private static String makeCoordinateString(@NonNull String units,
                                               double coord,
                                               int format) {
        // Just does the generic coordinate conversion stuff for coordinates.
        NumberFormat nf = NumberFormat.getInstance();

        try {
            switch(units) {
                case GHDConstants.PREFVAL_COORD_DEGREES:
                    // Easy case: Use the result Location gives us, modified by
                    // the longForm boolean.
                    switch(format) {
                        case OUTPUT_SHORT:
                            return SHORT_FORMAT.format(coord) + "\u00b0";
                        case OUTPUT_LONG:
                            return LONG_FORMAT.format(coord) + "\u00b0";
                        default:
                            return DETAIL_FORMAT.format(coord) + "\u00b0";
                    }
                case GHDConstants.PREFVAL_COORD_MINUTES: {
                    // Harder case 1: Minutes.
                    String temp = Location.convert(coord, Location.FORMAT_MINUTES);
                    String[] split = temp.split(":");

                    // Get the double form of the minutes...
                    double minutes = nf.parse(split[1]).doubleValue();

                    switch(format) {
                        case OUTPUT_SHORT:
                            return split[0] + "\u00b0" + SHORT_SECONDS_FORMAT.format(minutes) + "\u2032";
                        case OUTPUT_LONG:
                            return split[0] + "\u00b0" + LONG_SECONDS_FORMAT.format(minutes) + "\u2032";
                        default:
                            return split[0] + "\u00b0" + split[1] + "\u2032";
                    }
                }
                case GHDConstants.PREFVAL_COORD_SECONDS: {
                    // Harder case 2: Seconds.
                    String temp = Location.convert(coord, Location.FORMAT_SECONDS);
                    String[] split = temp.split(":");

                    // Get the double form of the seconds...
                    double seconds = nf.parse(split[2]).doubleValue();

                    switch(format) {
                        case OUTPUT_SHORT:
                            return split[0] + "\u00b0" + split[1] + "\u2032" + SHORT_SECONDS_FORMAT.format(seconds) + "\u2033";
                        case OUTPUT_LONG:
                            return split[0] + "\u00b0" + split[1] + "\u2032" + LONG_SECONDS_FORMAT.format(seconds) + "\u2033";
                        default:
                            return split[0] + "\u00b0" + split[1] + "\u2032" + split[2] + "\u2033";
                    }
                }
                default:
                    return "???";
            }
        } catch (Exception ex) {
            Log.e(DEBUG_TAG, "Exception thrown during coordinate conversion: " + ex.toString());
            ex.printStackTrace();
            return "???";
        }
    }
    
    /**
     * Grab the current coordinate unit preference.
     * 
     * @param c Context from whence the preferences arise
     * @return "Degrees", "Minutes", or "Seconds"
     */
    @NonNull
    public static String getCoordUnitPreference(@NonNull Context c) {
        // Units GO!!!
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        return prefs.getString(GHDConstants.PREF_COORD_UNITS, "Degrees");
    }
}
