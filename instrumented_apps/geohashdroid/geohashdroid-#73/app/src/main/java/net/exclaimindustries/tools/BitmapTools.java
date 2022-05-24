/*
 * BitmapTools.java
 * Copyright (C)2010 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.tools;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * BitmapTools are, as you probably guessed, tools for Bitmap manipulation.
 * Static tools, too.
 * 
 * @author Nicholas Killewald
 */
public class BitmapTools {
    private static final String DEBUG_TAG = "BitmapTools";
    
    /**
     * Creates a new Bitmap that's a scaled version of the given Bitmap, but
     * with the aspect ratio preserved.  Note that this will only scale down; if
     * the image is already smaller than the given dimensions, this will return
     * the same bitmap that was given to it.
     *  
     * @param bitmap Bitmap to scale
     * @param maxWidth max width of new Bitmap, in pixels
     * @param maxHeight max height of new Bitmap, in pixels
     * @param reversible whether or not the ratio should be treated as
     *                   reversible; that is, if the maxWidth and maxHeight are
     *                   given as 800x600, but the image is 600x800, it will
     *                   leave the image as 600x800 instead of reduce it to 
     *                   450x600
     * @return a new, scaled Bitmap, or the old bitmap if no scaling took place, or null if it failed entirely
     */
    public static Bitmap createRatioPreservedDownscaledBitmap(Bitmap bitmap, int maxWidth, int maxHeight, boolean reversible) {
        if(bitmap == null) return null;

        // Make sure the width and height are properly reversed, if needed.
        if(reversible && shouldBeReversed(maxWidth, maxHeight, bitmap.getWidth(), bitmap.getHeight())) {
            int t = maxWidth;
            //noinspection SuspiciousNameCombination
            maxWidth = maxHeight;
            maxHeight = t;
        }

        if(bitmap.getHeight() > maxHeight || bitmap.getWidth() > maxWidth) {
            // So, we determine how we're going to scale this, mostly
            // because there's no method in Bitmap to maintain aspect
            // ratio for us.
            double scaledByWidthRatio = ((double)maxWidth) / (double)bitmap.getWidth();
            double scaledByHeightRatio = ((double)maxHeight) / (double)bitmap.getHeight();

            int newWidth;
            int newHeight;

            if (bitmap.getHeight() * scaledByWidthRatio <= maxHeight) {
                // Scale it by making the width the max, as scaling the
                // height by the same amount makes it less than or equal
                // to the max height.
                newWidth = maxWidth;
                newHeight = (int)Math.round(bitmap.getHeight() * scaledByWidthRatio);
            } else {
                // Otherwise, go by making the height its own max.
                newWidth = (int)Math.round(bitmap.getWidth() * scaledByHeightRatio);
                newHeight = maxHeight;
            }

            // Now, do the scaling!  The caller must take care of GCing the
            // original Bitmap.
            Log.d(DEBUG_TAG, "Scaling file down to " + newWidth + "x" + newHeight + "...");
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        } else {
            // If it's too small already, just return what came in.
            Log.d(DEBUG_TAG, "File is already small enough (" + bitmap.getWidth() + "x" + bitmap.getHeight() + ")");
            if(bitmap.isMutable())
                return bitmap;
            else
                return bitmap.copy(bitmap.getConfig(), true);
        }
    }

    /**
     * Creates a new Bitmap that's a downscaled, ratio-preserved version of
     * a file on disk.  I'll admit there's probably a shorter name I could have
     * used, but none came to mind.  The major difference between this and the
     * Bitmap-oriented one is that it will attempt a rough downsampling before
     * it loads the original into memory, which should save tons of RAM and
     * avoid unsightly OutOfMemoryErrors.
     *
     * @param filename location of bitmap to open
     * @param maxWidth max width of new Bitmap, in pixels
     * @param maxHeight max height of new Bitmap, in pixels
     * @param reversible whether or not the ratio should be treated as
     *                   reversible; that is, if the maxWidth and maxHeight are
     *                   given as 800x600, but the image is 600x800, it will
     *                   leave the image as 600x800 instead of reduce it to 
     *                   450x600
     * @return a new, appropriately scaled Bitmap, or null if it failed entirely
     */
    public static Bitmap createRatioPreservedDownscaledBitmapFromFile(String filename, int maxWidth, int maxHeight, boolean reversible) {
        // First up, open the Bitmap ONLY for its size, if we can.
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        // This will always return null thanks to inJustDecodeBounds.
        BitmapFactory.decodeFile(filename, opts);

        // If the height or width are -1 in opts, we failed.
        if(opts.outHeight < 0 || opts.outWidth < 0) {
            Log.e(DEBUG_TAG, "Error opening file " + filename);
            return null;
        }
        
        // Make sure the width and height are properly reversed, if needed.
        if(reversible && shouldBeReversed(maxWidth, maxHeight, opts.outWidth, opts.outHeight)) {
            int t = maxWidth;
            //noinspection SuspiciousNameCombination
            maxWidth = maxHeight;
            maxHeight = t;
        }

        // Now, determine the best power-of-two to downsample by.  We
        // intentionally want it one level LOWER than the target; subsampling
        // doesn't do any sort of filtering or interpolation at all, meaning if
        // we wind up where it's a clean power-of-two to reduce it, the result
        // will be grainy and blocky.  This way, we wind up scaling it later
        // WITH filtering but with far less memory being used, which is a fair
        // tradeoff.
        int tempWidth = opts.outWidth;
        int tempHeight = opts.outHeight;
        int sampleFactor = 1;
        while(tempWidth / 2 >= maxWidth && tempHeight / 2 >= maxHeight) {
            tempWidth /= 2;
            tempHeight /= 2;
            sampleFactor *= 2;
        }
        
        Log.d(DEBUG_TAG, "Downsampling file to " + tempWidth + "x" + tempHeight + "...");

        // Good!  Now, let's pop it open and scale it the rest of the way.
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sampleFactor;

        // The reversible flag is always false here, as we've already applied
        // it beforehand.
        return createRatioPreservedDownscaledBitmap(BitmapFactory.decodeFile(filename, opts), maxWidth, maxHeight, false);
    }

    /**
     * Creates a new Bitmap that's a downscaled, ratio-preserved version of the
     * content at a URI.  This will need to be something that ContentResolver
     * can figure out, AND in a way that it can decode the image bounds properly
     * before a full load.  If it can't do that, it'll return a null.
     *
     * @param context Context from which a ContentResolver can be retrieved
     * @param uri URI of content to load
     * @param maxWidth max width of new Bitmap, in pixels
     * @param maxHeight max height of new Bitmap, in pixels
     * @param reversible whether or not the ratio should be treated as
     *                   reversible; that is, if the maxWidth and maxHeight are
     *                   given as 800x600, but the image is 600x800, it will
     *                   leave the image as 600x800 instead of reduce it to
     *                   450x600
     * @return a new, appropriately scaled Bitmap, or null if it failed entirely
     */
    public static Bitmap createRatioPreservedDownscaledBitmapFromUri(Context context, Uri uri, int maxWidth, int maxHeight, boolean reversible) {
        // Since any exception is grounds for an error, let's just try-catch
        // everything and return null if anything goes wrong.
        try {
            // Alright, ContentResolver.  You'd better do your job.
            InputStream input = context.getContentResolver().openInputStream(uri);

            if(input == null) return null;

            // Otherwise, it's the same as before.  Grab the bounds only.
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, opts);
            input.close();

            // I'm reasonably certain that there's no oddities possible with the
            // Uri in this case.  If the dimensions wind up -1, I'm just saying
            // it can't open it, and if there's some obscure case where a
            // legitimate Bitmap can be loaded whose dimensions are less than
            // zero, I don't care.
            if(opts.outHeight < 0 || opts.outWidth < 0) {
                Log.e(DEBUG_TAG, "Error opening URI " + uri.toString());
                return null;
            }

            // Do the same calculations as in the filename version...
            if(reversible && shouldBeReversed(maxWidth, maxHeight, opts.outWidth, opts.outHeight)) {
                int t = maxWidth;
                //noinspection SuspiciousNameCombination
                maxWidth = maxHeight;
                maxHeight = t;
            }

            int tempWidth = opts.outWidth;
            int tempHeight = opts.outHeight;
            int sampleFactor = 1;
            while(tempWidth / 2 >= maxWidth && tempHeight / 2 >= maxHeight) {
                tempWidth /= 2;
                tempHeight /= 2;
                sampleFactor *= 2;
            }

            Log.d(DEBUG_TAG, "Downsampling image to " + tempWidth + "x" + tempHeight + "...");

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleFactor;

            // Re-open the stream, as we closed it already.
            input = context.getContentResolver().openInputStream(uri);
            if(input == null) return null;

            // Read it into a Bitmap with the new options in hand.
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, opts);

            // Close 'er up.
            input.close();

            // Let 'er rip.
            return createRatioPreservedDownscaledBitmap(bitmap, maxWidth, maxHeight, false);
        } catch (IOException ioe) {
            // Aaaaaand something went wrong, so we return null.
            return null;
        }
    }

    private static boolean shouldBeReversed(int inWidth, int inHeight, int outWidth, int outHeight) {
        // If this ratio is 1.0, we never need to reverse it.
        if(inWidth == inHeight) return false;

        // If the original is more wide than tall but the second isn't, we can
        // reverse it.  Same with the other way around.
        return (inWidth < inHeight && outWidth > outHeight) || (inWidth > inHeight && outWidth < outHeight);
    }
}
