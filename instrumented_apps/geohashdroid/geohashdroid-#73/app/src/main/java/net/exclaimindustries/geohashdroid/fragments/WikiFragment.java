/*
 * WikiFragment.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.services.WikiService;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.geohashdroid.wiki.WikiUtils;
import net.exclaimindustries.tools.BitmapTools;
import net.exclaimindustries.tools.LocationUtil;

import java.text.DateFormat;
import java.util.Calendar;

/**
 * <code>WikiFragment</code> does double duty, handling what both of <code>WikiPictureEditor</code>
 * and <code>WikiMessageEditor</code> used to do.  Well, most of it.  Honestly,
 * most of that's been dumped into {@link WikiService}, but the interface part
 * here can handle either pictures or messages.
 */
public class WikiFragment extends CentralMapExtraFragment {
    private static final String PICTURE_URI = "pictureUri";

    private static final int GET_PICTURE = 1;

    private View mAnonWarning;
    private ImageButton mGalleryButton;
    private CheckBox mPictureCheckbox;
    private CheckBox mIncludeLocationCheckbox;
    private TextView mLocationView;
    private TextView mDistanceView;
    private EditText mMessage;
    private Button mPostButton;
    private TextView mHeader;

    private Location mLastLocation;

    private Uri mPictureUri;

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener = (sharedPreferences, key) -> {
        // Huh, we register for ALL changes, not just for a few prefs.  May
        // as well narrow it down...
        if(key.equals(GHDConstants.PREF_WIKI_USER) || key.equals(GHDConstants.PREF_WIKI_PASS)) {
            checkAnonStatus();
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.wiki, container, false);

        // Views!
        mAnonWarning = layout.findViewById(R.id.wiki_anon_warning);
        mPictureCheckbox = layout.findViewById(R.id.wiki_check_include_picture);
        mIncludeLocationCheckbox = layout.findViewById(R.id.wiki_check_include_location);
        mGalleryButton = layout.findViewById(R.id.wiki_thumbnail);
        mPostButton = layout.findViewById(R.id.wiki_post_button);
        mMessage = layout.findViewById(R.id.wiki_message);
        mLocationView = layout.findViewById(R.id.wiki_current_location);
        mDistanceView = layout.findViewById(R.id.wiki_distance);
        mHeader = layout.findViewById(R.id.wiki_header);

        // The picture checkbox determines if the other boxes are visible or
        // not.
        mPictureCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> resolvePictureControlVisibility());

        // The gallery button needs to fire off to the gallery.  Or Photos.  Or
        // whatever's listening for this intent.
        mGalleryButton.setOnClickListener(v -> {
            // Apparently there's been some... changes.  Changes in Kitkat
            // or the like.
            Intent i;

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
            } else {
                i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
            }

            startActivityForResult(i, GET_PICTURE);
        });

        // Any time the user edits the text, we also check to re-enable the post
        // button.
        mMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                // Ah, there we go.
                resolvePostButtonEnabledness();
            }
        });

        // The header goes to the current wiki page.
        mHeader.setOnClickListener(v -> {
            if(mInfo != null) {
                Intent i = new Intent();
                i.setAction(Intent.ACTION_VIEW);
                i.setData(Uri.parse(WikiUtils.getWikiBaseViewUrl() + WikiUtils.getWikiPageName(mInfo)));
                startActivity(i);
            }
        });

        // Here's the main event.
        mPostButton.setOnClickListener(v -> dispatchPost());

        // Make sure the header gets set here, too.
        applyHeader();

        // If we had a leftover Uri, apply that as well.
        if(savedInstanceState != null) {
            Uri pic = savedInstanceState.getParcelable(PICTURE_URI);
            if(pic != null) setImageUri(pic);
        } else {
            // setImageUri will call resolvePostButtonEnabledness, but since we
            // don't want to pass a null to the former, we'll call the latter if
            // we got a null.
            resolvePostButtonEnabledness();
        }

        updateCheckbox();

        return layout;
    }

    @Override
    public void onResume() {
        super.onResume();

        // We do the anon checks on resume, since it's possible that the user
        // came back from preferences and the anon states have changed.
        checkAnonStatus();

        // Plus, resubscribe for those changes.
        PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(mPrefListener);

        // Update the location, too.  This also makes the location fields
        // invisible if permissions aren't granted yet.  permissionsDenied()
        // will cover if they suddenly became available.
        updateLocation();
    }

    @Override
    public void onPause() {
        // Stop listening for changes.  We'll redo anon checks on resume anyway.
        PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(mPrefListener);

        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // We've also got a picture URI to deal with.
        outState.putParcelable(PICTURE_URI, mPictureUri);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case GET_PICTURE: {
                if(data != null) {
                    // Picture in!  We need to stash the URL away and make a
                    // thumbnail out of it, if we can!
                    Uri uri = data.getData();

                    if(uri == null)
                        return;

                    setImageUri(uri);
                }
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void setImageUri(@NonNull Uri uri) {
        // Grab a new Bitmap.  We'll toss this into the button.
        int dimen = getResources().getDimensionPixelSize(R.dimen.wiki_nominal_icon_size);
        final Bitmap thumbnail = BitmapTools
                .createRatioPreservedDownscaledBitmapFromUri(
                        getActivity(),
                        uri,
                        dimen,
                        dimen,
                        true
                );

        // Good!  Was it null?
        if(thumbnail == null) {
            // NO!  WRONG!  BAD!
            Toast.makeText(getActivity(), R.string.wiki_generic_image_error, Toast.LENGTH_LONG).show();
            return;
        }

        // With bitmap in hand...
        getActivity().runOnUiThread(() -> mGalleryButton.setImageBitmap(thumbnail));

        // And remember it for posting later.  Done!
        mPictureUri = uri;

        resolvePostButtonEnabledness();
    }

    @Override
    public void setInfo(Info info) {
        super.setInfo(info);

        applyHeader();
    }

    private void checkAnonStatus() {
        getActivity().runOnUiThread(() -> {
            // A user is anonymous if they either have no username or no
            // password (the wiki doesn't allow passwordless users, which
            // would just be silly anyway).
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

            String username = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
            String password = prefs.getString(GHDConstants.PREF_WIKI_PASS, "");

            if(username.isEmpty() || password.isEmpty()) {
                // If anything isn't defined, we can't set a picture.  Also,
                // uncheck the picture checkbox just to make sure.
                mPictureCheckbox.setChecked(false);
                mPictureCheckbox.setVisibility(View.GONE);
                mGalleryButton.setVisibility(View.GONE);
                mAnonWarning.setVisibility(View.VISIBLE);
            } else {
                // Now, we can't just turn everything back on without
                // checking.  But we CAN get rid of the anon warning and
                // bring back the picture checkbox.
                mAnonWarning.setVisibility(View.GONE);
                mPictureCheckbox.setVisibility(View.VISIBLE);
            }

            // Now, make sure everything else is up to date, including the
            // text on the post button.  This will do some redundant checks
            // in the case of hiding things, but meh.
            resolvePictureControlVisibility();
        });
    }

    private void resolvePictureControlVisibility() {
        // One checkbox to rule them all!
        getActivity().runOnUiThread(() -> {
            if(mPictureCheckbox.isChecked()) {
                mGalleryButton.setVisibility(View.VISIBLE);

                // Oh, and update a few strings, too.
                mPostButton.setText(R.string.wiki_dialog_submit_picture);
                mIncludeLocationCheckbox.setText(R.string.wiki_dialog_stamp_image);
                mMessage.setHint(R.string.hint_caption);
            } else {
                mGalleryButton.setVisibility(View.GONE);
                mPostButton.setText(R.string.wiki_dialog_submit_message);
                mIncludeLocationCheckbox.setText(R.string.wiki_dialog_append_coordinates);
                mMessage.setHint(R.string.hint_message);
            }

            // This also changes the post button's enabledness.
            resolvePostButtonEnabledness();
        });
    }

    private void resolvePostButtonEnabledness() {
        getActivity().runOnUiThread(() -> {
            // We can make a few booleans here just so the eventual call to
            // setEnabled is easier to read.
            boolean isInPictureMode = mPictureCheckbox.isChecked();
            boolean hasPicture = (mPictureUri != null);
            boolean hasMessage = !(mMessage.getText().toString().isEmpty());

            // So, to review, the button is enabled ONLY if there's a
            // message and, if we're in picture mode, there's a picture to
            // go with it.
            mPostButton.setEnabled(hasMessage && (hasPicture || !isInPictureMode));
        });
    }

    private void applyHeader() {
        getActivity().runOnUiThread(() -> {
            if(mInfo == null) {
                mHeader.setText("");
            } else {
                // Make sure it's underlined so it at least LOOKS like a
                // thing someone might click.
                Graticule g = mInfo.getGraticule();

                SpannableString text = new SpannableString(getString(R.string.wiki_dialog_header,
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(mInfo.getCalendar().getTime()),
                        (g == null
                                ? getString(R.string.globalhash_label)
                                : g.getLatitudeString(false) + " " + g.getLongitudeString(false))));
                text.setSpan(new UnderlineSpan(), 0, text.length(), 0);
                mHeader.setText(text);
            }
        });
    }

    private void updateLocation() {
        // If we're not ready yet (or if this isn't a phone layout), don't
        // bother.
        if(mLocationView == null || mDistanceView == null || mInfo == null) return;

        getActivity().runOnUiThread(() -> {
            // Easy enough, this is just the current location data.
            if(mPermissionsDenied) {
                mLocationView.setVisibility(View.INVISIBLE);
                mDistanceView.setVisibility(View.INVISIBLE);
            } else {
                mLocationView.setVisibility(View.VISIBLE);
                mDistanceView.setVisibility(View.VISIBLE);
            }

            if(mLastLocation == null) {
                // Or not, if there's no location.
                mLocationView.setText(R.string.standby_title);
                mDistanceView.setText(R.string.standby_title);
            } else {
                mLocationView.setText(UnitConverter.makeFullCoordinateString(getActivity(), mLastLocation, false, UnitConverter.OUTPUT_SHORT));
                mDistanceView.setText(UnitConverter.makeDistanceString(getActivity(), UnitConverter.DISTANCE_FORMAT_SHORT, mLastLocation.distanceTo(mInfo.getFinalLocation())));
            }
        });
    }

    private void dispatchPost() {
        // Time for fun!
        boolean includeLocation = !mPermissionsDenied && mIncludeLocationCheckbox.isChecked();
        boolean includePicture = mPictureCheckbox.isChecked();

        // So.  If we didn't have an Info yet, we're hosed.
        if(mInfo == null) {
            Toast.makeText(getActivity(), R.string.error_no_data_to_wiki, Toast.LENGTH_LONG).show();
            return;
        }

        // If there's no message, we're hosed.
        if(mMessage.getText().toString().isEmpty()) {
            Toast.makeText(getActivity(), R.string.error_no_message, Toast.LENGTH_LONG).show();
            return;
        }

        // If this is a picture post but there's no picture, we're hosed.
        if(includePicture && mPictureUri == null) {
            Toast.makeText(getActivity(), R.string.error_no_picture, Toast.LENGTH_LONG).show();
            return;
        }

        // Otherwise, it's time to send!
        String message = mMessage.getText().toString();
        Location loc = mLastLocation;
        if(!LocationUtil.isLocationNewEnough(loc)) loc = null;

        Intent i = new Intent(getActivity(), WikiService.class);
        i.putExtra(WikiService.EXTRA_INFO, mInfo);
        i.putExtra(WikiService.EXTRA_TIMESTAMP, Calendar.getInstance());
        i.putExtra(WikiService.EXTRA_MESSAGE, message);
        i.putExtra(WikiService.EXTRA_LOCATION, loc);
        i.putExtra(WikiService.EXTRA_INCLUDE_LOCATION, includeLocation);
        if(includePicture)
            i.putExtra(WikiService.EXTRA_IMAGE, mPictureUri);

        // And away it goes!
        getActivity().startService(i);

        // Post complete!  We're done here!
        if(mCloseListener != null)
            mCloseListener.extraFragmentClosing(this);
    }

    @NonNull
    @Override
    public FragmentType getType() {
        return FragmentType.WIKI;
    }

    @Override
    public void onLocationChanged(Location location) {
        // We'll get this in either from CentralMap or WikiActivity.  In either
        // case, we act the same way.
        mLastLocation = location;
        updateLocation();
    }

    @Override
    public void permissionsDenied(boolean denied) {
        // This comes in from ExpeditionMode if permissions are denied/granted
        // or from WikiActivity during onResume if permissions are granted some
        // other way (WikiActivity won't ask for permission; it'll just assume
        // the current permission state holds).
        mPermissionsDenied = denied;
        updateLocation();

        // Also, remove the Append Location box if permissions were denied.
        updateCheckbox();
    }

    private void updateCheckbox() {
        mIncludeLocationCheckbox.setVisibility(mPermissionsDenied ? View.GONE : View.VISIBLE);
    }
}
