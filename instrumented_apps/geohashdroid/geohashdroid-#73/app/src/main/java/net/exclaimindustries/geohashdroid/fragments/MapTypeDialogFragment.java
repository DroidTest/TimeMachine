/*
 * MapTypeDialogFragment.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;

import net.exclaimindustries.geohashdroid.R;

/**
 * Because I couldn't find a good iconic way to put the map type selection on
 * the map, we'll have to go to a dialog.
 */
public class MapTypeDialogFragment extends DialogFragment {
    private static final String DEBUG_TAG = "MapTypeDialogFragment";

    public final static int MAP_STYLE_NIGHT = 0x104;

    /**
     * INTERFACE!  INTERFACE INTERFACE INTERFACE INTERFACE!
     */
    public interface MapTypeCallback {
        /**
         * Called when the map type has been selected.
         *
         * @param type the map type, as the int that you would pass in to GoogleMap.setMapType(int)
         */
        void mapTypeSelected(int type);
    }

    private MapTypeCallback mCallback;

    /**
     * Creates a new MapTypeDialogFragment, with a handy callback already given!
     *
     * @param callback a handy callback!
     * @return a new MapTypeDialogFragment
     */
    public static MapTypeDialogFragment newInstance(@NonNull MapTypeCallback callback) {
        MapTypeDialogFragment frag = new MapTypeDialogFragment();
        frag.setCallback(callback);
        return frag;
    }

    /**
     * Sets the callback that'll be called when the map type's picked.
     *
     * @param callback that callback
     */
    public void setCallback(@NonNull MapTypeCallback callback) {
        mCallback = callback;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.menu_item_map_type)
                .setItems(R.array.menu_item_map_types, (dialog, which) -> {
                    // Picked!
                    if(mCallback == null) {
                        Log.e(DEBUG_TAG, "There's no callback set!");
                        return;
                    }

                    dialog.dismiss();

                    switch(which) {
                        case 0:
                            mCallback.mapTypeSelected(GoogleMap.MAP_TYPE_NORMAL);
                            break;
                        case 1:
                            mCallback.mapTypeSelected(MAP_STYLE_NIGHT);
                            break;
                        case 2:
                            mCallback.mapTypeSelected(GoogleMap.MAP_TYPE_HYBRID);
                            break;
                        case 3:
                            mCallback.mapTypeSelected(GoogleMap.MAP_TYPE_TERRAIN);
                    }
                })
                .create();
    }
}
