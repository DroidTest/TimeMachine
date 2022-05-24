/*
 * Graticule.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.util;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

/**
 * <p>
 * A <code>Graticule</code> represents, well, a graticule. A 1x1 square degree
 * space on the earth's surface. The very heart of Geohashing*.  The base
 * implementation of a Graticule is designed to be immutable owing to a few odd
 * things that happen around the equator and Prime Meridian.
 * </p>
 * 
 * <p>
 * Note that Graticules are immutable.
 * </p>
 * 
 * <p>
 * *: Well, maybe not the heart. At least the kidneys for sure.
 * </p>
 * 
 * @author Nicholas Killewald
 */
public class Graticule implements Parcelable {
    private int mLatitude;
    private int mLongitude;

    // These are to account for the "negative zero" graticules.
    private boolean mSouth = false;
    private boolean mWest = false;
    
    /**
     * Constructs a new Graticule with the given Location object.
     * 
     * @param location Location to make a new Graticule out of
     */
    public Graticule(Location location) {
        this(location.getLatitude(), location.getLongitude());
    }

    /**
     * Constructs a new Graticule with the given LatLng object, because GeoPoint
     * isn't good enough for the v2 API anymore, apparently.
     *
     * @param latLng LatLng to make a new Graticule out of
     */
    public Graticule(LatLng latLng) {
        this(latLng.latitude, latLng.longitude);
    }

    /**
     * <p>
     * Constructs a new Graticule with the given latitude and longitude. Note
     * that values that shoot around the planet will be clamped to 89 degrees
     * latitude and 179 degrees longitude (positive or negative).
     * </p>
     * 
     * <p>
     * With this constructor, you <b>MUST</b> specify if this is south or west
     * (that is, negative values). This is to account for the "negative zero"
     * graticules, for those living on the Prime Meridian or equator, as you
     * can't very well input -0 as a Java int and have it distinct from 0.
     * </p>
     * 
     * <p>
     * This will also ignore any negatives in your inputs (-75 will become 75).
     * </p>
     * 
     * @param latitude latitude to set
     * @param south true if south, false if north
     * @param longitude longitude to set
     * @param west true if west, false if east
     */
    public Graticule(int latitude, boolean south, int longitude, boolean west) {
        this.mSouth = south;
        this.mWest = west;
        this.setLatitude(Math.abs(latitude));
        this.setLongitude(Math.abs(longitude));
    }

    /**
     * <p>
     * Constructs a new Graticule with the given latitude and longitude as
     * doubles. This can thus make Graticules directly from GPS inputs. Note
     * that values that shoot around the planet will be clamped to 89 degrees
     * latitude and 179 degrees longitude (positive or negative).
     * </p>
     * 
     * <p>
     * Negative values will be interpreted as south and west. Please don't use
     * this if you're standing directly on the equator and/or Prime Meridian and
     * GPS gives you a direct zero.
     * </p>
     * 
     * @param latitude latitude to set
     * @param longitude longitude to set
     */
    public Graticule(double latitude, double longitude) {
        mSouth = latitude < 0;
        mWest = longitude < 0;
        this.setLatitude(Math.abs((int)latitude));
        this.setLongitude(Math.abs((int)longitude));
    }

    /**
     * Constructs a new Graticule with the given String forms of the latitude
     * and longitude.
     * 
     * @param latitude latitude to set
     * @param longitude longitude to set
     * @throws NullPointerException either of the input strings were empty
     * @throws NumberFormatException either of the input strings weren't numbers
     */
    public Graticule(String latitude, String longitude)
            throws NullPointerException, NumberFormatException {
        mSouth = latitude.charAt(0) == '-';
        mWest = longitude.charAt(0) == '-';
        this.setLatitude(Math.abs(Integer.valueOf(latitude)));
        this.setLongitude(Math.abs(Integer.valueOf(longitude)));
    }

    /**
     * <p>
     * Constructs a new Graticule offset from an existing one.  That is to say,
     * copy an existing Graticule and move it by however many degrees as is
     * specified.  Under the current implementation, if this gets offset past
     * the edges of the earth, it will attempt to wrap around.  This allows
     * people in the far eastern regions of Russia to see the nearby meetup
     * points if they happen to live near the 180E/W longitude line.  It does
     * not, however, allow for penguins and Santa Claus yet, so don't try to
     * fling yourself over the poles.
     * </p>
     *
     * <p>
     * Note carefully that moving one degree west of zero longitude will go to
     * "negative zero" longitude.  Same with latitude.  There is a distinction.
     * Therefore, be very careful when crossing the Prime Meridian and/or the
     * equator.
     * </p>
     *
     * @param g Graticule to copy
     * @param latOff number of degrees north to offset (negative is south)
     * @param lonOff number of degrees east to offset (negative is west)
     * @return a brand spankin' new Graticule, offset as per suggestion
     */
    @NonNull
    public static Graticule createOffsetFrom(@NonNull Graticule g, int latOff, int lonOff) {
        // If we're just returning the same Graticule, seriously, come on now.
        if(latOff == 0 && lonOff == 0) return g;

        // We already have all the data we need from the old Graticule.  But,
        // we need to account for passing through the Prime Meridian and/or
        // equator.  If the sign changes, decrement the amount of the change by
        // one.  This logic is gratuitously loopy.
        boolean goingSouth = (latOff < 0);
        latOff = Math.abs(latOff);

        int finalLat = g.getLatitude();
        int finalLon = g.getLongitude();
        boolean finalSouth = g.isSouth();
        boolean finalWest = g.isWest();

        // Skip the following if latitude is unaffected.
        if (latOff != 0) {
            if (g.isSouth() == goingSouth) {
                // Going the same direction, no equator-hacking needed.
                finalLat = g.getLatitude() + latOff;
            } else {
                // Going opposite directions, check for equator-hacking.
                if (g.getLatitude() < latOff) {
                    // We cross the equator!
                    latOff--;
                    finalSouth = !finalSouth;
                }
                finalLat = Math.abs(g.getLatitude() - latOff);
            }
        }

        // Meridian hacking can be handled differently to also cover planet-
        // wrapping at the same time.  This entire stunt depends on treating
        // the longitude as a value between 0 and 359, inclusive.  In this,
        // 179W is 0, 0W is 179, 0E is 180, and 179E is 359.
        
        // Adjust us properly.  Remember the negative zero graticules!
        if(finalWest)
            finalLon = -finalLon + 179;
        else
            finalLon += 180;
        
        finalLon += lonOff;
        finalLon %= 360;
        
        if(finalLon < 0) finalLon = 360 - Math.abs(finalLon);
        
        if(finalLon >= 180) {
            finalWest = false;
            finalLon -= 180;
        } else {
            finalWest = true;
            finalLon -= 179;
        }
        
        finalLon = Math.abs(finalLon);
        
        // Now make the new Graticule object and return it.
        return new Graticule(finalLat, finalSouth, finalLon, finalWest);
    }
    
    /**
     * Deparcelizinate a Graticule.
     * 
     * @param in the parcel to deparcelize
     */
    private Graticule(Parcel in) {
        readFromParcel(in);
    }
    
    public static final Parcelable.Creator<Graticule> CREATOR = new Parcelable.Creator<Graticule>() {
        public Graticule createFromParcel(Parcel in) {
            return new Graticule(in);
        }

        public Graticule[] newArray(int size) {
            return new Graticule[size];
        }
    };
    
    /**
     * Deparcel.  Read from parcel.  Deparcelize.  This constructs a Graticule
     * from a Parcel.
     * 
     * @param in parcel to deparcelize
     */
    public void readFromParcel(Parcel in) {
        // For the sake of efficiency, we store exactly two things in the
        // parcel.  Specifically, the latitude and longitude, represented from
        // 0-179 and 0-356, respectively, going from 89 south to 89 north and
        // 179 west to 179 east (both including a negative zero).  We can
        // determine mSouth and mWest from there.
        int absLat = in.readInt();
        int absLon = in.readInt();
        
        // I swear, if these wind up not being valid, I reserve the right to
        // dope slap you.
        if(absLat < 90) {
            mSouth = true;
            setLatitude(89 - absLat);
        } else {
            mSouth = false;
            setLatitude(absLat - 90);
        }
        
        if(absLon < 180) {
            mWest = true;
            setLongitude(179 - absLon);
        } else {
            mWest = false;
            setLongitude(absLon - 180);
        }
    }
    
    @Override
    public int describeContents() {
        // BLAH BLAH BLAH
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // Hey!  We've got a parcel to write out!  To compress this down a bit
        // further, we want to only store two ints (instead of two ints and two
        // booleans).  See the comments in readFromParcel for details.  To wit:
        
        // Latitude!
        if(mSouth)
            dest.writeInt(Math.abs(mLatitude - 89));
        else
            dest.writeInt(mLatitude + 90);
        
        if(mWest)
            dest.writeInt(Math.abs(mLongitude - 179));
        else
            dest.writeInt(mLongitude + 180);
    }

    /**
     * Returns true if the 30W Rule is in effect. Which is to say, anything east
     * of -30 longitude uses yesterday's stock value, regardless of if the DJIA
     * was updated to that point.  Note that this only determines if the
     * graticule itself abides by the 30W Rule; if the date is May 26, 2008 or
     * earlier, 30W is ignored.
     * 
     * @return true if the 30W Rule is in effect, false otherwise
     */
    public boolean uses30WRule() {
        return (mLongitude < 30 || !isWest());
    }

    private void setLatitude(int latitude) {
        // Work out invalid entries by clamping 'em down.
        if (latitude > 89)
            latitude = 89;
        
        this.mLatitude = latitude;
    }

    /**
     * Returns the absolute value of the current latitude. Run this against
     * isSouth() to figure out what the negative should be.
     * 
     * @return the absolute value of the current latitude
     */
    public int getLatitude() {
        return mLatitude;
    }

    /**
     * Returns the current latitude as a String to account for negative zero
     * graticule wackiness.
     * @param useNegativeValues true to return values as negative for south and positive for north, false to return values with N and S indicators
     * @return the current latitude as a String
     */
    @NonNull
    public String getLatitudeString(boolean useNegativeValues) {
        if (mSouth) {
            if(useNegativeValues) {
                return "-" + mLatitude;                
            } else {
                return mLatitude + "S";
            }
        } else {
            if(useNegativeValues) {
                return Integer.valueOf(mLatitude).toString();
            } else {
                return mLatitude + "N";
            }
        }
    }
    
    private void setLongitude(int longitude) {
        // Clamp!  Clamp!  Clamp!
        if (longitude > 179)
            longitude = 179;
       
        this.mLongitude = longitude;
    }

    /**
     * Returns the absolute value of the current longitude. Run this against
     * isEast() to figure out what the negative should be.
     * 
     * @return the absolute value of the current longitude
     */
    public int getLongitude() {
        return mLongitude;
    }

    /**
     * Returns the current longitude as a String to account for negative zero
     * graticule madness.
     * 
     * @param useNegativeValues true to return values as negative for west and positive for east, false to return values with E and W indicators
     * @return the current longitude as a String
     */
    @NonNull
    public String getLongitudeString(boolean useNegativeValues) {
        if (mWest) {
            if(useNegativeValues) {
                return "-" + mLongitude;
            } else {
                return mLongitude + "W";
            }
        } else {
            if(useNegativeValues) {
                return Integer.valueOf(mLongitude).toString();
            } else {
                return mLongitude + "E";
            }
        }
    }

    /**
     * Returns whether or not this is a southern latitude (negative).
     * 
     * @return true if south, false if north
     */
    public boolean isSouth() {
        return mSouth;
    }

    /**
     * Returns whether or not this is an western longitude (negative).
     * 
     * @return true if west, false if east.
     */
    public boolean isWest() {
        return mWest;
    }

    /**
     * Returns the center of this Graticule as a LatLng.
     *
     * @return a LatLng representing the center of this Graticule.
     */
    @NonNull
    public LatLng getCenterLatLng() {
        double lat, lon;

        if(isSouth()) {
            lat = -getLatitude() - 0.5;
        } else {
            lat = getLatitude() + 0.5;
        }

        if(isWest()) {
            lon = -getLongitude() - 0.5;
        } else {
            lon = getLongitude() + 0.5;
        }

        return new LatLng(lat, lon);
    }

    /**
     * Make a Maps v2 PolygonOptions out of this Graticule.  You can then style
     * it yourself and toss it into a map as need be.
     *
     * @return a PolygonOptions set up as this Graticule sits.
     */
    @NonNull
    public PolygonOptions getPolygon() {
        PolygonOptions toReturn = new PolygonOptions();

        int top, left, bottom, right;

        if(isSouth()) {
            bottom = -getLatitude() - 1;
            top = -getLatitude();
        } else {
            bottom = getLatitude();
            top = getLatitude() + 1;
        }

        if(isWest()) {
            right = -getLongitude() - 1;
            left = -getLongitude();
        } else {
            right = getLongitude();
            left = getLongitude() + 1;
        }

        // Now, draw the polygon.  Er... make the options.
        toReturn.add(new LatLng(top, left))
                .add(new LatLng(top, right))
                .add(new LatLng(bottom, right))
                .add(new LatLng(bottom, left));

        // Shove this into a GoogleMap, and style it as need be.
        return toReturn;
    }

    /**
     * <p>
     * Makes a LatLng out of this Graticule and component fractional hash parts.
     * In other words, this forces the fractional bits into a proper location
     * based on this Graticule.
     * </p>
     *
     * <p>
     * TODO: HashBuilder could start calling this instead...
     * </p>
     *
     * @param latHash the fractional latitude portion of the hash
     * @param lonHash the fractional longitude portion of the hash
     * @return a new LatLng
     * @throws IllegalArgumentException if latHash or lonHash are less than 0 or greater than 1
     */
    @NonNull
    public LatLng makePointFromHash(double latHash, double lonHash) {
        if(latHash < 0 || latHash > 1 || lonHash < 0 || lonHash > 1)
            throw new IllegalArgumentException("Those aren't valid hash values!");

        // getLatitude and getLongitude are absolute values, so we can do this:
        latHash += getLatitude();
        lonHash += getLongitude();

        // And then we adjust for south/west like so...
        if(isSouth()) latHash *= -1;
        if(isWest()) lonHash *= -1;

        // And out it goes!
        return new LatLng(latHash, lonHash);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o) {
        // First, this better be a Graticule.
        if(o == this) return true;
        if (!(o instanceof Graticule))
            return false;

        final Graticule g = (Graticule)o;

        // If everything matches up, these are identical. Two int checks and
        // two boolean checks are probably a lot faster than two String checks,
        // right?
        return !(g.getLatitude() != getLatitude()
                || g.getLongitude() != getLongitude()
                || g.isSouth() != isSouth() || g.isWest() != isWest());
    }

    @Override
    public int hashCode() {
        // Um... 11!  That's a prime number, right?
        int toReturn = 11;

        // And so's 37!
        toReturn = 37 * toReturn + mLatitude;
        toReturn = 37 * toReturn + mLongitude;
        toReturn = 37 * toReturn + (mSouth ? 0 : 1);
        toReturn = 37 * toReturn + (mWest ? 0 : 1);

        return toReturn;
    }

    @Override
    public String toString() {
        return "Graticule for " + getLatitudeString(false) + " " + getLongitudeString(false);
    }
}
