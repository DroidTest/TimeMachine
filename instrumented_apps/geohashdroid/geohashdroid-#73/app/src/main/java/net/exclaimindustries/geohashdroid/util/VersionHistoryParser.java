/*
 * VersionHistoryParser.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

import net.exclaimindustries.geohashdroid.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;

/**
 * The <code>VersionHistoryParser</code> parses the version history XML file
 * (obviously) and puts its contents into a nice class for simple reading later.
 */
public class VersionHistoryParser {
    /**
     * A <code>VersionEntry</code> holds a single entry in the version history
     * (obviously).  You will generally get a List of these.
     */
    public static class VersionEntry implements Parcelable {
        public VersionEntry() { }

        private VersionEntry(Parcel in) {
            // Oh, hey, we're Parcelable now.  Who would've guessed?
            readFromParcel(in);
        }

        public static final Parcelable.Creator<VersionEntry> CREATOR = new Parcelable.Creator<VersionEntry>() {
            @Override
            public VersionEntry createFromParcel(Parcel source) { return new VersionEntry(source); }

            @Override
            public VersionEntry[] newArray(int size) { return new VersionEntry[size]; }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            // Dump 'em out!
            dest.writeString(title);
            dest.writeString(versionName);
            dest.writeString(date);
            dest.writeInt(versionCode);
            dest.writeString(header);
            dest.writeString(footer);
            dest.writeStringList(bullets);
        }

        public void readFromParcel(Parcel in) {
            // Fill 'em back in!
            title = in.readString();
            versionName = in.readString();
            date = in.readString();
            versionCode = in.readInt();
            header = in.readString();
            footer = in.readString();
            bullets = in.createStringArrayList();
        }

        /**
         * The title of this version.  Can be empty.  Will generally be whatever
         * silly name I decided to give it.
         */
        @NonNull public String title = "";

        /**
         * The "name" of this version.  That is, something like "0.9.0-pre1".
         * Must never be empty.
         */
        @NonNull public String versionName = "";

        /**
         * The date this version was released, as an XML standard formatted date
         * string (i.e. 2015-08-30).  Make your own damn Calendar object if you
         * really want that.
         */
        @NonNull public String date = "";

        /**
         * The internal version code of this version.  That is, whatever the
         * manifest actually uses for version identification.
         */
        public int versionCode = 0;

        /**
         * The header of this version.  This is some descriptive text that comes
         * before the bullets.
         */
        @NonNull public String header = "";

        /**
         * The footer of this version.  This is some descriptive text that comes
         * after the bullets.
         */
        @NonNull public String footer = "";

        /**
         * The list of bullet entries.  Each of these should be displayed in
         * order in their own section, indented and with bullets on the side.
         */
        @NonNull public ArrayList<String> bullets = new ArrayList<>();

        @Override
        public String toString() {
            return "Version history entry, version " + versionName + " (" + versionCode + "), contains " + bullets.size() + " bullet(s)";
        }
    }

    /**
     * Parses out the version history and returns an array of entries.
     *
     * @param c a Context for resources
     * @return a bunch of VersionHistoryEntries
     * @throws XmlPullParserException something went wrong with XML parsing
     */
    public static ArrayList<VersionEntry> parseVersionHistory(Context c) throws XmlPullParserException {
        ArrayList<VersionEntry> toReturn = new ArrayList<>();

        // To the parser!
        XmlResourceParser xrp = c.getResources().getXml(R.xml.version_history);
        int eventType = xrp.getEventType();

        String currentTag = "";
        VersionEntry currentEntry = null;

        while(eventType != XmlPullParser.END_DOCUMENT) {
            // There's only a few events we can get, so...
            if(eventType == XmlPullParser.START_TAG) {
                // Okay... so, what tag is it?
                currentTag = xrp.getName();

                if(currentTag.equals("version")) {
                    // New version!  This means we get a new entry!
                    currentEntry = new VersionEntry();

                    // There's also a few attributes in a version.  Like so:
                    currentEntry.versionName = xrp.getAttributeValue(null, "name");
                    currentEntry.date = xrp.getAttributeValue(null, "date");
                    currentEntry.versionCode = Integer.parseInt(xrp.getAttributeValue(null, "version"));
                }
            } else if (eventType == XmlPullParser.TEXT) {
                // On text, we feed data in depending on what tag started to get
                // us here in the first place.
                if(currentEntry == null) {
                    throw new RuntimeException("The current entry is null and we're in text parsing!");
                }

                switch(currentTag) {
                    case "title":
                        currentEntry.title = xrp.getText();
                        break;
                    case "header":
                        currentEntry.header = xrp.getText();
                        break;
                    case "footer":
                        currentEntry.footer = xrp.getText();
                        break;
                    case "bullet":
                        // Ah, but a bullet?  That gets added to a list instead.
                        currentEntry.bullets.add(xrp.getText());
                        break;
                }
            } else if(eventType == XmlPullParser.END_TAG) {
                if(xrp.getName().equals("version")) {
                    // End of a version!  It can go into toReturn.
                    toReturn.add(currentEntry);
                }
            }

            try {
                eventType = xrp.next();
            } catch(IOException ioe) {
                throw new RuntimeException("An IOException occurred during XML parsing!");
            }
        }

        // And we're done!
        xrp.close();
        return toReturn;
    }
}
