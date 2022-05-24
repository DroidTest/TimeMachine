/*
 * Info.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.util;

import java.util.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import net.exclaimindustries.tools.DateTools;

/**
 * <p>
 * An <code>Info</code> object holds all the relevant info that involves the 
 * map. This, for the most part, involves the final destination, the current
 * date, and the graticule. It also includes utility methods for calculating
 * data from this information, most importantly the distance between some
 * location and the final destination.
 * </p>
 * 
 * <p>
 * <code>Info</code> objects are immutable and are meant to be generated from
 * HashBuilder as the last step once it has all the data it needs.  It can,
 * however, be built from anything else as need be.
 * </p>
 * 
 * @author Nicholas Killewald
 * 
 */
public class Info implements Parcelable {
    /** The earliest date at which the 30W Rule is used. */
    private static final Calendar LIMIT_30W = new GregorianCalendar(2008, Calendar.MAY, 26);

    private double mLatitude;
    private double mLongitude;
    private Graticule mGraticule;
    private Calendar mDate;
    private boolean mRetroHash;
    private boolean mValid;

    /**
     * Creates an Info object with the given data. That's it.  If making a
     * globalhash, give the latitude and longitude as the hash, not the full
     * coordinates (and make sure the graticule is null).
     * 
     * TODO: I've really got to find a better way to do globalhashes...
     * 
     * @param latitude
     *            the destination's latitude, as a double
     * @param longitude
     *            the destination's longitude, as a double
     * @param graticule
     *            the graticule
     * @param date
     *            the date
     */
    public Info(double latitude, double longitude, @Nullable Graticule graticule,
            @NonNull Calendar date) {
        mLatitude = latitude;
        mLongitude = longitude;
        mGraticule = graticule;
        setDate(date);
        mValid = true;
    }
    
    /**
     * Creates an Info object with the given graticule and date, but which is
     * invalid (i.e. has no valid latitude/longitude data).  This is used when
     * StockRunner reports an error; this way, any Handler can at least figure
     * out what was going on in the first place.
     * 
     * @param graticule the graticule
     * @param date the date
     */
    public Info(@Nullable Graticule graticule, @NonNull Calendar date) {
        mLatitude = 0;
        mLongitude = 0;
        mGraticule = graticule;
        setDate(date);
        mValid = false;
    }

    /**
     * Deparcelizes an Info object.  Obviously, this is used internally when we
     * need to rebuild Info from a Parcel, such as during Service operations.
     * 
     * @param in the parcel to deparcelize
     */
    private Info(Parcel in) {
        readFromParcel(in);
    }


    /**
     * Gets the latitude of the final destination.
     * 
     * @return the latitude
     */
    public double getLatitude() {
        if(mGraticule != null)
            return mLatitude;
        else
            return mLatitude * 180 - 90;
    }

    /**
     * Gets the longitude of the final destination.
     * 
     * @return the longitude
     */
    public double getLongitude() {
        if(mGraticule != null)
            return mLongitude;
        else
            return mLongitude * 360 - 180;
    }

    /**
     * Gets the fractional part of the latitude of the final destination.  That
     * is, the part determined by the hash.
     *
     * @return the fractional part of the latitude
     */
    public double getLatitudeHash() {
        if(mGraticule != null)
            return Math.abs(mLatitude) - mGraticule.getLatitude();
        else
            return mLatitude;
    }

    /**
     * Gets the fractional part of the longitude of the final destination.  That
     * is, the part determined by the hash.
     *
     * @return the fractional part of the longitude
     */
    public double getLongitudeHash() {
        if(mGraticule != null)
            return Math.abs(mLongitude) - mGraticule.getLongitude();
        else
            return mLongitude;
    }

    /**
     * Returns the final destination as a LatLng object, convenient for the Maps
     * v2 API.
     *
     * @return a LatLng based on the data obtained from the connection
     */
    @NonNull
    public LatLng getFinalDestinationLatLng() {
        return new LatLng(getLatitude(), getLongitude());
    }

    /**
     * Returns the final destination as a Location object, which isn't quite as
     * useful as a LatLng object, but you never know, it could come in handy.
     * 
     * @return a providerless Location based on the data obtained from the
     *         connection
     */
    @NonNull
    public Location getFinalLocation() {
        Location loc = new Location("");
        loc.setLatitude(getLatitude());
        loc.setLongitude(getLongitude());

        return loc;
    }

    /**
     * Gets the graticule.  This will be null if this is a globalhash.
     * 
     * @return the graticule
     */
    @Nullable
    public Graticule getGraticule() {
        return mGraticule;
    }

    /**
     * Gets the Calendar used to generate this set of information.
     * 
     * @return the Calendar
     */
    @NonNull
    public Calendar getCalendar() {
        return mDate;
    }

    /**
     * Gets the Date object from the Calendar object used to generate this set
     * of information.
     * 
     * @return the Date of the Calendar
     */
    @NonNull
    public Date getDate() {
        return mDate.getTime();
    }
    
    /**
     * Gets the distance, in meters, from the given Location and the final
     * destination.
     * 
     * @param loc
     *            Location to compare
     * @return the distance, in meters, to the final destination
     */
    public float getDistanceInMeters(@NonNull Location loc) {
        return loc.distanceTo(getFinalLocation());
    }

    /**
     * Gets the distance, in meters, from the given LatLng and the final
     * destination.
     *
     * @param latLng LatLng to compare
     * @return the distance, in meters, to the final destination
     */
    public float getDistanceInMeters(@NonNull LatLng latLng) {
        return locationFromLatLng(latLng).distanceTo(getFinalLocation());
    }
    
    /**
     * Returns a calendar representing the date from which the stock price was
     * pulled.  That is, back a day for the 30W Rule and rewinding to Friday if
     * it falls on a weekend.
     * 
     * @return a new adjusted Calendar
     */
    @NonNull
    public Calendar getStockCalendar() {
        return makeAdjustedCalendar(mDate, mGraticule);
    }
    
    /**
     * Returns a calendar representing the date from which the stock price was
     * pulled from a given date/graticule pair.  That is, back a day for the 30W
     * Rule or globalhashes and rewinding to Friday if it falls on a weekend.
     * 
     * @param c date to adjust
     * @param g Graticule to use to determine if the 30W Rule is in effect (if
     *          null, assumes this is a globalhash which is always back a day)
     * @return a new adjusted Calendar
     */
    @NonNull
    public static Calendar makeAdjustedCalendar(@NonNull Calendar c, @Nullable Graticule g) {
        // This adjusts the calendar for both the 30W Rule and to clamp all
        // weekend stocks to the preceding Friday.  This saves a few database
        // entries, as the weekend will always be Friday's value.  Note that
        // this doesn't account for holidays when the US stocks aren't trading.
        
        // First, clone the calendar.  We don't want to muck about with the
        // original for various reasons.
        Calendar cal = (Calendar)(c.clone());
        
        // Second, 30W Rule hackery.  If g is null, assume we're in a globalhash
        // (that is, adjustment is needed).  If the date is May 26, 2008 or
        // earlier (and this isn't a globalhash), ignore it anyway (the 30W Rule
        // only applies to non-globalhashes AFTER it was created).
        if(g == null || (cal.after(LIMIT_30W) && g.uses30WRule()))
            cal.add(Calendar.DAY_OF_MONTH, -1);
        
        // Third, if this new date is a weekend, clamp it back to Friday.
        if(cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY)
            // Saturday: Back one day
            cal.add(Calendar.DAY_OF_MONTH, -1);
        else if(cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            // SUNDAY SUNDAY SUNDAY!!!!!!: Back two days
            cal.add(Calendar.DAY_OF_MONTH, -2);
        
        // There!  Done!
        return cal;
    }

    @NonNull
    private static Location locationFromLatLng(@NonNull LatLng latLng) {
        Location loc = new Location("");

        loc.setLatitude(latLng.latitude);
        loc.setLongitude(latLng.longitude);

        return loc;
    }
    
    /**
     * Determines if this Info represents a point whose date follows the 30W
     * Rule.  Note that globalhashes always follow the 30W Rule.
     * 
     * @return true if 30W or global, false if not
     */
    public boolean uses30WRule() {
        // If mGraticule is null, this is always 30W.
        if(mGraticule == null) return true;
        
        // Otherwise, just forward it to the graticule itself.
        return mDate.after(LIMIT_30W) && mGraticule.uses30WRule();
    }
    
    /**
     * Determines if this Info represents a globalhash (and thus doesn't have
     * any sort of valid Graticule data).  Note that there's no way to set up
     * inspections to understand that isGlobalHash() == true implies
     * getGraticule() == null, so there may be issues that mean this rarely gets
     * called.
     * 
     * @return true if global, false if not
     */
    public boolean isGlobalHash() {
        return mGraticule == null;
    }
    
    /**
     * Determines if this Info represents a retrohash; that is, a geohash or
     * globalhash from a date in the past.  Note that this will return false for
     * geohashes from the future (i.e. a weekend when we already have the stock
     * values).
     * 
     * @return true if a retrohash, false if a current hash
     */
    public boolean isRetroHash() {
        return mRetroHash;
    }
    
    /**
     * Determines if this Info is valid.  A valid Info has latitude and
     * longitude data and can thus be sent straight to the map.  An invalid one
     * doesn't and shouldn't be used for hashing, but CAN be used in a
     * StockRunner handler to know what the date and graticule was.
     * 
     * @return true if valid, false if not
     */
    public boolean isValid() {
        return mValid;
    }
    
    public static final Parcelable.Creator<Info> CREATOR = new Parcelable.Creator<Info>() {
        public Info createFromParcel(Parcel in) {
            return new Info(in);
        }

        public Info[] newArray(int size) {
            return new Info[size];
        }
    };

    
    @Override
    public int describeContents() {
        // We don't do anything special with this.
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // Let's make us a parcel.  Order is important, remember!
        dest.writeDouble(mLatitude);
        dest.writeDouble(mLongitude);
        dest.writeParcelable(mGraticule, 0);
        dest.writeInt(mDate.get(Calendar.YEAR));
        dest.writeInt(mDate.get(Calendar.MONTH));
        dest.writeInt(mDate.get(Calendar.DAY_OF_MONTH));
        dest.writeInt(mRetroHash ? 1 : 0);
    }
    
    /**
     * Reads an incoming Parcel and deparcelizes it.  I'm going to keep using
     * the term "deparcelize" and its most logical forms until it catches on.
     * 
     * @param in parcel to deparcelize
     */
    public void readFromParcel(Parcel in) {
        // Same order!  Go!
        mLatitude = in.readDouble();
        mLongitude = in.readDouble();
        mGraticule = in.readParcelable(Graticule.class.getClassLoader());

        mDate = Calendar.getInstance();

        // In order, this better be year, month, day-of-month.
        mDate.set(in.readInt(), in.readInt(), in.readInt());

        mRetroHash = (in.readInt() == 1);
    }
    
    private void setDate(@NonNull Calendar cal) {
        // First, actually set the date.
        mDate = cal;
        
        // Then, determine if this is before or after today's date.  Since a
        // straight comparison also takes time into account, we need to force
        // today's date to midnight.
        Calendar today = Calendar.getInstance();
        
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        
        // Yes, this means that if the hash is in the future, mRetroHash will
        // be false.  The only way that can happen is if this is a weekend hash
        // and we're checking on Friday or something.
        mRetroHash = cal.before(today);
    }

    /**
     * Determines which Info of those given is closest to the also-given
     * Location.  The presence of the single Info param is because this is
     * generally called from the results of StockService with nearby points.
     * Any of the Infos (the single or the array) may be null; if both are null,
     * this will throw an exception.
     *
     * @param loc Location to compare against
     * @param info a single Info
     * @param nearby a bunch of Infos
     * @return the closest Info
     * @throws IllegalArgumentException info was null and nearby was either null or empty
     */
    @NonNull
    public static Info measureClosest(@NonNull Location loc, @Nullable Info info, @Nullable Info[] nearby)
        throws IllegalArgumentException {
        if(nearby == null || nearby.length == 0) {
            // If we were only given the single Info, return it.  Unless it's
            // null.
            if(info == null) {
                // If it's null, throw a fit.
                throw new IllegalArgumentException("You need to include at least one Info in measureClosest!");
            } else {
                return info;
            }
        }

        Info nearest = null;
        float bestDistance = Float.MAX_VALUE;

        // First, if we got a single Info, start with that.
        if(info != null) {
            nearest = info;
            bestDistance = loc.distanceTo(info.getFinalLocation());
        }

        // Now, loop through all the nearby Infos to see if any of those are any
        // better.
        for(Info i : nearby) {
            if(i == null) continue;

            float dist = loc.distanceTo(i.getFinalLocation());

            if(dist < bestDistance) {
                nearest = i;
                bestDistance = dist;
            }
        }

        // nearest can't be null here.  nearest gets assigned to be the single
        // info if it's not null, or at least one of the nearbys.  The only way
        // nearest can be null is if the distance of ALL the nearbys is equal to
        // Float.MAX_VALUE, which is just absurd.
        if(nearest == null)
            throw new IllegalArgumentException("You have impossible graticules that are somehow infinitely away from anything!");

        // And hey presto, we've got us a winner!
        return nearest;
    }

    @Override
    public String toString() {
        // This is mostly used for debugging purposes, so we may as well make it
        // useful.
        return "Info for "
                + (mGraticule == null ? "Globalhash" : "Graticule")
                + " on " + DateTools.getDateString(mDate)
                + "; point is at "
                + getLatitude() + "," + getLongitude();
    }

    @Override
    public boolean equals(Object o) {
        if(o == this) return true;
        if(!(o instanceof Info)) return false;

        final Info other = (Info)o;

        // I'm really sure I could make this clearer and/or more efficient if I
        // really thought more about it, and was not in a room full of other
        // reveling nerds as I tried writing it, but to compare the graticules
        // while making sure it doesn't NPE if either Info is a Globalhash...
        if(isGlobalHash() != other.isGlobalHash()) return false;

        // ...then, we see if the graticules match (if neither is a
        // Globalhash)...
        if(!isGlobalHash() && !(mGraticule.equals(other.mGraticule))) return false;

        // ...and also check the date, latitude, and longitude.
        //noinspection RedundantIfStatement
        if(!mDate.equals(other.mDate)
                || (getLatitude() != other.getLatitude())
                || (getLongitude() != other.getLongitude()))
            return false;

        // Otherwise, we match!
        return true;
    }

    @Override
    public int hashCode() {
        // Hash!
        int toReturn = 13;

        toReturn = 27 * toReturn + (mValid ? 1 : 0);
        toReturn = 27 * toReturn + (mRetroHash ? 1 : 0);
        long convert = Double.doubleToLongBits(mLatitude);
        toReturn = 27 * toReturn + (int)(convert ^ (convert >>> 32));
        convert = Double.doubleToLongBits(mLongitude);
        toReturn = 27 * toReturn + (int)(convert ^ (convert >>> 32));
        toReturn = 27 * toReturn + (mGraticule == null ? 0 : mGraticule.hashCode());
        toReturn = 27 * toReturn + (mDate == null ? 0 : mDate.hashCode());

        return toReturn;
    }
}
