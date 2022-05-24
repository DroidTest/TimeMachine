/*
 * HumanBytes.java
 * Copyright (C) 2006 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * See the LICENSE file under the toplevel images/ directory for terms.
 */

package net.exclaimindustries.tools;

import java.text.NumberFormat;

/**
 *<p>
 * <CODE>HumanBytes</CODE> contains a set of static methods that help in making
 * lengths of bytes easier to read by humans. As in, it attaches 'k' and 'M'
 * suffixes where appropriate and truncates values. Note that these represent
 * kilobytes in multiples of 1024, not 1000, and likewise up the chain.
 *</p>
 * 
 *<p>
 * Something tells me something in Java does this already, but I was having fun
 * one day and just decided to code this up.
 *</p>
 * 
 * @author Nicholas Killewald
 */
public final class HumanBytes {

    /** One kilobye (1024) */
    public static final long ONEKILO = 1024;
    /** One megabyte (1024^2) */
    public static final long ONEMEG = 1048576;
    /** One gigabyte (1024^3) */
    public static final long ONEGIG = 1073741824;
    /** One terabyte (1024^4) */
    public static final long ONETERA = 1099511627776L;
    /** One petabyte (1024^5) */
    public static final long ONEPETA = 1125899906842624L;

    /**
     *<p>
     * Turns the specified long into whatever the most ideal qualifier is. That
     * is, reduce it to whatever makes the most sense. For some examples:
     *</p>
     * 
     *<ul>
     *<li><code>HumanBytes.toIdeal(54099l)</code> returns "54.83kB"
     *<li><code>HumanBytes.toIdeal(84312267l)</code> returns "80.41MB"
     *<li><code>HumanBytes.toIdeal(463311975466l)</code> returns "431.5GB"
     *</ul>
     * 
     *<p>
     * Thus, this simply calls other methods depending on the size of the long.
     * Note that this will account for negatives if need be.
     *</p>
     * 
     * @param input
     *            long to convert
     * @return a String of the ideal human-readable representation of the long
     */
    public static String toIdeal(long input) {
        // NOTE TO SELF: A long is a 64-bit signed value. 64 bits of value
        // is 18,446,744,073,709,551,616. So, 64 bits of signed value would
        // be in the 9e18 range. (base), k, M, G, T, P.

        // First off, if this is less than 1024 (by absoluteness), just return
        // it.
        if (Math.abs(input) < 1024)
            return input + "B";

        // Now, go in order from biggest to smallest. Comparisons are easier
        // on the system than comparisons and divisions.
        if (Math.abs(input) >= ONEPETA)
            return toPeta(input);
        if (Math.abs(input) >= ONETERA)
            return toTera(input);
        if (Math.abs(input) >= ONEGIG)
            return toGiga(input);
        if (Math.abs(input) >= ONEMEG)
            return toMega(input);
        if (Math.abs(input) >= ONEKILO)
            return toKilo(input);

        // Fall out; this can't happen
        return input + "B";
    }

    /**
     * Turns the given long into a petabyte representation.
     * 
     * @param input
     *            long to convert
     * @return String representation of the input in petabytes
     */
    public static String toPeta(long input) {
        float toReturn = (float)(input) / ONEPETA;
        return finishIt(toReturn, "PB");
    }

    /**
     * Turns the given long into a terabyte representation.
     * 
     * @param input
     *            long to convert
     * @return String representation of the input in terabytes
     */
    public static String toTera(long input) {
        float toReturn = (float)(input) / ONETERA;
        return finishIt(toReturn, "TB");
    }

    /**
     * Turns the given long into a gigabyte representation.
     * 
     * @param input
     *            long to convert
     * @return String representation of the input in gigabytes
     */
    public static String toGiga(long input) {
        float toReturn = (float)(input) / ONEGIG;
        return finishIt(toReturn, "GB");
    }

    /**
     * Turns the given long into a megabyte representation.
     * 
     * @param input
     *            long to convert
     * @return String representation of the input in megabytes
     */
    public static String toMega(long input) {
        float toReturn = (float)(input) / ONEMEG;
        return finishIt(toReturn, "MB");
    }

    /**
     * Turns the given long into a kilobyte representation.
     * 
     * @param input
     *            long to convert
     * @return String representation of the input in kilobytes
     */
    public static String toKilo(long input) {
        float toReturn = (float)(input) / ONEKILO;
        return finishIt(toReturn, "kB");
    }

    /**
     *<p>
     * Finishes up output from a conversion. This means putting the right
     * decimals in, adding commas, and attaching the suffix.
     *</p>
     * 
     * @param input
     *            number to convert
     * @param suffix
     *            suffix to attach to the result
     * @return the appropriately formatted number
     */
    private static String finishIt(float input, String suffix) {
        NumberFormat nf = NumberFormat.getInstance();

        // If the resulting number is >= 1,000, just return
        // the int part of it and appropriate commas.
        if (Math.abs(input) >= 1000) {
            nf.setMaximumFractionDigits(0);
            return nf.format(input) + suffix;
        }

        // In the range of 100 to 999, we want one decimal
        if (Math.abs(input) < 1000 && Math.abs(input) >= 100) {
            nf.setMaximumFractionDigits(1);
            return nf.format(input) + suffix;
        }

        // In the range of 0 to 99, we want two decimals

        if (Math.abs(input) < 100 && Math.abs(input) >= 0) {
            nf.setMaximumFractionDigits(2);
            return nf.format(input) + suffix;
        }

        // And now we just kinda die.
        return nf.format(input) + suffix;
    }
}
