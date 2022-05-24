/*
 * PermissionDeniedDialogFragment.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import net.exclaimindustries.geohashdroid.R;

/**
 * Dialog that gets shown if the user doesn't feel like giving us the
 * permissions we need to do our job today.
 */
public class PermissionDeniedDialogFragment
        extends DialogFragment {
    public static final String TITLE = "title";
    public static final String MESSAGE = "message";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(args.getInt(TITLE, 0))
                .setMessage(args.getInt(MESSAGE, 0))
                .setPositiveButton(R.string.darn_label, (dialog, which) -> dismiss());
        return builder.create();
    }
}
