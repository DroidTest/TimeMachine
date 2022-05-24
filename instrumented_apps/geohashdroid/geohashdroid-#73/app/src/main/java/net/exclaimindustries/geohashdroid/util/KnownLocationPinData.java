/*
 * KnownLocationPinData.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.content.Context;
import android.graphics.Color;
import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import net.exclaimindustries.geohashdroid.R;

import java.util.Random;

/**
 * This handles the drawn pin data used by KnownLocation and the search result
 * markers in KnownLocationsPicker.  That is, given a LatLng, it uses that as a
 * hash to pick a pseudo-random color, pin angle, and pin length.
 */
public class KnownLocationPinData {
    private final double mAngle;
    private final float mLength;
    private final int mHue;

    /**
     * Creates the object and initializes the data portions.
     *
     * @param c a Context from which Resources will be derived
     * @param loc a LatLng to hash up
     */
    public KnownLocationPinData(@NonNull Context c, @NonNull LatLng loc) {
        Random rand = makeRandom(loc);

        // The angle, length, and hue are thus the first three of their
        // respective types off the top.
        mAngle = Math.toRadians((rand.nextDouble() * 20.0f) + 80.0f);
        mLength = c.getResources().getDimension(R.dimen.known_location_pin_base_length) * (1 - (rand.nextFloat() * 0.5f));
        mHue = rand.nextInt(360);
    }

    @NonNull
    private Random makeRandom(@NonNull LatLng loc) {
        // What we're looking for here is a stable randomizer with the seed
        // initialized to something (reasonably) unique to the location given.
        // So, more of a hashing function, really.  java.util.Random, as the
        // docs assure me, will ALWAYS be a certain algorithm for portability's
        // sake, and thus always give the same results (they also assure me this
        // is specifically NOT suitable for anything having to do with security,
        // which sounds like what I'm looking for).

        // So, to generate our seed, we're going to convert the latitude and
        // longitude into 32-bit ints.  Sort of.  More like we're going to
        // multiply them up so they're more reasonably in the domain of
        // -(2^31 - 1)...2^31.  Then, we bit-shift one of them such that we can
        // add both together into a long whose bits are reasonably unique,
        // giving us a seed that's reasonably unique.  This is entirely the
        // wrong way to do this.
        long latPart = Double.doubleToLongBits(loc.latitude);
        long lonPart = Double.doubleToLongBits(loc.longitude) << 32;

        long seed = latPart + lonPart;

        return new Random(seed);
    }

    /**
     * Gets the angle at which the pin should be drawn, in degrees.
     *
     * @return the angle
     */
    public double getAngle() {
        return mAngle;
    }

    /**
     * Gets the length of the pin.  The pin's head should have its center be
     * at the vertical end of the pin itself.
     *
     * @return the pin length
     */
    public float getLength() {
        return mLength;
    }

    /**
     * Gets the pin's hue.
     *
     * @return the hue
     */
    public int getHue() {
        return mHue;
    }

    /**
     * Gets the actual pin color.
     *
     * @return the color
     */
    public int getColor() {
        return Color.HSVToColor(new float[]{mHue, 1.0f, 0.8f});
    }
}
