/*
 * HexFraction.java
 * Copyright (C)2008 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.tools;

import java.math.BigDecimal;

/**
 * Contains a static method for parsing a hex string as if it were the
 * fractional part of a number and returning its fractional float value.
 * 
 * @author Nicholas Killewald
 */
public class HexFraction {
    /**
     * Converts a string, presumably the fractional part of a hex number, into
     * its fractional decimal counterpart. Don't feed it a negative.
     * 
     * @param s
     *            the hex string to convert
     * @return a float value of the hex string
     * @throws NumberFormatException
     *             parsing error with the string
     */
    public static double calculate(String s) throws NumberFormatException {
        // We're dealing with values to the precision of 1/(16^16). I think
        // BigDecimal is quite called for in this case.
        BigDecimal curvalue = new BigDecimal(0);

        // We need to parse the string one character at a time and continuously
        // calculate each digit's fractional hex value. Note, this WILL hurt.
        for (int i = 0; i < s.length(); i++) {
            // Get the hexit.
            String hexit = s.substring(i, i + 1);
            // Make it into an integer.
            int part = Integer.parseInt(hexit, 16);
            // Now divide it out.
            BigDecimal d1 = new BigDecimal(part);
            BigDecimal d2 = new BigDecimal(16);
            d2 = d2.pow(i + 1);
            curvalue = curvalue.add(d1.divide(d2));
            // Then repeat for the entire string.
        }
        // Finally, return at will!
        return curvalue.doubleValue();
    }
}
