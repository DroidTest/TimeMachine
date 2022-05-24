/*
 * GHDDatePickerDialogFragment.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.DatePicker;

import net.exclaimindustries.geohashdroid.R;

import java.util.Calendar;

/**
 * Really, this wouldn't be necessary if I didn't need to include that "Today"
 * button.  But, I do, and thus, this.
 */
public class GHDDatePickerDialogFragment extends DialogFragment implements DatePicker.OnDateChangedListener {
    private static final String DEBUG_TAG = "GHDDatePickerDialog";

    /**
     * Interface that tells something that a date's been picked.
     */
    public interface GHDDatePickerCallback {
        /**
         * Method that's called when a date's been picked.
         *
         * @param picked the date that's been picked
         */
        void datePicked(Calendar picked);
    }

    private GHDDatePickerCallback mCallback;
    private int mYear;
    private int mMonth;
    private int mDay;

    /**
     * Generates a new GHDDatePickerDialogFragment, suitable for use as
     * a DialogFragment.
     *
     * @param cal the starting Calendar
     * @return a dialog
     */
    public static GHDDatePickerDialogFragment newInstance(@NonNull Calendar cal) {
        GHDDatePickerDialogFragment frag = new GHDDatePickerDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable("calendar", cal);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Sets the callback.  Please set this.  And to something OTHER than null.
     * Else the dialog will just go away.
     *
     * @param callback the new callback
     */
    public void setCallback(GHDDatePickerCallback callback) {
        mCallback = callback;
    }

    @Override
    @SuppressLint("InflateParams")
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Calendar startCal;
        if(savedInstanceState != null)
            startCal = (Calendar)savedInstanceState.getSerializable("calendar");
        else
            startCal = (Calendar)getArguments().getSerializable("calendar");

        if(startCal == null) startCal = Calendar.getInstance();

        mYear = startCal.get(Calendar.YEAR);
        mMonth = startCal.get(Calendar.MONTH);
        mDay = startCal.get(Calendar.DAY_OF_MONTH);

        // Set up the view first.  This means the date picker needs to get an
        // initial date and the button needs to be clickerable.
        LayoutInflater inflater = ((LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE));

        View dialogView = inflater.inflate(R.layout.date_picker_dialog, null);
        final DatePicker picker = dialogView.findViewById(R.id.date_picker);
        picker.init(mYear, mMonth, mDay, this);

        View today = dialogView.findViewById(R.id.today);
        today.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            picker.updateDate(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        });

        return new AlertDialog.Builder(getActivity())
                .setView(dialogView)
                .setTitle(R.string.dialog_date_picker_title)
                .setPositiveButton(R.string.ok_label, (dialog, which) -> {
                    // Well, you heard the orders!
                    Calendar cal = makeCalendar();
                    dismiss();
                    if(mCallback != null) {
                        mCallback.datePicked(cal);
                    }
                    else
                        Log.e(DEBUG_TAG, "You didn't specify a callback!");
                })
                .setNegativeButton(R.string.cancel_label, (dialog, which) -> dismiss())
                .create();
    }

    private Calendar makeCalendar() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, mYear);
        cal.set(Calendar.MONTH, mMonth);
        cal.set(Calendar.DAY_OF_MONTH, mDay);

        return cal;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putSerializable("calendar", makeCalendar());

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        // DATE!
        mYear = year;
        mMonth = monthOfYear;
        mDay = dayOfMonth;
    }
}
