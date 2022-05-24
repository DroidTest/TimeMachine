/*
 * DateTools.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.tools;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * <code>DateTools</code> contains any method useful in the manipulation or use
 * of dates.  All without subclassing Calendar, for some reason.
 *
 * @author Nicholas Killewald
 */
public class DateTools {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
    private static final SimpleDateFormat HYPHENATED_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
    private static final SimpleDateFormat WIKI_DATE_FORMAT = new SimpleDateFormat("HH:mm, d MMMM yyyy (z)", Locale.ENGLISH);

    /**
     * Generates a YYYYMMDD string from a given Calendar object.
     *
     * @param c Calendar from which to get the string
     * @return a YYYYMMDD string
     */
    public static String getDateString(@NonNull Calendar c) {
        return DATE_FORMAT.format(c.getTime());
    }
    
    /**
     * Generates a YYYY-MM-DD string from a given Calendar object.
     *
     * @param c Calendar from which to get the string
     * @return a YYYY-MM-DD string
     */
    public static String getHyphenatedDateString(@NonNull Calendar c) {
        // Turns out the SimpleDateFormat class does all the tricky work for me.
        // Huh.
        return HYPHENATED_DATE_FORMAT.format(c.getTime());
    }
    
    /**
     * Generates a date string similar to what MediaWiki would produce for a
     * five-tilde signature.  That is, something like "13:25, 25 March 2012
     * (EDT)", <i>specifically</i> in English.
     * 
     * Note that this is specifically calibrated for the Geohashing wiki.  If
     * you're going to use this outside of Geohash Droid, you may want to make
     * sure the wiki you're posting to uses this same format and language.
     * 
     * @param c a Calendar from which to get the string
     * @return a wiki-signature-like date string
     */
    public static String getWikiDateString(@NonNull Calendar c) {
        return WIKI_DATE_FORMAT.format(c.getTime());
    }

    /**
     * Returns whether or not these two Calendars represent the same date,
     * ignoring all other data (time of day, etc).
     *
     * @param base a Calendar
     * @param comparator another Calendar
     * @return true if the same date, false if not
     */
    public static boolean isSameDate(@NonNull Calendar base, @NonNull Calendar comparator) {
        return base.get(Calendar.YEAR) == comparator.get(Calendar.YEAR)
                && base.get(Calendar.DAY_OF_YEAR) == comparator.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Returns whether or not the FIRST Calendar is tomorrow compared to the
     * SECOND Calendar.
     *
     * @param isThisTomorrow Calendar you are wondering if it's tomorrow or not
     * @param comparedToThis Base Calendar to which you are comparing the first one
     * @return true if the first Calendar is tomorrow compared to the second, false if not
     */
    public static boolean isTomorrow(@NonNull Calendar isThisTomorrow, @NonNull Calendar comparedToThis) {
        Calendar comparator = (Calendar)comparedToThis.clone();
        comparator.add(Calendar.DAY_OF_MONTH, 1);
        return isSameDate(isThisTomorrow, comparator);
    }

    /**
     * Returns whether or not the FIRST Calendar is the day after tomorrow
     * compared to the SECOND Calendar.
     *
     * @param isThisTomorrow Calendar you are wondering if it's the day after tomorrow or not
     * @param comparedToThis Base Calendar to which you are comparing the first one
     * @return true if the first Calendar is the day after tomorrow compared to the second, false if not
     */
    public static boolean isDayAfterTomorrow(@NonNull Calendar isThisTomorrow, @NonNull Calendar comparedToThis) {
        Calendar comparator = (Calendar)comparedToThis.clone();
        comparator.add(Calendar.DAY_OF_MONTH, 2);
        return isSameDate(isThisTomorrow, comparator);
    }
}
