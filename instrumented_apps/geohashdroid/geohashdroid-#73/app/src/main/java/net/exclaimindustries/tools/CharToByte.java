/*
 * CharToByte.java
 *
 * This file is distributed under the terms of the BSD license.
 * See the LICENSE file under the toplevel images/ directory for terms.
 */

package net.exclaimindustries.tools;

/**
 * <p>
 * Static methods that convert <code>byte</code> arrays to <code>char</code>
 * arrays and vice versa.
 * </p>
 */

public class CharToByte {
    /**
     * Converts the specified array of chars to an array of bytes.
     * 
     * @param chars
     *            char array to convert
     * @return an array of bytes
     */
    public static byte[] charsToBytes(char[] chars) {
        byte[] bytes = new byte[chars.length];
        int i;
        for (i = 0; i < chars.length; i++) {
            bytes[i] = (byte)(chars[i] & 0xFF);
        }
        return bytes;
    }

    /**
     * Converts the specified array of bytes to a String. The String will
     * consist of the hex digits of the byte array.
     * 
     * @param bytes
     *            bytes to convert
     * @return a String of hex digits
     */
    public static String bytesToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        String s;
        int i;
        for (i = 0; i < bytes.length; i++) {
            if (i % 32 == 0 && i != 0)
                sb.append("\n");
            s = Integer.toHexString(bytes[i]);
            if (s.length() < 2)
                s = "0" + s;
            if (s.length() > 2)
                s = s.substring(s.length() - 2);
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Converts the specified array of bytes to an array of chars. The char
     * array will be half as long as the byte array, since chars are 2 bytes
     * each.
     * 
     * @param bytes
     *            bytes to convert
     * @return a char array of hex digits
     */
    public static char[] bytesToChars(byte[] bytes) {
        char[] chars = new char[bytes.length / 2];
        for (int b = 0, c = 0; b < bytes.length; b += 2, c++) {
            int b1 = (int)bytes[b] & 0xFF;
            int b2 = (int)bytes[b + 1] & 0xFF;
            chars[c] = (char)((b1 << 8) + b2);
        }
        return chars;
    }

}
